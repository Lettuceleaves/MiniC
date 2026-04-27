package minic.compiler.ast;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 赋值表达式。
 *
 * @param targetName 赋值目标名称
 * @param value 右侧表达式
 * @param range 表达式源码范围
 */
public record AssignmentExpr(String targetName, Expression value, SourceRange range) implements Expression {
    /**
     * 创建赋值表达式。
     *
     * @param targetName 赋值目标名称
     * @param value 右侧表达式
     * @param range 表达式源码范围
     */
    public AssignmentExpr {
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(range, "range");
        if (targetName.isBlank()) {
            throw new IllegalArgumentException("targetName must not be blank");
        }
    }
}
