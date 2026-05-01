package minic.compiler.ir.value;

import minic.compiler.ir.model.IrType;

/**
 * IR 整数常量。
 *
 * @param value 常量值
 * @param type 常量类型
 */
public record IrConstant(long value, IrType type) implements IrValue {
    public IrConstant {
        java.util.Objects.requireNonNull(type, "type");
        if (!type.isIntegerScalar() && type != IrType.POINTER) {
            throw new IllegalArgumentException("constant type must be integer scalar or pointer");
        }
    }

    /**
     * 创建 int 常量。
     *
     * @param value int 常量值
     */
    public IrConstant(int value) {
        this(value, IrType.INT);
    }

    @Override
    public String toString() {
        return "IrConstant[value=" + value + ", type=" + type + "]";
    }
}
