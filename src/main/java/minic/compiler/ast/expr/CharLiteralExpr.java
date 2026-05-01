package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 字符字面量表达式。
 *
 * @param value Java char 值
 * @param lexeme 源码中的原始文本
 * @param range 表达式源码范围
 */
public record CharLiteralExpr(char value, String lexeme, SourceRange range) implements Expression {
    /**
     * 创建字符字面量表达式。
     *
     * @param value Java char 值
     * @param lexeme 源码中的原始文本
     * @param range 表达式源码范围
     */
    public CharLiteralExpr {
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(range, "range");
    }
}
