package minic.compiler.ir.value;

import minic.compiler.ir.model.IrType;

/**
 * IR 浮点常量。
 *
 * @param value double 保存的浮点值
 * @param type 常量类型，只能是 float 或 double
 */
public record IrFloatConstant(double value, IrType type) implements IrValue {
    public IrFloatConstant {
        java.util.Objects.requireNonNull(type, "type");
        if (!type.isFloatingScalar()) {
            throw new IllegalArgumentException("floating constant type must be float or double");
        }
    }

    public IrFloatConstant(float value) {
        this(value, IrType.FLOAT);
    }
}
