package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 下标访问表达式。
 *
 * @param target 被索引的表达式
 * @param index 下标表达式
 * @param range 表达式源码范围
 */
public record IndexExpr(Expression target, Expression index, SourceRange range) implements Expression {
    /**
     * 创建下标访问表达式。
     *
     * @param target 被索引的表达式
     * @param index 下标表达式
     * @param range 表达式源码范围
     */
    public IndexExpr {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(range, "range");
    }
}
