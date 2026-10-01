package minic.compiler.ir.manager;

import minic.SourceRange;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.IrConstant;
import minic.compiler.parser.node.Expression.AggregateInitExpr;
import minic.compiler.type.MiniType;

/** Explicit subobject initialization in declaration order; never pre-zero constructed elements. */
final class ObjectAggregateInitializer {
    private ObjectAggregateInitializer() { }

    static void emit(IrFunctionBuilder builder, ExpressionLowerer expressions, IrValue address,
                     MiniType type, AggregateInitExpr initializer, SourceRange range) {
        MiniType raw = type.unqualified();
        if (!type.isStruct() && !type.isArray()) {
            expressions.initializeAt(address, type, initializer.values().getFirst(), range);
            return;
        }
        int count = raw instanceof MiniType.ArrayType array ? array.length()
                : builder.fieldCount(((MiniType.StructType) raw).name());
        for (int index = 0; index < count; index++) {
            MiniType memberType;
            var memberAddress = builder.newTemporary(IrType.POINTER);
            if (raw instanceof MiniType.ArrayType array) {
                memberType = qualifiedMember(type, array.elementType());
                builder.addInstruction(new IrElementAddressInstruction(memberAddress, address,
                        new IrConstant(index), memberType, builder.sizeOf(memberType), range));
            } else {
                String owner = ((MiniType.StructType) raw).name();
                var field = builder.fieldLayout(owner, index);
                memberType = qualifiedMember(type, field.type());
                builder.addInstruction(new IrFieldAddressInstruction(memberAddress, address, owner,
                        field.name(), field.offset(), memberType, range));
            }
            if (index < initializer.values().size()) {
                var value = initializer.values().get(index);
                expressions.initializeAt(memberAddress, memberType, value, value.range());
            } else {
                ObjectZeroInitializer.emit(builder, memberAddress, memberType, range, true);
            }
        }
    }

    private static MiniType qualifiedMember(MiniType owner, MiniType member) {
        var qualifiers = java.util.EnumSet.noneOf(MiniType.TypeQualifier.class);
        qualifiers.addAll(member.qualifiers());
        if (owner.isConstQualified()) qualifiers.add(MiniType.TypeQualifier.CONST);
        if (owner.isVolatileQualified()) qualifiers.add(MiniType.TypeQualifier.VOLATILE);
        return MiniType.qualified(member.unqualified(), qualifiers);
    }
}
