package minic.compiler.ir.optimize;

import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrIndirectCallInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue.IrTemporary;

import java.util.*;

/**
 * Fixed Windows x64 integer register homes using conservative whole-function convex intervals,
 * including CFG boundary liveness. There is no interval splitting or edge shuffle. Volatile
 * call survivors have explicit stack save/restore points; optional nonvolatile homes instead
 * require one function-level save/restore. The static access threshold is a conservative cost
 * heuristic, not a claim of profile-guided profitability.
 */
public final class GlobalRegisterPlan {
    private static final List<String> POOL = List.of("r10", "r11");
    private static final List<String> CALLEE_SAVED = List.of("rbx", "r12", "r13", "r14", "r15");
    private final Map<String, String> registers;
    private final Map<String, IrType> temporaryTypes;
    private final Set<String> stackTemporaries;
    private final Map<Point, List<IrTemporary>> spills;
    private final List<String> calleeSavedRegisters;

    private GlobalRegisterPlan(IrFunction function, boolean allowCalleeSaved) {
        var analysis = IrLiveness.analyze(function);
        temporaryTypes = analysis.temporaryTypes();
        var excluded = new HashSet<String>();
        var volatileExcluded = new HashSet<String>();
        temporaryTypes.forEach((name, type) -> { if (type.isFloatingScalar()) excluded.add(name); });
        var spans = new LinkedHashMap<String, Span>();
        var transfers = new HashMap<String, Integer>();
        var callCounts = new HashMap<String, Integer>();
        var survivors = new LinkedHashMap<Point, Set<String>>();
        var cyclic = new HashMap<String, Boolean>();
        int position = 0;
        for (var block : function.blocks()) {
            // Boundary positions matter even when the block does not directly read the value.
            for (String name : analysis.liveIn(block.label())) extend(spans, name, position);
            for (int index = 0; index < block.instructions().size(); index++) {
                position = Math.incrementExact(position);
                var instruction = block.instructions().get(index);
                var point = analysis.instruction(block.label(), index);
                mention(spans, transfers, excluded, IrValueUses.result(instruction), position, point.executable());
                for (var input : IrValueUses.inputs(instruction)) if (input instanceof IrTemporary temporary)
                    mention(spans, transfers, excluded, temporary, position, point.executable());
                if (point.executable() && (instruction instanceof IrCallInstruction || instruction instanceof IrIndirectCallInstruction)) {
                    Set<String> across = point.liveAcrossCall();
                    survivors.put(new Point(block.label(), index), across);
                    for (String name : across) callCounts.merge(name, 1, Math::addExact);
                    if (!across.isEmpty() && cyclic.computeIfAbsent(block.label(), name -> inCycle(analysis.controlFlow(), name)))
                        volatileExcluded.addAll(across);
                }
            }
            position = Math.incrementExact(position);
            for (String name : analysis.liveOut(block.label())) extend(spans, name, position);
            position = Math.incrementExact(position);
        }
        callCounts.forEach((name, count) -> {
            // Each call adds one store and one reload. A single definition/use is cheaper left on stack.
            if (transfers.getOrDefault(name, 0) <= Math.addExact(Math.multiplyExact(2, count), 1)) volatileExcluded.add(name);
        });

        var intervals = spans.entrySet().stream().filter(entry -> !excluded.contains(entry.getKey()))
                .sorted(Comparator.<Map.Entry<String, Span>>comparingInt(entry -> entry.getValue().start())
                        .thenComparing(Map.Entry::getKey)).toList();
        var assigned = new LinkedHashMap<String, String>();
        var active = new ArrayList<Active>();
        for (var entry : intervals) {
            Span span = entry.getValue(); active.removeIf(value -> value.end() < span.start());
            String name = entry.getKey();
            // Four removed stack transfers can repay two full-width prologue/epilogue transfers.
            // Cheap values never open a nonvolatile home, even when another value already paid its save.
            boolean useNonvolatile = allowCalleeSaved && transfers.getOrDefault(name, 0) >= 4;
            var candidates = new ArrayList<String>();
            boolean crossesCall = callCounts.containsKey(name);
            if (useNonvolatile && crossesCall) candidates.addAll(CALLEE_SAVED);
            if (!volatileExcluded.contains(name)) candidates.addAll(POOL);
            if (useNonvolatile && !crossesCall) candidates.addAll(CALLEE_SAVED);
            for (String register : candidates) {
                if (active.stream().anyMatch(value -> value.register().equals(register))) continue;
                assigned.put(entry.getKey(), register); active.add(new Active(register, span.end())); break;
            }
        }
        registers = Collections.unmodifiableMap(assigned);
        calleeSavedRegisters = CALLEE_SAVED.stream().filter(assigned::containsValue).toList();
        var onStack = new LinkedHashSet<>(temporaryTypes.keySet()); onStack.removeAll(assigned.keySet());
        stackTemporaries = Collections.unmodifiableSet(onStack);
        var saved = new LinkedHashMap<Point, List<IrTemporary>>();
        survivors.forEach((point, names) -> {
            var values = new ArrayList<IrTemporary>();
            for (String name : names) if (assigned.containsKey(name) && POOL.contains(assigned.get(name)))
                values.add(new IrTemporary(name, temporaryTypes.get(name)));
            // Stable register order also makes emission and diagnostics reproducible.
            values.sort(Comparator.comparing(value -> assigned.get(value.name())));
            if (!values.isEmpty()) saved.put(point, List.copyOf(values));
        });
        spills = Collections.unmodifiableMap(saved);
    }

    private static void mention(Map<String, Span> spans, Map<String, Integer> transfers, Set<String> excluded,
                                IrTemporary value, int position, boolean executable) {
        if (value == null) return;
        if (!executable) excluded.add(value.name());
        extend(spans, value.name(), position);
        transfers.merge(value.name(), 1, Math::addExact);
    }
    private static void extend(Map<String, Span> spans, String name, int position) {
        Span previous = spans.get(name);
        spans.put(name, previous == null ? new Span(position, position)
                : new Span(Math.min(previous.start(), position), Math.max(previous.end(), position)));
    }
    /** A cached reachability query per call-containing block; includes irreducible loops and self edges. */
    private static boolean inCycle(IrControlFlow flow, String block) {
        var pending = new ArrayDeque<>(flow.successors(block)); var visited = new HashSet<String>();
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            if (next.equals(block)) return true;
            if (visited.add(next)) pending.addAll(flow.successors(next));
        }
        return false;
    }

    public static GlobalRegisterPlan allocate(IrFunction function) {
        return allocate(function, false);
    }
    public static GlobalRegisterPlan allocate(IrFunction function, boolean allowCalleeSaved) {
        return new GlobalRegisterPlan(Objects.requireNonNull(function, "function"), allowCalleeSaved);
    }
    public String strategyName() { return calleeSavedRegisters.isEmpty() ? "global-volatile-registers" : "global-callee-saved-registers"; }
    public Map<String, String> registers() { return registers; }
    public Map<String, IrType> temporaryTypes() { return temporaryTypes; }
    public Set<String> stackTemporaries() { return stackTemporaries; }
    /** Full-width registers that emission must preserve once in this function's prologue/epilogue. */
    public List<String> calleeSavedRegisters() { return calleeSavedRegisters; }
    public List<IrTemporary> spillsAt(String block, int instructionIndex) {
        return spills.getOrDefault(new Point(block, instructionIndex), List.of());
    }
    private record Span(int start, int end) { }
    private record Active(String register, int end) { }
    private record Point(String block, int instructionIndex) { }
}
