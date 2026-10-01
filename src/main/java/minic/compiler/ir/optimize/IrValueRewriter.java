package minic.compiler.ir.optimize;

import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.value.IrValue;
import java.util.function.UnaryOperator;

/** Rewrites value inputs only, retaining result definitions, storage identities and effects. */
final class IrValueRewriter {
    private IrValueRewriter() { }
    static IrInstruction inputs(IrInstruction instruction, UnaryOperator<IrValue> map) {
        return switch (instruction) {
            case IrBinaryInstruction v -> new IrBinaryInstruction(v.result(),v.operator(),map.apply(v.left()),map.apply(v.right()),v.range());
            case IrUnaryInstruction v -> new IrUnaryInstruction(v.result(),v.operator(),map.apply(v.operand()),v.range());
            case IrCastInstruction v -> new IrCastInstruction(v.result(),map.apply(v.value()),v.range());
            case IrMoveInstruction v -> new IrMoveInstruction(v.result(),map.apply(v.value()),v.range());
            case IrSelectInstruction v -> new IrSelectInstruction(v.result(),map.apply(v.condition()),map.apply(v.thenValue()),map.apply(v.elseValue()),v.range());
            case IrLoadPointerInstruction v -> new IrLoadPointerInstruction(v.result(),map.apply(v.address()),v.volatileAccess(),v.range());
            case IrStorePointerInstruction v -> new IrStorePointerInstruction(map.apply(v.address()),map.apply(v.value()),v.volatileAccess(),v.range());
            case IrStoreLocalInstruction v -> new IrStoreLocalInstruction(v.local(),map.apply(v.value()),v.volatileAccess(),v.range());
            case IrElementAddressInstruction v -> new IrElementAddressInstruction(v.result(),map.apply(v.baseAddress()),map.apply(v.index()),v.elementType(),v.elementSizeBytes(),v.range());
            case IrFieldAddressInstruction v -> new IrFieldAddressInstruction(v.result(),map.apply(v.baseAddress()),v.ownerStructName(),v.fieldName(),v.offset(),v.fieldType(),v.range());
            case IrMemCopyInstruction v -> new IrMemCopyInstruction(map.apply(v.destination()),map.apply(v.source()),v.sizeBytes(),v.volatileAccess(),v.range());
            case IrCallInstruction v -> new IrCallInstruction(v.result(),v.calleeName(),v.arguments().stream().map(map).toList(),v.variadic(),v.range());
            case IrIndirectCallInstruction v -> new IrIndirectCallInstruction(v.result(),map.apply(v.calleeAddress()),v.arguments().stream().map(map).toList(),v.variadic(),v.range());
            case IrBranchInstruction v -> new IrBranchInstruction(map.apply(v.condition()),v.thenLabel(),v.elseLabel(),v.range());
            case IrReturnInstruction v -> new IrReturnInstruction(v.value()==null?null:map.apply(v.value()),v.range());
            case IrCheckNonZeroInstruction v -> new IrCheckNonZeroInstruction(map.apply(v.value()),v.range());
            case IrDeclareLocalInstruction v -> v;
            case IrCheckInitializedInstruction v -> v;
            case IrAddressOfLocalInstruction v -> v;
            case IrLoadLocalInstruction v -> v;
            case IrJumpInstruction v -> v;
            case IrTrapInstruction v -> v;
        };
    }
}
