package minic.compiler.ast;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 整数字面量表达式。
 *
 * @param value Java int 值
 * @param lexeme 源码中的原始文本
 * @param range 表达式源码范围
 */
public record IntegerLiteralExpr(int value, String lexeme, SourceRange range) implements Expression {
    /**
     * 创建整数字面量表达式。
     *
     * @param value Java int 值
     * @param lexeme 源码中的原始文本
     * @param range 表达式源码范围
     */
    public IntegerLiteralExpr {
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(range, "range");
    }
}
