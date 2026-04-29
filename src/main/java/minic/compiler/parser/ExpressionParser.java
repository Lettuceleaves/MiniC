package minic.compiler.parser;

import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.source.SourceRange;

import java.util.ArrayList;

final class ExpressionParser {
    private final ParserState state;

    ExpressionParser(ParserState state) {
        this.state = state;
    }

    Expression parseExpression() {
        return parseAssignment();
    }

    private Expression parseAssignment() {
        Expression expression = parseEquality();
        if (!state.match(TokenKind.EQUAL)) {
            return expression;
        }

        Token equalsToken = state.previous();
        Expression value = parseAssignment();
        if (expression instanceof NameExpr nameExpr && value != null) {
            return new AssignmentExpr(
                    nameExpr.name(),
                    value,
                    new SourceRange(
                            nameExpr.range().sourceFile(),
                            nameExpr.range().startOffset(),
                            value.range().endOffset()
                    )
            );
        }

        state.report(equalsToken, "赋值左侧必须是标识符");
        return value;
    }

    private Expression parseEquality() {
        Expression expression = parseRelational();
        while (state.match(TokenKind.EQUAL_EQUAL) || state.match(TokenKind.BANG_EQUAL)) {
            Token operator = state.previous();
            Expression right = parseRelational();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseRelational() {
        Expression expression = parseAdditive();
        while (state.match(TokenKind.LESS)
                || state.match(TokenKind.LESS_EQUAL)
                || state.match(TokenKind.GREATER)
                || state.match(TokenKind.GREATER_EQUAL)) {
            Token operator = state.previous();
            Expression right = parseAdditive();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseAdditive() {
        Expression expression = parseMultiplicative();
        while (state.match(TokenKind.PLUS) || state.match(TokenKind.MINUS)) {
            Token operator = state.previous();
            Expression right = parseMultiplicative();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseMultiplicative() {
        Expression expression = parsePrimary();
        while (state.match(TokenKind.STAR) || state.match(TokenKind.SLASH)) {
            Token operator = state.previous();
            Expression right = parsePrimary();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parsePrimary() {
        if (state.match(TokenKind.INTEGER_LITERAL)) {
            Token integerToken = state.previous();
            return new IntegerLiteralExpr(
                    (Integer) integerToken.literalValue(),
                    integerToken.lexeme(),
                    integerToken.range()
            );
        }
        if (state.match(TokenKind.IDENTIFIER)) {
            Token nameToken = state.previous();
            if (state.match(TokenKind.LEFT_PAREN)) {
                return finishCall(nameToken);
            }
            return new NameExpr(nameToken.lexeme(), nameToken.range());
        }
        if (state.match(TokenKind.LEFT_PAREN)) {
            Token startToken = state.previous();
            Expression expression = parseExpression();
            Token endToken = state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
            if (expression == null || endToken == null) {
                return expression;
            }
            return new GroupingExpr(
                    expression,
                    new SourceRange(
                            startToken.range().sourceFile(),
                            startToken.range().startOffset(),
                            endToken.range().endOffset()
                    )
            );
        }

        state.report(state.peek(), "期望表达式");
        if (!state.isAtEnd()) {
            state.advance();
        }
        return null;
    }

    private Expression finishCall(Token calleeToken) {
        ArrayList<Expression> arguments = new ArrayList<>();
        if (!state.check(TokenKind.RIGHT_PAREN)) {
            do {
                Expression argument = parseExpression();
                if (argument != null) {
                    arguments.add(argument);
                }
            } while (state.match(TokenKind.COMMA));
        }

        Token endToken = state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        if (endToken == null) {
            return null;
        }
        return new CallExpr(
                calleeToken.lexeme(),
                arguments,
                new SourceRange(
                        calleeToken.range().sourceFile(),
                        calleeToken.range().startOffset(),
                        endToken.range().endOffset()
                )
        );
    }

    private Expression combineBinary(Expression left, Token operator, Expression right) {
        if (left == null || right == null) {
            return left != null ? left : right;
        }
        return new BinaryExpr(
                left,
                operator.kind(),
                right,
                new SourceRange(
                        left.range().sourceFile(),
                        left.range().startOffset(),
                        right.range().endOffset()
                )
        );
    }
}
