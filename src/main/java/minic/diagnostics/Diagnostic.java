package minic.diagnostics;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 表示编译或运行阶段产生的一条结构化诊断。
 *
 * @param code 稳定诊断编号，例如 {@code LEX001}
 * @param severity 诊断严重级别
 * @param message 用户可读诊断信息
 * @param range 诊断关联的源码范围
 */
public record Diagnostic(
        String code,
        DiagnosticSeverity severity,
        String message,
        SourceRange range
) {
    /**
     * 创建诊断对象。
     *
     * @param code 稳定诊断编号，例如 {@code LEX001}
     * @param severity 诊断严重级别
     * @param message 用户可读诊断信息
     * @param range 诊断关联的源码范围
     */
    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(range, "range");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
