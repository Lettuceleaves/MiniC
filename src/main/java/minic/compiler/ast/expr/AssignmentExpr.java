package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 赋值表达式。
 *
 * @param target 赋值目标表达式
 * @param value 右侧表达式
 * @param range 表达式源码范围
 */
public record AssignmentExpr(Expression target, Expression value, SourceRange range) implements Expression {
    /**
     * 创建赋值表达式。
     *
     * @param target 赋值目标表达式
     * @param value 右侧表达式
     * @param range 表达式源码范围
     */
    public AssignmentExpr {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(range, "range");
    }

    /**
     * 创建变量赋值表达式。
     *
     * @param targetName 赋值目标名称
     * @param value 右侧表达式
     * @param range 表达式源码范围
     */
    public AssignmentExpr(String targetName, Expression value, SourceRange range) {
        this(new NameExpr(targetName, range), value, range);
    }

    /**
     * 返回变量赋值目标名称。
     *
     * @return 目标名称
     * @throws IllegalStateException 目标不是名称表达式时抛出
     */
    public String targetName() {
        if (target instanceof NameExpr nameExpr) {
            return nameExpr.name();
        }
        throw new IllegalStateException("assignment target is not a name");
    }
}
