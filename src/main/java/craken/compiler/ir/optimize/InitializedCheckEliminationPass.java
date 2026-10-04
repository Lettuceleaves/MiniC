package craken.compiler.ir.optimize;

import craken.compiler.ir.IrResult;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.*;
import craken.compiler.ir.model.IrBlock;
import craken.compiler.ir.model.IrFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Removes initialization checks only after a proof on every incoming path. */
public final class InitializedCheckEliminationPass implements IrPass {
    @Override public String name() { return "initialized-check-elimination"; }
    @Override public IrResult apply(IrResult input) {
        Objects.requireNonNull(input, "input");
        List<IrFunction> functions = input.functions().stream().map(this::eliminate).toList();
        return functions.equals(input.functions()) ? input
                : new IrResult(functions, input.stringData(), input.globalData(), input.externalFunctionNames(),
                        input.externalObjectNames(), input.structLayouts(), input.currentAstNode(), input.currentSubject(), input.displayNames(), input.entryFunction());
    }

    private IrFunction eliminate(IrFunction function) {
        if (function.blocks().isEmpty()) return function;
        IrControlFlow flow = IrControlFlow.analyze(function);
        String entry = function.blocks().getFirst().label();
        Map<String, Set<String>> outgoing = new HashMap<>();
        // A missing state is the lattice's TOP until a predecessor is processed.
        // Intersect actual predecessor facts, including the empty invocation edge.
        boolean changed;
        do {
            changed = false;
            for (IrBlock block : function.blocks()) {
                if (!flow.reachable().contains(block.label())) continue;
                Set<String> incoming = meet(block.label(), entry, flow, outgoing);
                if (incoming == null) continue;
                Set<String> next = transfer(flow.effectiveInstructions(block.label()), incoming, null);
                if (!next.equals(outgoing.put(block.label(), next))) changed = true;
            }
        } while (changed);

        List<IrBlock> blocks = new ArrayList<>();
        for (IrBlock block : function.blocks()) {
            if (!flow.reachable().contains(block.label())) { blocks.add(block); continue; }
            List<IrInstruction> prefix = flow.effectiveInstructions(block.label());
            List<IrInstruction> instructions = new ArrayList<>();
            transfer(prefix, meet(block.label(), entry, flow, outgoing), instructions);
            instructions.addAll(block.instructions().subList(prefix.size(), block.instructions().size()));
            blocks.add(new IrBlock(block.label(), instructions));
        }
        return blocks.equals(function.blocks()) ? function : new IrFunction(function.name(), function.returnType(),
                function.parameters(), function.variadic(), blocks, function.range());
    }

    private Set<String> meet(String label, String entry, IrControlFlow flow, Map<String, Set<String>> outgoing) {
        if (label.equals(entry)) return Set.of();
        Set<String> incoming = null;
        for (String predecessor : flow.predecessors(label)) {
            Set<String> previous = outgoing.get(predecessor);
            if (!flow.reachable().contains(predecessor) || previous == null) continue;
            if (incoming == null) incoming = new HashSet<>(previous);
            else incoming.retainAll(previous);
        }
        return incoming;
    }

    private Set<String> transfer(List<IrInstruction> instructions, Set<String> incoming, List<IrInstruction> output) {
        Set<String> initialized = new HashSet<>(incoming);
        for (IrInstruction instruction : instructions) {
            if (instruction instanceof IrDeclareLocalInstruction declare) initialized.remove(declare.local().name());
            else if (instruction instanceof IrStoreLocalInstruction store) initialized.add(store.local().name());
            else if (instruction instanceof IrCheckInitializedInstruction check) {
                if (!initialized.add(check.local().name())) continue;
                // A retained check proves the flag on its normal continuation. On failure
                // the backend returns through the function trap epilogue instead.
            }
            // Only declare/store-local alter the private native flag. Pointer writes,
            // memcpy and calls neither reset a known flag nor prove an unknown one.
            if (output != null) output.add(instruction);
        }
        return initialized;
    }
}
