package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 括号表达式。
 *
 * @param expression 括号内部表达式
 * @param range 表达式源码范围，包含左右括号
 */
public record GroupingExpr(Expression expression, SourceRange range) implements Expression {
    /**
     * 创建括号表达式。
     *
     * @param expression 括号内部表达式
     * @param range 表达式源码范围，包含左右括号
     */
    public GroupingExpr {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(range, "range");
    }
}
