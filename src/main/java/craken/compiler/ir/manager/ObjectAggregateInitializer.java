package craken.compiler.ir.manager;

import craken.SourceRange;
import craken.compiler.ir.instruction.MemoryInstruction.*;
import craken.compiler.ir.model.IrType;
import craken.compiler.ir.value.IrValue;
import craken.compiler.ir.value.IrValue.IrConstant;
import craken.compiler.parser.node.Expression.AggregateInitExpr;
import craken.compiler.type.CrakenType;

/** Explicit subobject initialization in declaration order; never pre-zero constructed elements. */
final class ObjectAggregateInitializer {
    private ObjectAggregateInitializer() { }

    static void emit(IrFunctionBuilder builder, ExpressionLowerer expressions, IrValue address,
                     CrakenType type, AggregateInitExpr initializer, SourceRange range) {
        CrakenType raw = type.unqualified();
        if (!type.isStruct() && !type.isArray()) {
            expressions.initializeAt(address, type, initializer.values().getFirst(), range);
            return;
        }
        int count = raw instanceof CrakenType.ArrayType array ? array.length()
                : builder.fieldCount(((CrakenType.StructType) raw).name());
        for (int index = 0; index < count; index++) {
            CrakenType memberType;
            var memberAddress = builder.newTemporary(IrType.POINTER);
            if (raw instanceof CrakenType.ArrayType array) {
                memberType = qualifiedMember(type, array.elementType());
                builder.addInstruction(new IrElementAddressInstruction(memberAddress, address,
                        new IrConstant(index), memberType, builder.sizeOf(memberType), range));
            } else {
                String owner = ((CrakenType.StructType) raw).name();
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

    private static CrakenType qualifiedMember(CrakenType owner, CrakenType member) {
        var qualifiers = java.util.EnumSet.noneOf(CrakenType.TypeQualifier.class);
        qualifiers.addAll(member.qualifiers());
        if (owner.isConstQualified()) qualifiers.add(CrakenType.TypeQualifier.CONST);
        if (owner.isVolatileQualified()) qualifiers.add(CrakenType.TypeQualifier.VOLATILE);
        return CrakenType.qualified(member.unqualified(), qualifiers);
    }
}
