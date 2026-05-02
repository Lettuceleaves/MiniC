package minic.compiler.stage;

/**
 * 编译阶段执行状态。
 */
public enum CompilerStageStatus {
    /**
     * 尚未开始。
     */
    NOT_STARTED,

    /**
     * 正在执行。
     */
    RUNNING,

    /**
     * 已完成。
     */
    COMPLETED,

    /**
     * 已失败。
     */
    FAILED
}
