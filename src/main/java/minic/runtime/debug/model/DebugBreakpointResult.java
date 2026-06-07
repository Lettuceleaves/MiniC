package minic.runtime.debug;

import java.util.Objects;

/**
 * 断点设置结果。
 *
 * @param accepted 是否接受
 * @param breakpoint 断点；拒绝时为 {@code null}
 * @param message 解释
 */
public record DebugBreakpointResult(
        boolean accepted,
        DebugBreakpoint breakpoint,
        String message
) {
    /**
     * 创建断点设置结果。
     */
    public DebugBreakpointResult {
        Objects.requireNonNull(message, "message");
    }

    /**
     * 接受断点。
     *
     * @param breakpoint 断点
     * @return 结果
     */
    public static DebugBreakpointResult accepted(DebugBreakpoint breakpoint) {
        Objects.requireNonNull(breakpoint, "breakpoint");
        return new DebugBreakpointResult(true, breakpoint, "断点已设置在第 " + breakpoint.line() + " 行");
    }

    /**
     * 拒绝断点。
     *
     * @param line 行号
     * @return 结果
     */
    public static DebugBreakpointResult rejected(int line) {
        return new DebugBreakpointResult(false, null, "第 " + line + " 行当前不可断");
    }
}
