package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * double 字面量表达式。
 *
 * @param value Java double 值
 * @param lexeme 源码中的原始文本
 * @param range 表达式源码范围
 */
public record DoubleLiteralExpr(double value, String lexeme, SourceRange range) implements Expression {
    /**
     * 创建 double 字面量表达式。
     *
     * @param value Java double 值
     * @param lexeme 源码中的原始文本
     * @param range 表达式源码范围
     */
    public DoubleLiteralExpr {
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(range, "range");
    }
}
