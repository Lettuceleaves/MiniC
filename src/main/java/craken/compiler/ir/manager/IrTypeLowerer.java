package craken.compiler.ir.manager;

import craken.compiler.ir.model.IrType;
import craken.compiler.type.CrakenType;

public final class IrTypeLowerer {
    private IrTypeLowerer() {
    }

    public static IrType lower(CrakenType type) {
        if (type.containsPlaceholder()) throw new IllegalArgumentException("source placeholder requires deduction before IR: " + type);
        if (type.containsReference()) throw new IllegalArgumentException("source reference type requires binding before IR lowering: " + type);
        type = type.unqualified();
        if (type.isPointer() || type.isVaList()) {
            return IrType.POINTER;
        }
        if (type.isArray() || type.isStruct()) {
            // 聚合表达式在 IR 中始终表示为指向其存储的地址；完整类型保留在 CrakenType 中。
            return IrType.POINTER;
        }
        if (type.equals(CrakenType.BOOL)) {
            return IrType.BOOL;
        }
        if (type.equals(CrakenType.CHAR)) {
            return IrType.CHAR;
        }
        if (type.equals(CrakenType.SIGNED_CHAR)) {
            return IrType.SIGNED_CHAR;
        }
        if (type.equals(CrakenType.UNSIGNED_CHAR)) {
            return IrType.UNSIGNED_CHAR;
        }
        if (type.equals(CrakenType.SHORT)) {
            return IrType.SHORT;
        }
        if (type.equals(CrakenType.UNSIGNED_SHORT)) {
            return IrType.UNSIGNED_SHORT;
        }
        if (type.equals(CrakenType.UNSIGNED_INT)) {
            return IrType.UNSIGNED_INT;
        }
        if (type.equals(CrakenType.LONG)) {
            return IrType.LONG;
        }
        if (type.equals(CrakenType.UNSIGNED_LONG)) {
            return IrType.UNSIGNED_LONG;
        }
        if (type.equals(CrakenType.LONG_LONG)) {
            return IrType.LONG_LONG;
        }
        if (type.equals(CrakenType.UNSIGNED_LONG_LONG)) {
            return IrType.UNSIGNED_LONG_LONG;
        }
        if (type.equals(CrakenType.FLOAT)) {
            return IrType.FLOAT;
        }
        if (type.equals(CrakenType.DOUBLE) || type.equals(CrakenType.LONG_DOUBLE)) {
            return IrType.DOUBLE;
        }
        if (type.isNullPointer()) {
            return IrType.POINTER;
        }
        if (type.isFunction()) {
            throw new IllegalArgumentException("function value must be represented by a pointer: " + type);
        }
        return IrType.INT;
    }
}
