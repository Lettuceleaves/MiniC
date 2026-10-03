package minic.compiler.ir.manager;

import minic.compiler.ir.model.IrType;
import minic.compiler.type.MiniType;

public final class IrTypeLowerer {
    private IrTypeLowerer() {
    }

    public static IrType lower(MiniType type) {
        if (type.containsPlaceholder()) throw new IllegalArgumentException("source placeholder requires deduction before IR: " + type);
        if (type.containsReference()) throw new IllegalArgumentException("source reference type requires binding before IR lowering: " + type);
        type = type.unqualified();
        if (type.isPointer() || type.isVaList()) {
            return IrType.POINTER;
        }
        if (type.isArray() || type.isStruct()) {
            // 聚合表达式在 IR 中始终表示为指向其存储的地址；完整类型保留在 MiniType 中。
            return IrType.POINTER;
        }
        if (type.equals(MiniType.BOOL)) {
            return IrType.BOOL;
        }
        if (type.equals(MiniType.CHAR)) {
            return IrType.CHAR;
        }
        if (type.equals(MiniType.SIGNED_CHAR)) {
            return IrType.SIGNED_CHAR;
        }
        if (type.equals(MiniType.UNSIGNED_CHAR)) {
            return IrType.UNSIGNED_CHAR;
        }
        if (type.equals(MiniType.SHORT)) {
            return IrType.SHORT;
        }
        if (type.equals(MiniType.UNSIGNED_SHORT)) {
            return IrType.UNSIGNED_SHORT;
        }
        if (type.equals(MiniType.UNSIGNED_INT)) {
            return IrType.UNSIGNED_INT;
        }
        if (type.equals(MiniType.LONG)) {
            return IrType.LONG;
        }
        if (type.equals(MiniType.UNSIGNED_LONG)) {
            return IrType.UNSIGNED_LONG;
        }
        if (type.equals(MiniType.LONG_LONG)) {
            return IrType.LONG_LONG;
        }
        if (type.equals(MiniType.UNSIGNED_LONG_LONG)) {
            return IrType.UNSIGNED_LONG_LONG;
        }
        if (type.equals(MiniType.FLOAT)) {
            return IrType.FLOAT;
        }
        if (type.equals(MiniType.DOUBLE) || type.equals(MiniType.LONG_DOUBLE)) {
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
