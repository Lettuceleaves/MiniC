package minic.runtime.debug;

/**
 * Debugger 控制命令。
 */
public enum DebugCommand {
    /**
     * 快进到结束、断点、错误或暂停请求。
     */
    FAST_FORWARD,

    /**
     * 运行到下一个断点。
     */
    RUN_TO_BREAKPOINT,

    /**
     * 运行到程序结束或运行时错误，不因普通断点暂停。
     */
    RUN_TO_END,

    /**
     * 单步执行源码级可见语句。
     */
    STEP_OVER,

    /**
     * 步入函数调用。
     */
    STEP_INTO,

    /**
     * 执行到当前函数返回。
     */
    STEP_OUT,

    /**
     * 请求连续运行在下一条可见源码行前暂停。
     */
    PAUSE,

    /**
     * 关闭 Debug 会话。
     */
    CLOSE,

    /**
     * 重启 Debug 会话。
     */
    RESTART,

    /**
     * 回退到上一个可见调试步。
     */
    STEP_BACK,

    /**
     * 回退到本调用层的上一个可见调试步。
     */
    STEP_BACK_OVER,

    /**
     * 回退到上一个断点命中状态。
     */
    BACK_TO_BREAKPOINT,

    /**
     * 返回进入当前调用前的调用处。
     */
    BACK_TO_CALL_SITE
}
