package minic.compiler.ast;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * 函数调用表达式。
 *
 * @param calleeName 被调用函数名
 * @param arguments 实参表达式列表
 * @param range 表达式源码范围
 */
public record CallExpr(String calleeName, List<Expression> arguments, SourceRange range) implements Expression {
    /**
     * 创建函数调用表达式，并防御性复制实参列表。
     *
     * @param calleeName 被调用函数名
     * @param arguments 实参表达式列表
     * @param range 表达式源码范围
     */
    public CallExpr {
        Objects.requireNonNull(calleeName, "calleeName");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(range, "range");
        if (calleeName.isBlank()) {
            throw new IllegalArgumentException("calleeName must not be blank");
        }
        arguments = List.copyOf(arguments);
    }
}
