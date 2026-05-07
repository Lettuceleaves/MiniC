package minic.uiapi;

import minic.diagnostics.Diagnostic;

import java.util.Objects;

/**
 * UI 诊断摘要。
 *
 * @param code 诊断编码
 * @param severity 严重级别
 * @param message 诊断消息
 * @param sourceName 源码名称
 * @param startOffset 起始 offset
 * @param endOffset 结束 offset
 */
public record UiDiagnosticDto(
        String code,
        String severity,
        String message,
        String sourceName,
        int startOffset,
        int endOffset
) {
    public UiDiagnosticDto {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(sourceName, "sourceName");
    }

    static UiDiagnosticDto from(Diagnostic diagnostic) {
        return new UiDiagnosticDto(
                diagnostic.code(),
                diagnostic.severity().name(),
                diagnostic.message(),
                diagnostic.range().sourceFile().path(),
                diagnostic.range().startOffset(),
                diagnostic.range().endOffset()
        );
    }
}
