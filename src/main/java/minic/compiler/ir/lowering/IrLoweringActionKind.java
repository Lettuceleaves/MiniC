package minic.compiler.ir.lowering;

/**
 * IR lowering 动作类型。
 */
public enum IrLoweringActionKind {
    /**
     * 注册外部函数。
     */
    REGISTER_EXTERNAL,

    /**
     * 产出一个函数 IR。
     */
    LOWER_FUNCTION,

    /**
     * 完成 IR 模块。
     */
    COMPLETE_MODULE
}
