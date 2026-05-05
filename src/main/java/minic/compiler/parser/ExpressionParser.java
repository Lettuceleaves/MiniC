package minic.compiler.parser;

import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.BoolLiteralExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.CharLiteralExpr;
import minic.compiler.ast.expr.ConditionalExpr;
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
import minic.compiler.ast.expr.PostfixUpdateExpr;
import minic.compiler.ast.expr.SizeofExpr;
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
        Expression expression = parseConditional();
        if (matchAssignmentOperator()) {
            Token operatorToken = state.previous();
            Expression value = parseAssignment();
            if (isAssignmentTarget(expression) && value != null) {
                return buildAssignment(expression, operatorToken.kind(), value);
            }
            state.report(operatorToken, "赋值左侧必须是可赋值表达式");
            return value;
        }

        return expression;
    }

    private boolean matchAssignmentOperator() {
        return state.match(TokenKind.EQUAL)
                || state.match(TokenKind.PLUS_EQUAL)
                || state.match(TokenKind.MINUS_EQUAL)
                || state.match(TokenKind.STAR_EQUAL)
                || state.match(TokenKind.SLASH_EQUAL)
                || state.match(TokenKind.PERCENT_EQUAL)
                || state.match(TokenKind.AMPERSAND_EQUAL)
                || state.match(TokenKind.PIPE_EQUAL)
                || state.match(TokenKind.CARET_EQUAL)
                || state.match(TokenKind.LESS_LESS_EQUAL)
                || state.match(TokenKind.GREATER_GREATER_EQUAL);
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

    private Expression parseConditional() {
        Expression condition = parseLogicalOr();
        if (!state.match(TokenKind.QUESTION)) {
            return condition;
        }
        Expression thenExpression = parseExpression();
        state.consume(TokenKind.COLON, "期望 ':'");
        Expression elseExpression = parseConditional();
        if (condition == null || thenExpression == null || elseExpression == null) {
            return condition;
        }
        ConditionalExpr conditionalExpr = new ConditionalExpr(
                condition,
                thenExpression,
                elseExpression,
                new SourceRange(
                        condition.range().sourceFile(),
                        condition.range().startOffset(),
                        elseExpression.range().endOffset()
                )
        );
        state.build(conditionalExpr, "ConditionalExpr", conditionalExpr.range());
        return conditionalExpr;
    }

    private Expression parseLogicalOr() {
        Expression expression = parseLogicalAnd();
        while (state.match(TokenKind.PIPE_PIPE)) {
            Token operator = state.previous();
            Expression right = parseLogicalAnd();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseLogicalAnd() {
        Expression expression = parseBitwiseOr();
        while (state.match(TokenKind.AMPERSAND_AMPERSAND)) {
            Token operator = state.previous();
            Expression right = parseBitwiseOr();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseBitwiseOr() {
        Expression expression = parseBitwiseXor();
        while (state.match(TokenKind.PIPE)) {
            Token operator = state.previous();
            Expression right = parseBitwiseXor();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseBitwiseXor() {
        Expression expression = parseBitwiseAnd();
        while (state.match(TokenKind.CARET)) {
            Token operator = state.previous();
            Expression right = parseBitwiseAnd();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseBitwiseAnd() {
        Expression expression = parseEquality();
        while (state.match(TokenKind.AMPERSAND)) {
            Token operator = state.previous();
            Expression right = parseEquality();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
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
        Expression expression = parseShift();
        while (state.match(TokenKind.LESS)
                || state.match(TokenKind.LESS_EQUAL)
                || state.match(TokenKind.GREATER)
                || state.match(TokenKind.GREATER_EQUAL)) {
            Token operator = state.previous();
            Expression right = parseShift();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseShift() {
        Expression expression = parseAdditive();
        while (state.match(TokenKind.LESS_LESS) || state.match(TokenKind.GREATER_GREATER)) {
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
        while (state.match(TokenKind.STAR) || state.match(TokenKind.SLASH) || state.match(TokenKind.PERCENT)) {
            Token operator = state.previous();
            Expression right = parseUnary();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseUnary() {
        if (state.match(TokenKind.AMPERSAND)
                || state.match(TokenKind.STAR)
                || state.match(TokenKind.BANG)
                || state.match(TokenKind.TILDE)
                || state.match(TokenKind.PLUS_PLUS)
                || state.match(TokenKind.MINUS_MINUS)) {
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
        if (state.match(TokenKind.SIZEOF)) {
            return parseSizeof(state.previous());
        }
        return parsePostfix();
    }

    private Expression parseSizeof(Token sizeofToken) {
        if (state.match(TokenKind.LEFT_PAREN)) {
            TypeParser typeParser = new TypeParser(state);
            if (typeParser.canStartType()) {
                ParsedType type = typeParser.parseType("期望 sizeof 类型");
                Token endToken = state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
                if (type == null || endToken == null) {
                    return null;
                }
                SizeofExpr sizeofExpr = new SizeofExpr(
                        null,
                        type.type(),
                        new SourceRange(
                                sizeofToken.range().sourceFile(),
                                sizeofToken.range().startOffset(),
                                endToken.range().endOffset()
                        )
                );
                state.build(sizeofExpr, "SizeofExpr type", sizeofExpr.range());
                return sizeofExpr;
            }
            Expression grouped = parseExpression();
            Token endToken = state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
            if (grouped == null || endToken == null) {
                return grouped;
            }
            GroupingExpr groupingExpr = new GroupingExpr(grouped, new SourceRange(
                    sizeofToken.range().sourceFile(),
                    sizeofToken.range().startOffset(),
                    endToken.range().endOffset()
            ));
            SizeofExpr sizeofExpr = new SizeofExpr(groupingExpr, null, groupingExpr.range());
            state.build(sizeofExpr, "SizeofExpr expression", sizeofExpr.range());
            return sizeofExpr;
        }
        Expression expression = parseUnary();
        if (expression == null) {
            return null;
        }
        SizeofExpr sizeofExpr = new SizeofExpr(
                expression,
                null,
                new SourceRange(
                        sizeofToken.range().sourceFile(),
                        sizeofToken.range().startOffset(),
                        expression.range().endOffset()
                )
        );
        state.build(sizeofExpr, "SizeofExpr expression", sizeofExpr.range());
        return sizeofExpr;
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
            if (state.match(TokenKind.PLUS_PLUS) || state.match(TokenKind.MINUS_MINUS)) {
                Token operatorToken = state.previous();
                if (!isAssignmentTarget(expression)) {
                    state.report(operatorToken, "自增自减操作数必须是可赋值表达式");
                    continue;
                }
                IntegerLiteralExpr one = new IntegerLiteralExpr(1, "1", operatorToken.range());
                TokenKind binaryOperator = operatorToken.kind() == TokenKind.PLUS_PLUS ? TokenKind.PLUS : TokenKind.MINUS;
                BinaryExpr updatedValue = new BinaryExpr(
                        expression,
                        binaryOperator,
                        one,
                        new SourceRange(
                                expression.range().sourceFile(),
                                expression.range().startOffset(),
                                operatorToken.range().endOffset()
                        )
                );
                state.build(updatedValue, "BinaryExpr " + updatedValue.operator(), updatedValue.range());
                expression = buildAssignment(expression, TokenKind.EQUAL, updatedValue);
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

    private AssignmentExpr buildAssignment(Expression target, TokenKind operator, Expression value) {
        AssignmentExpr assignmentExpr = new AssignmentExpr(
                target,
                operator,
                value,
                new SourceRange(
                        target.range().sourceFile(),
                        target.range().startOffset(),
                        value.range().endOffset()
                )
        );
        state.build(assignmentExpr, "AssignmentExpr", assignmentExpr.range());
        return assignmentExpr;
    }
}
