package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Removes unreachable control flow and unused, nontrapping computations. */
public final class DeadCodeEliminationPass implements IrPass {
    @Override public String name() { return "dead-code-elimination"; }
    @Override public IrResult apply(IrResult input) {
        Objects.requireNonNull(input, "input");
        var functions = input.functions().stream().map(this::eliminate).toList();
        if (functions.equals(input.functions())) return input;
        return new IrResult(functions, input.stringData(), input.globalData(), input.externalFunctionNames(),
                input.externalObjectNames(), input.structLayouts(), input.currentAstNode(), input.currentSubject(), input.displayNames(), input.entryFunction());
    }

    private IrFunction eliminate(IrFunction source) {
        IrControlFlow original = IrControlFlow.analyze(source);
        List<IrBlock> folded = new ArrayList<>();
        for (IrBlock block : source.blocks()) {
            if (!original.reachable().contains(block.label())) continue;
            var prefix = original.effectiveInstructions(block.label()).stream().map(this::foldBranch).toList();
            folded.add(new IrBlock(block.label(), prefix));
        }
        IrFunction reachableFunction = withBlocks(source, folded);
        IrControlFlow flow = IrControlFlow.analyze(reachableFunction);
        List<IrBlock> blocks = folded.stream().filter(block -> flow.reachable().contains(block.label())).toList();

        // Backward may-liveness works for repeated definitions; no SSA assumption.
        Map<String, Set<String>> liveIn = new LinkedHashMap<>();
        Map<String, Set<String>> liveOut = new LinkedHashMap<>();
        blocks.forEach(block -> { liveIn.put(block.label(), Set.of()); liveOut.put(block.label(), Set.of()); });
        boolean changed;
        do {
            changed = false;
            for (int i = blocks.size() - 1; i >= 0; i--) {
                IrBlock block = blocks.get(i);
                Set<String> outgoing = new HashSet<>();
                for (String successor : flow.successors(block.label())) outgoing.addAll(liveIn.get(successor));
                Set<String> incoming = transfer(block.instructions(), outgoing, null);
                if (!incoming.equals(liveIn.get(block.label())) || !outgoing.equals(liveOut.get(block.label()))) changed = true;
                liveIn.put(block.label(), incoming);
                liveOut.put(block.label(), outgoing);
            }
        } while (changed);

        List<IrBlock> result = new ArrayList<>();
        for (IrBlock block : blocks) {
            var retained = new ArrayList<IrInstruction>();
            transfer(block.instructions(), liveOut.get(block.label()), retained);
            result.add(new IrBlock(block.label(), retained.reversed()));
        }
        return result.equals(source.blocks()) ? source : withBlocks(source, result);
    }

    private Set<String> transfer(List<IrInstruction> instructions, Set<String> outgoing, List<IrInstruction> retained) {
        Set<String> live = new HashSet<>(outgoing);
        for (int index = instructions.size() - 1; index >= 0; index--) {
            IrInstruction instruction = instructions.get(index);
            IrTemporary result = IrValueUses.result(instruction);
            if (result != null && !live.contains(result.name()) && removable(instruction)) continue;
            if (retained != null) retained.add(instruction);
            if (result != null) live.remove(result.name());
            for (IrValue input : IrValueUses.inputs(instruction)) {
                if (input instanceof IrTemporary value) live.add(value.name());
            }
        }
        return live;
    }

    private IrInstruction foldBranch(IrInstruction instruction) {
        if (!(instruction instanceof IrBranchInstruction branch)
                || !(branch.condition() instanceof IrConstant constant)) return instruction;
        long value = constant.value();
        // Frontend bool constants are canonical; do not guess at malformed bool representations.
        if (constant.type() == IrType.BOOL && value != 0 && value != 1) return instruction;
        int bytes = constant.type().sizeBytes();
        if (bytes < Long.BYTES) value &= (1L << (bytes * Byte.SIZE)) - 1;
        // Floating conditions retain comparisons and floating-environment effects.
        return new IrJumpInstruction(value != 0 ? branch.thenLabel() : branch.elseLabel(), branch.range());
    }

    private boolean removable(IrInstruction instruction) {
        return switch (instruction) {
            case IrMoveInstruction ignored -> true;
            case IrBinaryInstruction binary -> binary.operator() != IrBinaryOperator.DIVIDE
                    && binary.operator() != IrBinaryOperator.MODULO
                    && !binary.left().type().isFloatingScalar() && !binary.right().type().isFloatingScalar();
            case IrUnaryInstruction unary -> !unary.operand().type().isFloatingScalar();
            case IrCastInstruction cast -> !cast.value().type().isFloatingScalar() && !cast.result().type().isFloatingScalar();
            case IrSelectInstruction select -> !select.condition().type().isFloatingScalar();
            case IrLoadLocalInstruction load -> !load.volatileAccess();
            case IrAddressOfLocalInstruction ignored -> true;
            case IrElementAddressInstruction ignored -> true;
            case IrFieldAddressInstruction ignored -> true;
            // Calls, stores, pointer loads, volatile accesses and runtime checks stay observable.
            default -> false;
        };
    }

    private IrFunction withBlocks(IrFunction source, List<IrBlock> blocks) {
        return new IrFunction(source.name(), source.returnType(), source.parameters(), source.variadic(), blocks, source.range());
    }
}
