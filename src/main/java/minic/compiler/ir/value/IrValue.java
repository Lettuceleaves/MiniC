package minic.compiler.ir.value;

import minic.compiler.ir.model.IrType;

/**
 * IR 值的基接口。
 */
public interface IrValue {
    /**
     * 返回该值的类型。
     *
     * @return IR 值类型
     */
    IrType type();
}
