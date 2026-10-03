package minic.compiler.ir.manager;

import minic.SourceRange;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;

/** Typed zero stores shared by C aggregates and explicit value-initialization. */
final class ObjectZeroInitializer {
    private ObjectZeroInitializer() { }

    static void emit(IrFunctionBuilder builder, IrValue address, MiniType type, SourceRange range,
                     boolean completeRepresentation) {
        emit(builder, address, type, range, completeRepresentation, false);
    }

    private static void emit(IrFunctionBuilder builder, IrValue address, MiniType type, SourceRange range,
                             boolean completeRepresentation, boolean inheritedVolatile) {
        boolean volatileAccess = type.isVolatileQualified() || inheritedVolatile;
        boolean childVolatile = completeRepresentation && volatileAccess;
        MiniType raw = type.unqualified();
        if (raw instanceof MiniType.ArrayType array) {
            for (int index = 0; index < array.length(); index++) {
                var element = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrElementAddressInstruction(element, address,
                        new IrConstant(index), array.elementType(), builder.sizeOf(array.elementType()), range));
                emit(builder, element, array.elementType(), range, completeRepresentation, childVolatile);
            }
            return;
        }
        if (raw instanceof MiniType.StructType struct) {
            var covered = new java.util.BitSet();
            for (int index = 0; index < builder.fieldCount(struct.name()); index++) {
                var field = builder.fieldLayout(struct.name(), index);
                var member = builder.newTemporary(IrType.POINTER);
                builder.addInstruction(new IrFieldAddressInstruction(member, address, struct.name(),
                        field.name(), field.offset(), field.type(), range));
                emit(builder, member, field.type(), range, completeRepresentation, childVolatile);
                if (completeRepresentation) covered.set(field.offset(), field.offset() + field.size());
            }
            // Value-initialization also zeroes class padding. Legacy C aggregate
            // initialization keeps its existing member-only stores and generated IR.
            if (completeRepresentation) {
                for (int offset = covered.nextClearBit(0); offset < builder.sizeOf(type);
                     offset = covered.nextClearBit(offset + 1)) {
                    var padding = builder.newTemporary(IrType.POINTER);
                    builder.addInstruction(new IrElementAddressInstruction(padding, address,
                            new IrConstant(offset), MiniType.UNSIGNED_CHAR, 1, range));
                    builder.addInstruction(new IrStorePointerInstruction(padding,
                            new IrConstant(0, IrType.UNSIGNED_CHAR), volatileAccess, range));
                }
            }
            return;
        }
        IrType irType = IrTypeLowerer.lower(type);
        IrValue zero = irType.isFloatingScalar() ? new IrFloatConstant(0.0, irType) : new IrConstant(0, irType);
        builder.addInstruction(new IrStorePointerInstruction(address, zero, volatileAccess, range));
    }
}
