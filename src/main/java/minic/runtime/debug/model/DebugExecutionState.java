package minic.runtime.debug;

/**
 * Debugger 会话执行状态。
 */
public enum DebugExecutionState {
    /**
     * 已暂停，可接受单步或连续运行命令。
     */
    PAUSED,

    /**
     * 连续运行中。
     */
    RUNNING,

    /**
     * 已完成。
     */
    COMPLETED,

    /**
     * 发生运行时错误或诊断。
     */
    FAILED,

    /**
     * 会话已关闭。
     */
    CLOSED
}
