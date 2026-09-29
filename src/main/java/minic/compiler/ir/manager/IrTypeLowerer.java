package minic.compiler.ir.manager;

import minic.compiler.ir.model.IrType;
import minic.compiler.type.MiniType;

public final class IrTypeLowerer {
    private IrTypeLowerer() {
    }

    public static IrType lower(MiniType type) {
        if (type.isPointer()) {
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
        if (type.equals(MiniType.LONG)) {
            return IrType.LONG;
        }
        if (type.equals(MiniType.FLOAT)) {
            return IrType.FLOAT;
        }
        if (type.equals(MiniType.DOUBLE)) {
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
