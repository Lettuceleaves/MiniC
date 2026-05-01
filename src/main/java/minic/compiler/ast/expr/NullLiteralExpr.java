package minic.compiler.ast.expr;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * NULL 空指针常量表达式。
 *
 * @param lexeme 源码中的原始文本
 * @param range 表达式源码范围
 */
public record NullLiteralExpr(String lexeme, SourceRange range) implements Expression {
    /**
     * 创建 NULL 空指针常量表达式。
     *
     * @param lexeme 源码中的原始文本
     * @param range 表达式源码范围
     */
    public NullLiteralExpr {
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(range, "range");
    }
}
