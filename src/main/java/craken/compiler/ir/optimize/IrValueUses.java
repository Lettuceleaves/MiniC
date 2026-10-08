package craken.compiler.ir.optimize;

import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.instruction.CallInstruction.*;
import craken.compiler.ir.instruction.ComputeInstruction.*;
import craken.compiler.ir.instruction.ControlInstruction.*;
import craken.compiler.ir.instruction.MemoryInstruction.*;
import craken.compiler.ir.value.IrValue;
import craken.compiler.ir.value.IrValue.IrTemporary;

import java.util.ArrayList;
import java.util.List;

/** Value operands, excluding storage slots: a parameter reference is a live slot, not a temporary. */
final class IrValueUses {
    private IrValueUses() { }

    static IrTemporary result(IrInstruction instruction) {
        return switch (instruction) {
            case IrBinaryInstruction value -> value.result();
            case IrUnaryInstruction value -> value.result();
            case IrCastInstruction value -> value.result();
            case IrMoveInstruction value -> value.result();
            case IrSelectInstruction value -> value.result();
            case IrAddressOfLocalInstruction value -> value.result();
            case IrLoadLocalInstruction value -> value.result();
            case IrLoadPointerInstruction value -> value.result();
            case IrElementAddressInstruction value -> value.result();
            case IrFieldAddressInstruction value -> value.result();
            case IrCallInstruction value -> value.result();
            case IrIndirectCallInstruction value -> value.result();
            case IrDeclareLocalInstruction ignored -> null;
            case IrCheckInitializedInstruction ignored -> null;
            case IrStoreLocalInstruction ignored -> null;
            case IrStorePointerInstruction ignored -> null;
            case IrMemCopyInstruction ignored -> null;
            case IrBranchInstruction ignored -> null;
            case IrJumpInstruction ignored -> null;
            case IrReturnInstruction ignored -> null;
            case IrCheckNonZeroInstruction ignored -> null;
            case IrTrapInstruction ignored -> null;
            case IrCaptureInstruction ignored -> null;
        };
    }

    static List<IrValue> inputs(IrInstruction instruction) {
        return switch (instruction) {
            case IrBinaryInstruction value -> List.of(value.left(), value.right());
            case IrUnaryInstruction value -> List.of(value.operand());
            case IrCastInstruction value -> List.of(value.value());
            case IrMoveInstruction value -> List.of(value.value());
            case IrSelectInstruction value -> List.of(value.condition(), value.thenValue(), value.elseValue());
            case IrLoadPointerInstruction value -> List.of(value.address());
            case IrElementAddressInstruction value -> List.of(value.baseAddress(), value.index());
            case IrFieldAddressInstruction value -> List.of(value.baseAddress());
            case IrCallInstruction value -> value.arguments();
            case IrIndirectCallInstruction value -> {
                var inputs = new ArrayList<IrValue>();
                inputs.add(value.calleeAddress()); inputs.addAll(value.arguments()); yield List.copyOf(inputs);
            }
            case IrStoreLocalInstruction value -> List.of(value.value());
            case IrStorePointerInstruction value -> List.of(value.address(), value.value());
            case IrMemCopyInstruction value -> List.of(value.destination(), value.source());
            case IrBranchInstruction value -> List.of(value.condition());
            case IrReturnInstruction value -> value.value() == null ? List.of() : List.of(value.value());
            case IrCheckNonZeroInstruction value -> List.of(value.value());
            case IrDeclareLocalInstruction ignored -> List.of();
            case IrCheckInitializedInstruction ignored -> List.of();
            case IrLoadLocalInstruction ignored -> List.of();
            case IrAddressOfLocalInstruction ignored -> List.of();
            case IrJumpInstruction ignored -> List.of();
            case IrTrapInstruction ignored -> List.of();
            case IrCaptureInstruction ignored -> List.of();
        };
    }
}
