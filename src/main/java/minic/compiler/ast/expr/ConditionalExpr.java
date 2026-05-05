package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 条件表达式 {@code condition ? thenExpr : elseExpr}。
 *
 * @param condition 条件
 * @param thenExpression 为真分支表达式
 * @param elseExpression 为假分支表达式
 * @param range 表达式源码范围
 */
public record ConditionalExpr(
        Expression condition,
        Expression thenExpression,
        Expression elseExpression,
        SourceRange range
) implements Expression {
    /**
     * 创建条件表达式。
     *
     * @param condition 条件
     * @param thenExpression 为真分支表达式
     * @param elseExpression 为假分支表达式
     * @param range 表达式源码范围
     */
    public ConditionalExpr {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(thenExpression, "thenExpression");
        Objects.requireNonNull(elseExpression, "elseExpression");
        Objects.requireNonNull(range, "range");
    }
}
