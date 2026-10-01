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
 * require one function-level save/restore. Executable accesses in cyclic blocks have weight two;
 * other accesses have weight one. This bounded, static heuristic assumes some loop reuse,
 * not measured frequencies or guaranteed profitability. Existing liveness remains authoritative.
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
        var savedTransfers = new HashMap<String, Integer>();
        var weightedTransfers = new HashMap<String, Integer>();
        var cyclic = cyclicBlocks(analysis.controlFlow());
        // This is only a cost discount for a native emission opportunity. All mentions still
        // contribute to spans/interference, even when this planner is used without fusion.
        var fusedTemporaries = new HashSet<String>();
        if (allowCalleeSaved) {
            var branches = ComparisonBranchPlan.analyze(function);
            var fieldLoads = FieldLoadPlan.analyze(function);
            for (var block : function.blocks()) for (int i = 0; i < block.instructions().size(); i++) {
                var fieldFusion = fieldLoads.at(block.label(), i);
                if (fieldFusion != null) fusedTemporaries.add(fieldFusion.fieldAddress().result().name());
                var fusion = branches.at(block.label(), i);
                if (fusion != null) {
                    for (var temporary : fusion.discardedTemporaries()) fusedTemporaries.add(temporary.name());
                }
            }
        }
        var callCounts = new HashMap<String, Integer>();
        var survivors = new LinkedHashMap<Point, Set<String>>();
        int position = 0;
        for (var block : function.blocks()) {
            int weight = cyclic.contains(block.label()) ? 2 : 1;
            // Boundary positions matter even when the block does not directly read the value.
            for (String name : analysis.liveIn(block.label())) extend(spans, name, position);
            for (int index = 0; index < block.instructions().size(); index++) {
                position = Math.incrementExact(position);
                var instruction = block.instructions().get(index);
                var point = analysis.instruction(block.label(), index);
                mention(spans, transfers, savedTransfers, weightedTransfers, fusedTemporaries, excluded, IrValueUses.result(instruction), position, point.executable(), weight);
                for (var input : IrValueUses.inputs(instruction)) if (input instanceof IrTemporary temporary)
                    mention(spans, transfers, savedTransfers, weightedTransfers, fusedTemporaries, excluded, temporary, position, point.executable(), weight);
                if (point.executable() && (instruction instanceof IrCallInstruction || instruction instanceof IrIndirectCallInstruction)) {
                    Set<String> across = point.liveAcrossCall();
                    survivors.put(new Point(block.label(), index), across);
                    for (String name : across) callCounts.merge(name, 1, Math::addExact);
                    if (!across.isEmpty() && cyclic.contains(block.label()))
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
        var paidRegisters = new HashSet<String>();
        for (var entry : intervals) {
            Span span = entry.getValue(); active.removeIf(value -> value.end() < span.start());
            String name = entry.getKey();
            // Opening a home still needs four weighted transfers. Once its function save is
            // paid, a later definition/use pair may reuse it without another prologue cost.
            boolean openNonvolatile = allowCalleeSaved && weightedTransfers.getOrDefault(name, 0) >= 4;
            boolean reuseNonvolatile = allowCalleeSaved && savedTransfers.getOrDefault(name, 0) >= 2;
            var nonvolatileCandidates = new ArrayList<String>();
            if (reuseNonvolatile) for (String register : CALLEE_SAVED)
                if (paidRegisters.contains(register)) nonvolatileCandidates.add(register);
            if (openNonvolatile) for (String register : CALLEE_SAVED)
                if (!paidRegisters.contains(register)) nonvolatileCandidates.add(register);
            var candidates = new ArrayList<String>();
            boolean crossesCall = callCounts.containsKey(name);
            if (crossesCall) candidates.addAll(nonvolatileCandidates);
            if (!volatileExcluded.contains(name)) candidates.addAll(POOL);
            if (!crossesCall) candidates.addAll(nonvolatileCandidates);
            for (String register : candidates) {
                if (active.stream().anyMatch(value -> value.register().equals(register))) continue;
                assigned.put(entry.getKey(), register); active.add(new Active(register, span.end()));
                if (CALLEE_SAVED.contains(register)) paidRegisters.add(register);
                break;
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

    private static void mention(Map<String, Span> spans, Map<String, Integer> transfers,
                                Map<String, Integer> savedTransfers, Map<String, Integer> weightedTransfers,
                                Set<String> fusedTemporaries, Set<String> excluded,
                                IrTemporary value, int position, boolean executable, int weight) {
        if (value == null) return;
        if (!executable) excluded.add(value.name());
        extend(spans, value.name(), position);
        transfers.merge(value.name(), 1, Math::addExact);
        if (executable && !fusedTemporaries.contains(value.name())) {
            savedTransfers.merge(value.name(), 1, Math::addExact);
            weightedTransfers.merge(value.name(), weight, Math::addExact);
        }
    }
    private static void extend(Map<String, Span> spans, String name, int position) {
        Span previous = spans.get(name);
        spans.put(name, previous == null ? new Span(position, position)
                : new Span(Math.min(previous.start(), position), Math.max(previous.end(), position)));
    }
    /** Iterative SCC discovery: linear CFG cost, no recursive Java stack for large functions. */
    private static Set<String> cyclicBlocks(IrControlFlow flow) {
        var visited = new HashSet<String>(); var finish = new ArrayList<String>();
        var pending = new ArrayDeque<Visit>();
        for (String root : flow.reachable()) {
            pending.addLast(new Visit(root, false));
            while (!pending.isEmpty()) {
                var visit = pending.removeLast();
                if (visit.finished()) { finish.add(visit.block()); continue; }
                if (!visited.add(visit.block())) continue;
                pending.addLast(new Visit(visit.block(), true));
                for (String next : flow.successors(visit.block()))
                    if (flow.reachable().contains(next) && !visited.contains(next)) pending.addLast(new Visit(next, false));
            }
        }
        visited.clear(); var cyclic = new HashSet<String>(); var reverse = new ArrayDeque<String>();
        for (int i = finish.size() - 1; i >= 0; i--) {
            String root = finish.get(i); if (!visited.add(root)) continue;
            var component = new ArrayList<String>(); reverse.addLast(root);
            while (!reverse.isEmpty()) {
                String block = reverse.removeLast(); component.add(block);
                for (String previous : flow.predecessors(block))
                    if (flow.reachable().contains(previous) && visited.add(previous)) reverse.addLast(previous);
            }
            if (component.size() > 1 || flow.successors(root).contains(root)) cyclic.addAll(component);
        }
        return cyclic;
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
    private record Visit(String block, boolean finished) { }
    private record Span(int start, int end) { }
    private record Active(String register, int end) { }
    private record Point(String block, int instructionIndex) { }
}
