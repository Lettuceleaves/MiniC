package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * 结构体大括号初始化表达式，如 {1, 2, 3}。
 *
 * @param values 按字段顺序排列的初始化值列表
 * @param range 表达式源码范围
 */
public record StructInitExpr(List<Expression> values, SourceRange range) implements Expression {
    /**
     * 创建结构体初始化表达式。
     *
     * @param values 按字段顺序排列的初始化值列表
     * @param range 表达式源码范围
     */
    public StructInitExpr {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(range, "range");
        values = List.copyOf(values);
    }
}
