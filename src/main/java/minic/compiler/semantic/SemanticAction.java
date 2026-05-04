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
 * @param astNode 当前动作对应 AST 节点；没有时为 {@code null}
 * @param scope 当前动作所属最低作用域；没有时为 {@code null}
 */
public record SemanticAction(SemanticActionKind kind, String subject, Diagnostic diagnostic, Object astNode, Scope scope) {
    /**
     * 创建语义动作。
     *
     * @param kind 动作类型
     * @param subject 动作对象摘要
     * @param diagnostic 本动作新增的代表性 diagnostic；没有时为 {@code null}
     * @param astNode 当前动作对应 AST 节点；没有时为 {@code null}
     * @param scope 当前动作所属最低作用域；没有时为 {@code null}
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
        return of(kind, subject, null, null);
    }

    /**
     * 创建携带当前 AST 节点和作用域的动作。
     *
     * @param kind 动作类型
     * @param subject 动作对象摘要
     * @param astNode 当前 AST 节点
     * @param scope 当前作用域
     * @return 语义动作
     */
    public static SemanticAction of(SemanticActionKind kind, String subject, Object astNode, Scope scope) {
        return new SemanticAction(kind, subject, null, astNode, scope);
    }

    /**
     * 创建 diagnostic 动作。
     *
     * @param subject 动作对象摘要
     * @param diagnostic diagnostic
     * @return 语义动作
     */
    public static SemanticAction diagnostic(String subject, Diagnostic diagnostic) {
        return diagnostic(subject, diagnostic, null, null);
    }

    /**
     * 创建携带上下文的 diagnostic 动作。
     *
     * @param subject 动作对象摘要
     * @param diagnostic diagnostic
     * @param astNode 当前 AST 节点
     * @param scope 当前作用域
     * @return 语义动作
     */
    public static SemanticAction diagnostic(String subject, Diagnostic diagnostic, Object astNode, Scope scope) {
        return new SemanticAction(SemanticActionKind.REPORT_DIAGNOSTIC, subject, diagnostic, astNode, scope);
    }

    /**
     * 返回代表性 diagnostic。
     *
     * @return diagnostic Optional
     */
    public Optional<Diagnostic> diagnosticOptional() {
        return Optional.ofNullable(diagnostic);
    }

    /**
     * 返回当前 AST 节点。
     *
     * @return AST 节点 Optional
     */
    public Optional<Object> astNodeOptional() {
        return Optional.ofNullable(astNode);
    }

    /**
     * 返回当前最低作用域。
     *
     * @return 作用域 Optional
     */
    public Optional<Scope> scopeOptional() {
        return Optional.ofNullable(scope);
    }
}
