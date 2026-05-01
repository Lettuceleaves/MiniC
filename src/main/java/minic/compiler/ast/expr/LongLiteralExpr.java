package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * long 整数字面量表达式。
 *
 * @param value Java long 值
 * @param lexeme 源码中的原始文本
 * @param range 表达式源码范围
 */
public record LongLiteralExpr(long value, String lexeme, SourceRange range) implements Expression {
    /**
     * 创建 long 整数字面量表达式。
     *
     * @param value Java long 值
     * @param lexeme 源码中的原始文本
     * @param range 表达式源码范围
     */
    public LongLiteralExpr {
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(range, "range");
    }
}
