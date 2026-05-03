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
        UiSourceRangeDto range
) {
    public MiniCDiagnosticItem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    /**
     * 展示文本。
     *
     * @return 展示文本
     */
    public String displayText() {
        return severity + "  " + code + "  " + message;
    }
}
