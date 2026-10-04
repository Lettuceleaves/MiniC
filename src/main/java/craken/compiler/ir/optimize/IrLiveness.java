package craken.compiler.ir.optimize;

import craken.compiler.ir.model.IrFunction;
import craken.compiler.ir.model.IrType;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import craken.compiler.ir.instruction.CallInstruction.IrIndirectCallInstruction;
import craken.compiler.ir.value.IrValue;
import craken.compiler.ir.value.IrValue.IrTemporary;

import java.util.*;

/**
 * Immutable may-liveness for temporary values in existing, verified non-SSA IR.
 * Every executable instruction participates, including a dead computation whose
 * destination the backend will still write. This analysis never removes code.
 * Parameter homes, source locals, initialization flags and aliased memory are
 * deliberately outside its domain and must retain separate storage.
 */
public final class IrLiveness {
    /** Empty facts with executable=false mean no data-flow fact, not permission to reuse arbitrary storage. */
    public record InstructionLiveness(boolean executable, Set<String> liveBefore,
                                      Set<String> liveAfter, Set<String> liveAcrossCall) {
        public InstructionLiveness {
            liveBefore = Set.copyOf(liveBefore);
            liveAfter = Set.copyOf(liveAfter);
            liveAcrossCall = Set.copyOf(liveAcrossCall);
        }
    }
    private static final InstructionLiveness NON_EXECUTABLE = new InstructionLiveness(false, Set.of(), Set.of(), Set.of());
    private final IrControlFlow flow;
    private final Map<String, IrType> temporaryTypes;
    private final Map<String, Set<String>> liveIn;
    private final Map<String, Set<String>> liveOut;
    private final Map<String, List<InstructionLiveness>> instructions;
    private final Set<String> liveAcrossCalls;

    private IrLiveness(IrFunction function) {
        flow = IrControlFlow.analyze(function);
        var types = new LinkedHashMap<String, IrType>();
        var blockUses = new HashMap<String, Set<String>>();
        var blockDefinitions = new HashMap<String, Set<String>>();
        var incoming = new LinkedHashMap<String, Set<String>>();
        var outgoing = new LinkedHashMap<String, Set<String>>();
        var blockNames = new HashSet<String>();
        function.blocks().forEach(block -> blockNames.add(block.label()));
        for (var block : function.blocks()) {
            for (String successor : flow.successors(block.label()))
                if (!blockNames.contains(successor)) throw new IllegalArgumentException("Unknown block target: " + successor);
            // Retain every type, including unreachable instructions: the backend may still emit their destinations.
            for (IrInstruction instruction : block.instructions()) {
                register(types, IrValueUses.result(instruction));
                for (IrValue value : IrValueUses.inputs(instruction))
                    if (value instanceof IrTemporary temporary) register(types, temporary);
            }
            Set<String> uses = new HashSet<>(), definitions = new HashSet<>();
            if (flow.reachable().contains(block.label())) {
                for (IrInstruction instruction : flow.effectiveInstructions(block.label())) {
                    // A read-modify-write uses the old value before defining the new one.
                    for (IrValue value : IrValueUses.inputs(instruction))
                        if (value instanceof IrTemporary temporary && !definitions.contains(temporary.name())) uses.add(temporary.name());
                    IrTemporary result = IrValueUses.result(instruction);
                    if (result != null) definitions.add(result.name());
                }
            }
            blockUses.put(block.label(), Set.copyOf(uses));
            blockDefinitions.put(block.label(), Set.copyOf(definitions));
            incoming.put(block.label(), Set.of());
            outgoing.put(block.label(), Set.of());
        }

        // Backward worklist with union at joins, rather than SSA or a linearized interval approximation.
        var pending = new ArrayDeque<String>();
        var queued = new HashSet<String>();
        for (var block : function.blocks().reversed()) {
            if (flow.reachable().contains(block.label())) { pending.addLast(block.label()); queued.add(block.label()); }
        }
        while (!pending.isEmpty()) {
            String block = pending.removeFirst(); queued.remove(block);
            Set<String> out = new HashSet<>();
            for (String successor : flow.successors(block)) out.addAll(incoming.get(successor));
            Set<String> in = new HashSet<>(out);
            in.removeAll(blockDefinitions.get(block));
            in.addAll(blockUses.get(block));
            outgoing.put(block, Set.copyOf(out));
            if (!in.equals(incoming.get(block))) {
                incoming.put(block, Set.copyOf(in));
                for (String predecessor : flow.predecessors(block))
                    if (flow.reachable().contains(predecessor) && queued.add(predecessor)) pending.addLast(predecessor);
            }
        }

        var points = new LinkedHashMap<String, List<InstructionLiveness>>();
        Set<String> acrossAll = new HashSet<>();
        for (var block : function.blocks()) {
            var facts = new ArrayList<>(Collections.nCopies(block.instructions().size(), NON_EXECUTABLE));
            if (flow.reachable().contains(block.label())) {
                Set<String> after = outgoing.get(block.label());
                var effective = flow.effectiveInstructions(block.label());
                for (int index = effective.size() - 1; index >= 0; index--) {
                    IrInstruction instruction = effective.get(index);
                    IrTemporary result = IrValueUses.result(instruction);
                    Set<String> before = new HashSet<>(after);
                    if (result != null) before.remove(result.name());
                    for (IrValue value : IrValueUses.inputs(instruction))
                        if (value instanceof IrTemporary temporary) before.add(temporary.name());
                    Set<String> across = new HashSet<>();
                    if (instruction instanceof IrCallInstruction || instruction instanceof IrIndirectCallInstruction) {
                        across.addAll(before); across.retainAll(after);
                        // A same-name call result denotes a NEW value; the old value need not survive the call.
                        if (result != null) across.remove(result.name());
                        acrossAll.addAll(across);
                    }
                    facts.set(index, new InstructionLiveness(true, before, after, across));
                    after = before;
                }
            }
            points.put(block.label(), List.copyOf(facts));
        }
        temporaryTypes = Collections.unmodifiableMap(types);
        liveIn = Collections.unmodifiableMap(incoming);
        liveOut = Collections.unmodifiableMap(outgoing);
        instructions = Collections.unmodifiableMap(points);
        liveAcrossCalls = Set.copyOf(acrossAll);
    }

    public static IrLiveness analyze(IrFunction function) { return new IrLiveness(Objects.requireNonNull(function, "function")); }
    public IrControlFlow controlFlow() { return flow; }
    /** Includes unreachable definitions/uses; insertion order follows the original instructions. */
    public Map<String, IrType> temporaryTypes() { return temporaryTypes; }
    public Set<String> liveIn(String block) { return get(liveIn, block); }
    public Set<String> liveOut(String block) { return get(liveOut, block); }
    /** One record for every ORIGINAL instruction index, including explicit non-executable records. */
    public List<InstructionLiveness> instructions(String block) { return get(instructions, block); }
    public InstructionLiveness instruction(String block, int index) { return instructions(block).get(index); }
    public Set<String> liveBefore(String block, int index) { return instruction(block,index).liveBefore(); }
    public Set<String> liveAfter(String block, int index) { return instruction(block,index).liveAfter(); }
    public Set<String> liveAcrossCall(String block, int index) { return instruction(block,index).liveAcrossCall(); }
    /** Union of values whose pre-call value must survive any executable direct or indirect call. */
    public Set<String> liveAcrossCalls() { return liveAcrossCalls; }

    private static void register(Map<String, IrType> types, IrTemporary temporary) {
        if (temporary == null) return;
        IrType previous = types.putIfAbsent(temporary.name(), temporary.type());
        if (previous != null && previous != temporary.type())
            throw new IllegalArgumentException("Conflicting temporary type: " + temporary.name());
    }
    private static <T> T get(Map<String, T> map, String block) {
        T result = map.get(block);
        if (result == null) throw new IllegalArgumentException("Unknown block: " + block);
        return result;
    }
}
