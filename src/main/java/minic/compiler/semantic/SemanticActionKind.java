package minic.compiler.semantic;

/**
 * 语义分析动作类型。
 */
public enum SemanticActionKind {
    /**
     * 注册结构体符号。
     */
    REGISTER_STRUCTS,

    /**
     * 校验声明类型。
     */
    CHECK_TYPES,

    /**
     * 计算结构体布局。
     */
    COMPUTE_STRUCT_LAYOUTS,

    /**
     * 注册函数符号。
     */
    REGISTER_FUNCTIONS,

    /**
     * 校验 main 函数。
     */
    VALIDATE_MAIN,

    /**
     * 分析函数体。
     */
    ANALYZE_FUNCTION_BODY,

    /**
     * 分析函数体语句。
     */
    ANALYZE_STATEMENT,

    /**
     * 校验函数返回路径。
     */
    VALIDATE_FUNCTION_RETURN,

    /**
     * 报告 diagnostic。
     */
    REPORT_DIAGNOSTIC
}
