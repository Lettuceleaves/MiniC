package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 布尔字面量表达式。
 *
 * @param value Java boolean 值
 * @param lexeme 源码中的原始文本
 * @param range 表达式源码范围
 */
public record BoolLiteralExpr(boolean value, String lexeme, SourceRange range) implements Expression {
    /**
     * 创建布尔字面量表达式。
     *
     * @param value Java boolean 值
     * @param lexeme 源码中的原始文本
     * @param range 表达式源码范围
     */
    public BoolLiteralExpr {
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(range, "range");
    }
}
