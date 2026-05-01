package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 结构体字段访问表达式。
 *
 * @param target 被访问的结构体表达式
 * @param fieldName 字段名
 * @param range 表达式源码范围
 */
public record FieldAccessExpr(Expression target, String fieldName, SourceRange range) implements Expression {
    /**
     * 创建结构体字段访问表达式。
     *
     * @param target 被访问的结构体表达式
     * @param fieldName 字段名
     * @param range 表达式源码范围
     */
    public FieldAccessExpr {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(range, "range");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
    }
}
