package minic.runtime.debug;

/**
 * 源码行断点。
 *
 * @param line 一基源码行号
 * @param enabled 是否启用
 */
public record DebugBreakpoint(int line, boolean enabled) {
    /**
     * 创建断点。
     */
    public DebugBreakpoint {
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
    }

    /**
     * 创建启用断点。
     *
     * @param line 一基源码行号
     * @return 断点
     */
    public static DebugBreakpoint enabled(int line) {
        return new DebugBreakpoint(line, true);
    }

    /**
     * 返回稳定 key。
     *
     * @return key
     */
    public String key() {
        return Integer.toString(line);
    }
}
