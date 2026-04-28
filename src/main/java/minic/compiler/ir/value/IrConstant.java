package minic.compiler.ir.value;

import minic.compiler.ir.model.IrType;

/**
 * IR 整数常量。
 *
 * @param value 常量值
 */
public record IrConstant(int value) implements IrValue {
    @Override
    public IrType type() {
        return IrType.INT;
    }
}
