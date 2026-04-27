package minic.compiler.ir;

/**
 * IR 值的基接口。
 */
public sealed interface IrValue permits IrConstant, IrTemporary, IrParameterRef {
    /**
     * 返回该值的类型。
     *
     * @return IR 值类型
     */
    IrType type();
}
