package minic.compiler.ir.optimize;

import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue.IrTemporary;

import java.util.*;

/**
 * Conservative Windows x64 local allocation in the two volatile GPRs unused by the emitter's
 * fixed scratch registers. Every assigned value is confined to one executable block and never
 * live across a call. Other temporaries retain their existing stack homes; this is not global
 * register allocation and does not shrink the physical frame.
 */
public final class LocalRegisterPlan {
    private static final List<String> POOL = List.of("r10", "r11");
    private final Map<String, String> registers;
    private final Map<String, IrType> temporaryTypes;
    private final Set<String> stackTemporaries;

    private LocalRegisterPlan(IrFunction function) {
        var analysis = IrLiveness.analyze(function);
        temporaryTypes = analysis.temporaryTypes();
        Set<String> excluded = new HashSet<>(analysis.liveAcrossCalls());
        temporaryTypes.forEach((name, type) -> { if (type.isFloatingScalar()) excluded.add(name); });
        var spans = new LinkedHashMap<String, Span>();
        for (var block : function.blocks()) {
            excluded.addAll(analysis.liveIn(block.label()));
            excluded.addAll(analysis.liveOut(block.label()));
            for (int index = 0; index < block.instructions().size(); index++) {
                var instruction = block.instructions().get(index);
                var point = analysis.instruction(block.label(), index);
                mention(spans, excluded, IrValueUses.result(instruction), block.label(), index, point.executable());
                for (var input : IrValueUses.inputs(instruction)) if (input instanceof IrTemporary temporary)
                    mention(spans, excluded, temporary, block.label(), index, point.executable());
            }
        }

        var assigned = new LinkedHashMap<String, String>();
        for (var block : function.blocks()) {
            var intervals = spans.entrySet().stream()
                    .filter(entry -> !excluded.contains(entry.getKey()) && entry.getValue().block().equals(block.label()))
                    .sorted(Comparator.comparingInt(entry -> entry.getValue().start())).toList();
            var active = new ArrayList<Active>();
            for (var entry : intervals) {
                Span span = entry.getValue();
                // Closed intervals also keep a dying input separate from this instruction's result.
                active.removeIf(value -> value.end() < span.start());
                for (String register : POOL) {
                    if (active.stream().anyMatch(value -> value.register().equals(register))) continue;
                    assigned.put(entry.getKey(), register);
                    active.add(new Active(register, span.end()));
                    break;
                }
            }
        }
        registers = Collections.unmodifiableMap(assigned);
        var onStack = new LinkedHashSet<>(temporaryTypes.keySet()); onStack.removeAll(assigned.keySet());
        stackTemporaries = Collections.unmodifiableSet(onStack);
    }

    private static void mention(Map<String, Span> spans, Set<String> excluded, IrTemporary temporary,
                                String block, int index, boolean executable) {
        if (temporary == null) return;
        String name = temporary.name();
        if (!executable) excluded.add(name);
        Span previous = spans.get(name);
        if (previous == null) spans.put(name, new Span(block, index, index));
        else if (!previous.block().equals(block)) excluded.add(name);
        else spans.put(name, new Span(block, Math.min(previous.start(), index), Math.max(previous.end(), index)));
    }

    public static LocalRegisterPlan allocate(IrFunction function) {
        return new LocalRegisterPlan(Objects.requireNonNull(function, "function"));
    }
    public String strategyName() { return "local-volatile-registers"; }
    public Map<String, String> registers() { return registers; }
    public Map<String, IrType> temporaryTypes() { return temporaryTypes; }
    public Set<String> stackTemporaries() { return stackTemporaries; }

    private record Span(String block, int start, int end) { }
    private record Active(String register, int end) { }
}
