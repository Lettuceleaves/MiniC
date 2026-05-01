package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * float 字面量表达式。
 *
 * @param value Java float 值
 * @param lexeme 源码中的原始文本
 * @param range 表达式源码范围
 */
public record FloatLiteralExpr(float value, String lexeme, SourceRange range) implements Expression {
    /**
     * 创建 float 字面量表达式。
     *
     * @param value Java float 值
     * @param lexeme 源码中的原始文本
     * @param range 表达式源码范围
     */
    public FloatLiteralExpr {
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(range, "range");
    }
}
