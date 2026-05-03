package minic.compiler.parser;

import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.BoolLiteralExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.CharLiteralExpr;
import minic.compiler.ast.expr.DoubleLiteralExpr;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.FieldAccessExpr;
import minic.compiler.ast.expr.FloatLiteralExpr;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IndexExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.LongLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.expr.NullLiteralExpr;
import minic.compiler.ast.expr.StringLiteralExpr;
import minic.compiler.ast.expr.UnaryExpr;
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
        state.enter("expression");
        Expression expression = parseAssignment();
        if (expression != null) {
            state.exit("expression", expression.range());
        }
        return expression;
    }

    private Expression parseAssignment() {
        Expression expression = parseEquality();
        if (!state.match(TokenKind.EQUAL)) {
            return expression;
        }

        Token equalsToken = state.previous();
        Expression value = parseAssignment();
        if (isAssignmentTarget(expression) && value != null) {
            AssignmentExpr assignmentExpr = new AssignmentExpr(
                    expression,
                    value,
                    new SourceRange(
                            expression.range().sourceFile(),
                            expression.range().startOffset(),
                            value.range().endOffset()
                    )
            );
            state.build(assignmentExpr, "AssignmentExpr", assignmentExpr.range());
            return assignmentExpr;
        }

        state.report(equalsToken, "赋值左侧必须是标识符");
        return value;
    }

    private boolean isAssignmentTarget(Expression expression) {
        if (expression instanceof NameExpr) {
            return true;
        }
        if (expression instanceof IndexExpr || expression instanceof FieldAccessExpr) {
            return true;
        }
        return expression instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenKind.STAR;
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
        Expression expression = parseUnary();
        while (state.match(TokenKind.STAR) || state.match(TokenKind.SLASH)) {
            Token operator = state.previous();
            Expression right = parseUnary();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseUnary() {
        if (state.match(TokenKind.AMPERSAND) || state.match(TokenKind.STAR)) {
            Token operator = state.previous();
            Expression operand = parseUnary();
            if (operand == null) {
                return null;
            }
            UnaryExpr unaryExpr = new UnaryExpr(
                    operator.kind(),
                    operand,
                    new SourceRange(
                            operator.range().sourceFile(),
                            operator.range().startOffset(),
                            operand.range().endOffset()
                    )
            );
            state.build(unaryExpr, "UnaryExpr " + unaryExpr.operator(), unaryExpr.range());
            return unaryExpr;
        }
        return parsePostfix();
    }

    private Expression parsePostfix() {
        Expression expression = parsePrimary();
        while (expression != null) {
            if (state.match(TokenKind.LEFT_BRACKET)) {
                Expression index = parseExpression();
                Token endToken = state.consume(TokenKind.RIGHT_BRACKET, "期望 ']'");
                if (index == null || endToken == null) {
                    return expression;
                }
                expression = new IndexExpr(
                        expression,
                        index,
                        new SourceRange(
                                expression.range().sourceFile(),
                                expression.range().startOffset(),
                                endToken.range().endOffset()
                        )
                );
                state.build(expression, "IndexExpr", expression.range());
                continue;
            }
            if (state.match(TokenKind.DOT)) {
                expression = finishFieldAccess(expression, false);
                continue;
            }
            if (state.match(TokenKind.ARROW)) {
                expression = finishFieldAccess(expression, true);
                continue;
            }
            if (state.match(TokenKind.LEFT_PAREN)) {
                expression = finishCall(expression);
                continue;
            }
            break;
        }
        return expression;
    }

    private Expression finishFieldAccess(Expression target, boolean viaPointer) {
        Token fieldToken = state.consume(TokenKind.IDENTIFIER, "期望字段名");
        if (fieldToken == null) {
            return target;
        }
        FieldAccessExpr fieldAccessExpr = new FieldAccessExpr(
                target,
                fieldToken.lexeme(),
                viaPointer,
                new SourceRange(
                        target.range().sourceFile(),
                        target.range().startOffset(),
                        fieldToken.range().endOffset()
                )
        );
        state.build(fieldAccessExpr, "FieldAccessExpr " + fieldAccessExpr.fieldName(), fieldAccessExpr.range());
        return fieldAccessExpr;
    }

    private Expression parsePrimary() {
        if (state.match(TokenKind.INTEGER_LITERAL)) {
            Token integerToken = state.previous();
            IntegerLiteralExpr expr = new IntegerLiteralExpr(
                    (Integer) integerToken.literalValue(),
                    integerToken.lexeme(),
                    integerToken.range()
            );
            state.build(expr, "IntegerLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenKind.LONG_LITERAL)) {
            Token longToken = state.previous();
            LongLiteralExpr expr = new LongLiteralExpr(
                    (Long) longToken.literalValue(),
                    longToken.lexeme(),
                    longToken.range()
            );
            state.build(expr, "LongLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenKind.FLOAT_LITERAL)) {
            Token floatToken = state.previous();
            FloatLiteralExpr expr = new FloatLiteralExpr(
                    (Float) floatToken.literalValue(),
                    floatToken.lexeme(),
                    floatToken.range()
            );
            state.build(expr, "FloatLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenKind.DOUBLE_LITERAL)) {
            Token doubleToken = state.previous();
            DoubleLiteralExpr expr = new DoubleLiteralExpr(
                    (Double) doubleToken.literalValue(),
                    doubleToken.lexeme(),
                    doubleToken.range()
            );
            state.build(expr, "DoubleLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenKind.CHAR_LITERAL)) {
            Token charToken = state.previous();
            CharLiteralExpr expr = new CharLiteralExpr(
                    (Character) charToken.literalValue(),
                    charToken.lexeme(),
                    charToken.range()
            );
            state.build(expr, "CharLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenKind.BOOL_LITERAL)) {
            Token boolToken = state.previous();
            BoolLiteralExpr expr = new BoolLiteralExpr(
                    (Boolean) boolToken.literalValue(),
                    boolToken.lexeme(),
                    boolToken.range()
            );
            state.build(expr, "BoolLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenKind.NULL_LITERAL)) {
            Token nullToken = state.previous();
            NullLiteralExpr expr = new NullLiteralExpr(nullToken.lexeme(), nullToken.range());
            state.build(expr, "NullLiteralExpr " + expr.lexeme(), expr.range());
            return expr;
        }
        if (state.match(TokenKind.STRING_LITERAL)) {
            Token stringToken = state.previous();
            StringLiteralExpr expr = new StringLiteralExpr(
                    (String) stringToken.literalValue(),
                    stringToken.lexeme(),
                    stringToken.range()
            );
            state.build(expr, "StringLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenKind.IDENTIFIER)) {
            Token nameToken = state.previous();
            NameExpr expr = new NameExpr(nameToken.lexeme(), nameToken.range());
            state.build(expr, "NameExpr " + expr.name(), expr.range());
            return expr;
        }
        if (state.match(TokenKind.LEFT_PAREN)) {
            Token startToken = state.previous();
            Expression expression = parseExpression();
            Token endToken = state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
            if (expression == null || endToken == null) {
                return expression;
            }
            GroupingExpr groupingExpr = new GroupingExpr(
                    expression,
                    new SourceRange(
                            startToken.range().sourceFile(),
                            startToken.range().startOffset(),
                            endToken.range().endOffset()
                    )
            );
            state.build(groupingExpr, "GroupingExpr", groupingExpr.range());
            return groupingExpr;
        }

        state.report(state.peek(), "期望表达式");
        if (!state.isAtEnd()) {
            state.advance();
        }
        return null;
    }

    private Expression finishCall(Expression callee) {
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
        CallExpr callExpr = new CallExpr(
                callee,
                arguments,
                new SourceRange(
                        callee.range().sourceFile(),
                        callee.range().startOffset(),
                        endToken.range().endOffset()
                )
        );
        state.build(callExpr, "CallExpr", callExpr.range());
        return callExpr;
    }

    private Expression combineBinary(Expression left, Token operator, Expression right) {
        if (left == null || right == null) {
            return left != null ? left : right;
        }
        return traceBinary(new BinaryExpr(
                left,
                operator.kind(),
                right,
                new SourceRange(
                        left.range().sourceFile(),
                        left.range().startOffset(),
                        right.range().endOffset()
                )
        ));
    }

    private Expression traceBinary(BinaryExpr binaryExpr) {
        state.build(binaryExpr, "BinaryExpr " + binaryExpr.operator(), binaryExpr.range());
        return binaryExpr;
    }
}
