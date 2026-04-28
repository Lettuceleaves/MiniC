package minic.compiler.ast.stmt;

import minic.compiler.ast.expr.Expression;
import minic.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * return 语句 AST 节点。
 *
 * @param expression 返回表达式；不存在时为 {@code null}
 * @param range return 语句覆盖的源码范围
 */
public record ReturnStmt(Expression expression, SourceRange range) implements Statement {
    /**
     * 创建 return 语句节点。
     *
     * @param expression 返回表达式；不存在时为 {@code null}
     * @param range return 语句覆盖的源码范围
     */
    public ReturnStmt {
        Objects.requireNonNull(range, "range");
    }

    /**
     * 以 {@link Optional} 形式返回返回表达式。
     *
     * @return 返回表达式；不存在时为空
     */
    public Optional<Expression> expressionOptional() {
        return Optional.ofNullable(expression);
    }
}
