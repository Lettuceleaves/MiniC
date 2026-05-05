package minic.compiler.ast.expr;

import minic.source.SourceRange;
import minic.compiler.lexer.TokenKind;

import java.util.Objects;
import java.util.Optional;

/**
 * 赋值表达式。
 *
 * @param target 赋值目标表达式
 * @param operator 赋值操作符；普通赋值为 {@code =}
 * @param value 右侧表达式
 * @param range 表达式源码范围
 */
public record AssignmentExpr(Expression target, TokenKind operator, Expression value, SourceRange range) implements Expression {
    /**
     * 创建赋值表达式。
     *
     * @param target 赋值目标表达式
     * @param operator 赋值操作符；普通赋值为 {@code =}
     * @param value 右侧表达式
     * @param range 表达式源码范围
     */
    public AssignmentExpr {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(range, "range");
    }

    /**
     * 创建普通赋值表达式。
     *
     * @param target 赋值目标表达式
     * @param value 右侧表达式
     * @param range 表达式源码范围
     */
    public AssignmentExpr(Expression target, Expression value, SourceRange range) {
        this(target, TokenKind.EQUAL, value, range);
    }

    /**
     * 创建变量赋值表达式。
     *
     * @param targetName 赋值目标名称
     * @param value 右侧表达式
     * @param range 表达式源码范围
     */
    public AssignmentExpr(String targetName, Expression value, SourceRange range) {
        this(new NameExpr(targetName, range), TokenKind.EQUAL, value, range);
    }

    /**
     * 返回变量赋值目标名称。
     *
     * @return 目标名称
     * @throws IllegalStateException 目标不是名称表达式时抛出
     */
    public String targetName() {
        if (target instanceof NameExpr nameExpr) {
            return nameExpr.name();
        }
        throw new IllegalStateException("assignment target is not a name");
    }

    /**
     * 返回复合赋值对应的二元操作符。
     *
     * @return 二元操作符；普通赋值时为空
     */
    public Optional<TokenKind> compoundBinaryOperator() {
        return switch (operator) {
            case PLUS_EQUAL -> Optional.of(TokenKind.PLUS);
            case MINUS_EQUAL -> Optional.of(TokenKind.MINUS);
            case STAR_EQUAL -> Optional.of(TokenKind.STAR);
            case SLASH_EQUAL -> Optional.of(TokenKind.SLASH);
            case PERCENT_EQUAL -> Optional.of(TokenKind.PERCENT);
            case AMPERSAND_EQUAL -> Optional.of(TokenKind.AMPERSAND);
            case PIPE_EQUAL -> Optional.of(TokenKind.PIPE);
            case CARET_EQUAL -> Optional.of(TokenKind.CARET);
            case LESS_LESS_EQUAL -> Optional.of(TokenKind.LESS_LESS);
            case GREATER_GREATER_EQUAL -> Optional.of(TokenKind.GREATER_GREATER);
            default -> Optional.empty();
        };
    }
}
