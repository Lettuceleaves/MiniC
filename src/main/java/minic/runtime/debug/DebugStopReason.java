package minic.runtime.debug;

/**
 * Debugger 停止原因。
 */
public enum DebugStopReason {
    /**
     * 会话初始状态。
     */
    START,

    /**
     * 控制命令完成。
     */
    STEP,

    /**
     * 命中断点。
     */
    BREAKPOINT,

    /**
     * 用户请求暂停。
     */
    PAUSE_REQUESTED,

    /**
     * 函数返回导致停止。
     */
    RETURN,

    /**
     * 程序执行完成。
     */
    COMPLETED,

    /**
     * 运行时错误。
     */
    ERROR,

    /**
     * 会话关闭。
     */
    CLOSED
}
