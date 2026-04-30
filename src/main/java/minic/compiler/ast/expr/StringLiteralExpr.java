package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 字符串字面量表达式。
 *
 * @param value 解码后的字符串值
 * @param lexeme 源码中的原始文本
 * @param range 表达式源码范围
 */
public record StringLiteralExpr(String value, String lexeme, SourceRange range) implements Expression {
    /**
     * 创建字符串字面量表达式。
     *
     * @param value 解码后的字符串值
     * @param lexeme 源码中的原始文本
     * @param range 表达式源码范围
     */
    public StringLiteralExpr {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(range, "range");
    }
}
