package minic.compiler.ast.stmt;

import minic.compiler.ast.expr.Expression;
import minic.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * for 循环语句 AST 节点。
 *
 * @param initializer 初始化语句；不存在时为 {@code null}
 * @param condition 条件表达式；不存在时为 {@code null}
 * @param step 步进表达式；不存在时为 {@code null}
 * @param body 循环体语句
 * @param range 语句覆盖的源码范围
 */
public record ForStmt(
        Statement initializer,
        Expression condition,
        Expression step,
        Statement body,
        SourceRange range
) implements Statement {
    /**
     * 创建 for 循环语句。
     *
     * @param initializer 初始化语句；不存在时为 {@code null}
     * @param condition 条件表达式；不存在时为 {@code null}
     * @param step 步进表达式；不存在时为 {@code null}
     * @param body 循环体语句
     * @param range 语句源码范围
     */
    public ForStmt {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(range, "range");
    }

    /**
     * 以 {@link Optional} 形式返回初始化语句。
     *
     * @return 初始化语句；不存在时为空
     */
    public Optional<Statement> initializerOptional() {
        return Optional.ofNullable(initializer);
    }

    /**
     * 以 {@link Optional} 形式返回条件表达式。
     *
     * @return 条件表达式；不存在时为空
     */
    public Optional<Expression> conditionOptional() {
        return Optional.ofNullable(condition);
    }

    /**
     * 以 {@link Optional} 形式返回步进表达式。
     *
     * @return 步进表达式；不存在时为空
     */
    public Optional<Expression> stepOptional() {
        return Optional.ofNullable(step);
    }
}
