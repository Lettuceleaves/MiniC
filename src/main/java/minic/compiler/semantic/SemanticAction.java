package minic.compiler.semantic;

import minic.diagnostics.Diagnostic;

import java.util.Objects;
import java.util.Optional;

/**
 * 语义分析单步动作。
 *
 * @param kind 动作类型
 * @param subject 动作对象摘要
 * @param diagnostic 本动作新增的代表性 diagnostic；没有时为 {@code null}
 */
public record SemanticAction(SemanticActionKind kind, String subject, Diagnostic diagnostic) {
    /**
     * 创建语义动作。
     *
     * @param kind 动作类型
     * @param subject 动作对象摘要
     * @param diagnostic 本动作新增的代表性 diagnostic；没有时为 {@code null}
     */
    public SemanticAction {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
    }

    /**
     * 创建无 diagnostic 动作。
     *
     * @param kind 动作类型
     * @param subject 动作对象摘要
     * @return 语义动作
     */
    public static SemanticAction of(SemanticActionKind kind, String subject) {
        return new SemanticAction(kind, subject, null);
    }

    /**
     * 创建 diagnostic 动作。
     *
     * @param subject 动作对象摘要
     * @param diagnostic diagnostic
     * @return 语义动作
     */
    public static SemanticAction diagnostic(String subject, Diagnostic diagnostic) {
        return new SemanticAction(SemanticActionKind.REPORT_DIAGNOSTIC, subject, diagnostic);
    }

    /**
     * 返回代表性 diagnostic。
     *
     * @return diagnostic Optional
     */
    public Optional<Diagnostic> diagnosticOptional() {
        return Optional.ofNullable(diagnostic);
    }
}
