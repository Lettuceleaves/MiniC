package craken.compiler.ir.optimize;

import craken.compiler.ir.instruction.ControlInstruction.*;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.model.IrBlock;
import craken.compiler.ir.model.IrFunction;

import java.util.*;

/**
 * Immutable CFG for the current non-SSA IR. The first terminator ends a block's
 * executable prefix; trailing instructions and unreachable empty blocks are legal.
 * Missing target names remain in successor lists so a verifier can diagnose them.
 */
public final class IrControlFlow {
    private final Map<String, List<IrInstruction>> instructions;
    private final Map<String, List<String>> successors;
    private final Map<String, List<String>> predecessors;
    private final Set<String> reachable;

    private IrControlFlow(IrFunction function) {
        Map<String, List<IrInstruction>> prefixes = new LinkedHashMap<>();
        Map<String, List<String>> next = new LinkedHashMap<>();
        Map<String, List<String>> previous = new LinkedHashMap<>();
        for (IrBlock block : function.blocks()) {
            if (prefixes.containsKey(block.label())) throw new IllegalArgumentException("Duplicate block: " + block.label());
            int length = 0;
            for (IrInstruction instruction : block.instructions()) {
                length++;
                if (isTerminator(instruction)) break;
            }
            var prefix = List.copyOf(block.instructions().subList(0, length));
            prefixes.put(block.label(), prefix);
            next.put(block.label(), prefix.isEmpty() ? List.of() : targets(prefix.getLast()));
            previous.put(block.label(), new ArrayList<>());
        }
        next.forEach((from, targets) -> targets.forEach(target -> {
            if (previous.containsKey(target)) previous.get(target).add(from);
        }));
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        if (!function.blocks().isEmpty()) pending.add(function.blocks().getFirst().label());
        while (!pending.isEmpty()) {
            String block = pending.removeFirst();
            if (!next.containsKey(block) || !visited.add(block)) continue;
            pending.addAll(next.get(block));
        }
        instructions = immutableMap(prefixes);
        successors = immutableMap(next);
        predecessors = immutableMap(previous);
        reachable = Collections.unmodifiableSet(visited);
    }

    public static IrControlFlow analyze(IrFunction function) {
        return new IrControlFlow(Objects.requireNonNull(function, "function"));
    }

    public List<IrInstruction> effectiveInstructions(String block) { return get(instructions, block); }
    public List<String> successors(String block) { return get(successors, block); }
    public List<String> predecessors(String block) { return get(predecessors, block); }
    public Set<String> reachable() { return reachable; }

    public static boolean isTerminator(IrInstruction instruction) {
        return instruction instanceof IrBranchInstruction || instruction instanceof IrJumpInstruction
                || instruction instanceof IrReturnInstruction;
    }

    static List<String> targets(IrInstruction instruction) {
        if (instruction instanceof IrJumpInstruction jump) return List.of(jump.targetLabel());
        if (instruction instanceof IrBranchInstruction branch) {
            return branch.thenLabel().equals(branch.elseLabel()) ? List.of(branch.thenLabel())
                    : List.of(branch.thenLabel(), branch.elseLabel());
        }
        return List.of();
    }

    private static <T> List<T> get(Map<String, List<T>> map, String block) {
        List<T> result = map.get(block);
        if (result == null) throw new IllegalArgumentException("Unknown block: " + block);
        return result;
    }

    private static <T> Map<String, List<T>> immutableMap(Map<String, List<T>> values) {
        Map<String, List<T>> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }
}
