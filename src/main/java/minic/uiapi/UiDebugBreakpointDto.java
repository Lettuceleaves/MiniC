package minic.uiapi;

/**
 * UI Debug 断点 DTO。
 *
 * @param line 源码行
 * @param enabled 是否启用
 */
public record UiDebugBreakpointDto(int line, boolean enabled) {
    public UiDebugBreakpointDto {
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
    }

    static UiDebugBreakpointDto fromLine(int line, boolean enabled) {
        return new UiDebugBreakpointDto(line, enabled);
    }
}
