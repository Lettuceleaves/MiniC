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
     * 进入函数 IR lowering。
     */
    BEGIN_FUNCTION,

    /**
     * 产出函数体语句 IR。
     */
    LOWER_STATEMENT,

    /**
     * 处理函数体 AST 节点。
     */
    LOWER_AST_NODE,

    /**
     * 完成函数 IR。
     */
    COMPLETE_FUNCTION,

    /**
     * 完成 IR 模块。
     */
    COMPLETE_MODULE
}
