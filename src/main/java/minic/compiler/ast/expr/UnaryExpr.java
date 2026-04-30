package minic.compiler.ast.expr;

import minic.compiler.lexer.TokenKind;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 一元表达式。
 *
 * @param operator 操作符 token 类型
 * @param operand 操作数表达式
 * @param range 表达式源码范围
 */
public record UnaryExpr(TokenKind operator, Expression operand, SourceRange range) implements Expression {
    /**
     * 创建一元表达式。
     *
     * @param operator 操作符 token 类型
     * @param operand 操作数表达式
     * @param range 表达式源码范围
     */
    public UnaryExpr {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(operand, "operand");
        Objects.requireNonNull(range, "range");
    }
}
