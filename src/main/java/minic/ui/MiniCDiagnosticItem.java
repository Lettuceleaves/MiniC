package minic.ui;

import minic.uiapi.UiSourceRangeDto;

import java.util.Objects;

/**
 * UI 诊断列表项。
 *
 * @param code 诊断编码
 * @param severity 严重级别
 * @param message 消息
 * @param range 源码范围
 */
public record MiniCDiagnosticItem(
        String code,
        String severity,
        String message,
        UiSourceRangeDto range,
        int line,
        int column
) {
    public MiniCDiagnosticItem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    public MiniCDiagnosticItem(String code, String severity, String message, UiSourceRangeDto range) {
        this(code, severity, message, range, 1, range == null ? 1 : Math.max(1, range.startOffset() + 1));
    }

    /**
     * 展示文本。
     *
     * @return 展示文本
     */
    public String displayText() {
        return severity + "  " + code + "  " + locationText() + "  " + message;
    }

    private String locationText() {
        if (range == null) {
            return "<unknown>";
        }
        return range.sourceName() + ":" + line + ":" + column;
    }
}
