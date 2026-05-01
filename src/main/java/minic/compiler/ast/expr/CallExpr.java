package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * 函数调用表达式。
 *
 * @param callee 被调用表达式
 * @param arguments 实参表达式列表
 * @param range 表达式源码范围
 */
public record CallExpr(Expression callee, List<Expression> arguments, SourceRange range) implements Expression {
    /**
     * 创建函数调用表达式，并防御性复制实参列表。
     *
     * @param callee 被调用表达式
     * @param arguments 实参表达式列表
     * @param range 表达式源码范围
     */
    public CallExpr {
        Objects.requireNonNull(callee, "callee");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(range, "range");
        arguments = List.copyOf(arguments);
    }

    /**
     * 创建直接函数调用表达式。
     *
     * @param calleeName 被调用函数名
     * @param arguments 实参表达式列表
     * @param range 表达式源码范围
     */
    public CallExpr(String calleeName, List<Expression> arguments, SourceRange range) {
        this(new NameExpr(calleeName, range), arguments, range);
        if (calleeName.isBlank()) {
            throw new IllegalArgumentException("calleeName must not be blank");
        }
    }

    /**
     * 返回直接调用的函数名。
     *
     * @return 函数名
     * @throws IllegalStateException 被调用目标不是简单名称时抛出
     */
    public String calleeName() {
        if (callee instanceof NameExpr nameExpr) {
            return nameExpr.name();
        }
        throw new IllegalStateException("callee is not a direct function name");
    }

    /**
     * 判断调用目标是否为简单名称。
     *
     * @return 直接调用返回 {@code true}
     */
    public boolean hasDirectCalleeName() {
        return callee instanceof NameExpr;
    }
}
