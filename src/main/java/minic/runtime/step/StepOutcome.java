package minic.runtime.step;

/**
 * 单步推进结果类别。
 */
public enum StepOutcome {
    /**
     * 成功推进了一步。
     */
    ADVANCED,

    /**
     * 当前阶段刚刚完成。
     */
    STAGE_COMPLETED,

    /**
     * 当前游标无法继续推进。
     */
    CANNOT_ADVANCE,

    /**
     * 请求的能力已预留但当前不支持。
     */
    UNSUPPORTED,

    /**
     * 推进失败，并携带 diagnostics。
     */
    FAILED
}
