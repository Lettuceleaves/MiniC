package minic.compiler.ast.expr;

import minic.compiler.lexer.TokenKind;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 后缀自增自减表达式。
 *
 * @param target 被更新表达式
 * @param operator 操作符
 * @param range 表达式源码范围
 */
public record PostfixUpdateExpr(Expression target, TokenKind operator, SourceRange range) implements Expression {
    /**
     * 创建后缀自增自减表达式。
     *
     * @param target 被更新表达式
     * @param operator 操作符
     * @param range 表达式源码范围
     */
    public PostfixUpdateExpr {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(range, "range");
        if (operator != TokenKind.PLUS_PLUS && operator != TokenKind.MINUS_MINUS) {
            throw new IllegalArgumentException("operator must be ++ or --");
        }
    }
}
