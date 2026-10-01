package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.ControlInstruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue.IrConstant;

import java.util.Objects;

/** Removes only integer checks whose constant value proves the failure edge unreachable. */
public final class NonZeroCheckEliminationPass implements IrPass {
    @Override public String name() { return "nonzero-check-elimination"; }
    @Override public IrResult apply(IrResult input) {
        Objects.requireNonNull(input, "input");
        var functions = input.functions().stream().map(this::eliminate).toList();
        return functions.equals(input.functions()) ? input : new IrResult(functions, input.stringData(), input.globalData(),
                input.externalFunctionNames(), input.externalObjectNames(), input.structLayouts(), input.currentAstNode(),
                input.currentSubject(), input.displayNames(), input.entryFunction());
    }

    private IrFunction eliminate(IrFunction function) {
        var blocks = function.blocks().stream().map(block -> {
            var instructions = block.instructions().stream().filter(instruction ->
                    !(instruction instanceof IrCheckNonZeroInstruction check
                            && check.value() instanceof IrConstant constant && definitelyNonzero(constant))).toList();
            return instructions.equals(block.instructions()) ? block : new IrBlock(block.label(), instructions);
        }).toList();
        return blocks.equals(function.blocks()) ? function : new IrFunction(function.name(), function.returnType(),
                function.parameters(), function.variadic(), blocks, function.range());
    }

    private static boolean definitelyNonzero(IrConstant value) {
        if (!value.type().isIntegerScalar()) return false;
        // Do not assign meaning to malformed noncanonical bool IR.
        if (value.type() == IrType.BOOL) return value.value() == 1;
        int width = value.type().sizeBytes() * Byte.SIZE;
        long bits = width == Long.SIZE ? value.value() : value.value() & ((1L << width) - 1);
        return bits != 0;
    }
}
