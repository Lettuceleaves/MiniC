package minic.diagnostics;

/**
 * 诊断严重级别。
 */
public enum DiagnosticSeverity {
    /**
     * 阻止当前编译或执行阶段继续成功完成的错误。
     */
    ERROR,

    /**
     * 不阻止继续执行但需要展示给用户的警告。
     */
    WARNING,

    /**
     * 辅助说明信息。
     */
    INFO
}
