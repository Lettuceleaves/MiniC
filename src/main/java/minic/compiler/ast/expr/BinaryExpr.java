package minic.compiler.ast.expr;

import minic.compiler.lexer.TokenKind;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 二元运算表达式。
 *
 * @param left 左操作数
 * @param operator 运算符 token 类型
 * @param right 右操作数
 * @param range 表达式源码范围
 */
public record BinaryExpr(Expression left, TokenKind operator, Expression right, SourceRange range) implements Expression {
    /**
     * 创建二元运算表达式。
     *
     * @param left 左操作数
     * @param operator 运算符 token 类型
     * @param right 右操作数
     * @param range 表达式源码范围
     */
    public BinaryExpr {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(range, "range");
    }
}
