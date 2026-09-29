package minic.compiler.parser.manager;

import minic.compiler.parser.Parser;

import minic.compiler.parser.node.Expression.AssignmentExpr;
import minic.compiler.parser.node.Expression.BinaryExpr;
import minic.compiler.parser.node.Expression.BoolLiteralExpr;
import minic.compiler.parser.node.Expression.CallExpr;
import minic.compiler.parser.node.Expression.CharLiteralExpr;
import minic.compiler.parser.node.Expression.ConditionalExpr;
import minic.compiler.parser.node.Expression.CastExpr;
import minic.compiler.parser.node.Expression.CommaExpr;
import minic.compiler.parser.node.Expression.DoubleLiteralExpr;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.FieldAccessExpr;
import minic.compiler.parser.node.Expression.FloatLiteralExpr;
import minic.compiler.parser.node.Expression.GroupingExpr;
import minic.compiler.parser.node.Expression.IndexExpr;
import minic.compiler.parser.node.Expression.IntegerLiteralExpr;
import minic.compiler.parser.node.Expression.IntegerConstantExpr;
import minic.compiler.parser.node.Expression.LongLiteralExpr;
import minic.compiler.parser.node.Expression.NameExpr;
import minic.compiler.parser.node.Expression.NullLiteralExpr;
import minic.compiler.parser.node.Expression.SizeofExpr;
import minic.compiler.parser.node.Expression.StringLiteralExpr;
import minic.compiler.parser.node.Expression.UnaryExpr;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.lexer.token.Token.IntegerLiteralKind;
import minic.compiler.lexer.token.Token.IntegerLiteralValue;
import minic.source.SourceRange;
import minic.compiler.type.MiniType;

import java.util.ArrayList;

public final class ExpressionManager {
    private final Parser.Context state;
    private final Parser.TypeReader typeReader;
    private final java.util.Map<String, Long> enumConstants;

    public ExpressionManager(Parser.Context state, Parser.TypeReader typeReader) {
        this(state, typeReader, java.util.Map.of());
    }

    public ExpressionManager(Parser.Context state, Parser.TypeReader typeReader,
                             java.util.Map<String, Long> enumConstants) {
        this.state = state;
        this.typeReader = typeReader;
        this.enumConstants = enumConstants;
    }

    public Expression parseExpression() {
        state.enter("expression");
        Expression expression = parseAssignment();
        if (expression != null && state.match(TokenType.COMMA)) {
            ArrayList<Expression> expressions = new ArrayList<>();
            expressions.add(expression);
            do {
                Expression next = parseAssignment();
                if (next != null) expressions.add(next);
            } while (state.match(TokenType.COMMA));
            if (expressions.size() > 1) {
                expression = new CommaExpr(expressions,
                        SourceRange.span(expressions.getFirst().range(), expressions.getLast().range()));
                state.build(expression, "CommaExpr", expression.range());
            }
        }
        if (expression != null) {
            state.exit("expression", expression.range());
        }
        return expression;
    }

    /** Parse an assignment-expression where comma is a surrounding-list separator. */
    public Expression parseAssignmentExpression() {
        return parseAssignment();
    }

    private Expression parseAssignment() {
        Expression expression = parseConditional();
        if (matchAssignmentOperator()) {
            Token operatorToken = state.previous();
            Expression value = parseAssignment();
            if (isAssignmentTarget(expression) && value != null) {
                return buildAssignment(expression, operatorToken.type(), value);
            }
            state.report(operatorToken, "赋值左侧必须是可赋值表达式");
            return value;
        }

        return expression;
    }

    private boolean matchAssignmentOperator() {
        return state.match(TokenType.EQUAL)
                || state.match(TokenType.PLUS_EQUAL)
                || state.match(TokenType.MINUS_EQUAL)
                || state.match(TokenType.STAR_EQUAL)
                || state.match(TokenType.SLASH_EQUAL)
                || state.match(TokenType.PERCENT_EQUAL)
                || state.match(TokenType.AMPERSAND_EQUAL)
                || state.match(TokenType.PIPE_EQUAL)
                || state.match(TokenType.CARET_EQUAL)
                || state.match(TokenType.LESS_LESS_EQUAL)
                || state.match(TokenType.GREATER_GREATER_EQUAL);
    }

    private boolean isAssignmentTarget(Expression expression) {
        if (expression instanceof NameExpr) {
            return true;
        }
        if (expression instanceof IndexExpr || expression instanceof FieldAccessExpr) {
            return true;
        }
        return expression instanceof UnaryExpr unaryExpr && unaryExpr.operator() == TokenType.STAR;
    }

    private Expression parseConditional() {
        Expression condition = parseLogicalOr();
        if (!state.match(TokenType.QUESTION)) {
            return condition;
        }
        Expression thenExpression = parseExpression();
        state.consume(TokenType.COLON, "期望 ':'");
        Expression elseExpression = parseConditional();
        if (condition == null || thenExpression == null || elseExpression == null) {
            return condition;
        }
        ConditionalExpr conditionalExpr = new ConditionalExpr(
                condition,
                thenExpression,
                elseExpression,
                SourceRange.span(condition.range(), elseExpression.range())
        );
        state.build(conditionalExpr, "ConditionalExpr", conditionalExpr.range());
        return conditionalExpr;
    }

    private Expression parseLogicalOr() {
        Expression expression = parseLogicalAnd();
        while (state.match(TokenType.PIPE_PIPE)) {
            Token operator = state.previous();
            Expression right = parseLogicalAnd();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseLogicalAnd() {
        Expression expression = parseBitwiseOr();
        while (state.match(TokenType.AMPERSAND_AMPERSAND)) {
            Token operator = state.previous();
            Expression right = parseBitwiseOr();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseBitwiseOr() {
        Expression expression = parseBitwiseXor();
        while (state.match(TokenType.PIPE)) {
            Token operator = state.previous();
            Expression right = parseBitwiseXor();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseBitwiseXor() {
        Expression expression = parseBitwiseAnd();
        while (state.match(TokenType.CARET)) {
            Token operator = state.previous();
            Expression right = parseBitwiseAnd();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseBitwiseAnd() {
        Expression expression = parseEquality();
        while (state.match(TokenType.AMPERSAND)) {
            Token operator = state.previous();
            Expression right = parseEquality();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseEquality() {
        Expression expression = parseRelational();
        while (state.match(TokenType.EQUAL_EQUAL) || state.match(TokenType.BANG_EQUAL)) {
            Token operator = state.previous();
            Expression right = parseRelational();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseRelational() {
        Expression expression = parseShift();
        while (state.match(TokenType.LESS)
                || state.match(TokenType.LESS_EQUAL)
                || state.match(TokenType.GREATER)
                || state.match(TokenType.GREATER_EQUAL)) {
            Token operator = state.previous();
            Expression right = parseShift();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseShift() {
        Expression expression = parseAdditive();
        while (state.match(TokenType.LESS_LESS) || state.match(TokenType.GREATER_GREATER)) {
            Token operator = state.previous();
            Expression right = parseAdditive();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseAdditive() {
        Expression expression = parseMultiplicative();
        while (state.match(TokenType.PLUS) || state.match(TokenType.MINUS)) {
            Token operator = state.previous();
            Expression right = parseMultiplicative();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseMultiplicative() {
        Expression expression = parseCast();
        while (state.match(TokenType.STAR) || state.match(TokenType.SLASH) || state.match(TokenType.PERCENT)) {
            Token operator = state.previous();
            Expression right = parseCast();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    private Expression parseCast() {
        if (state.check(TokenType.LEFT_PAREN) && typeReader.canStartTypeAt(1)) {
            Token start = state.advance();
            Parser.ParsedType target = typeReader.parseType("期望转换目标类型");
            Token close = state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
            Expression operand = parseCast();
            if (target == null || close == null || operand == null) return operand;
            CastExpr cast = new CastExpr(target.type(), operand, SourceRange.span(start.range(), operand.range()));
            state.build(cast, "CastExpr " + target.type(), cast.range());
            return cast;
        }
        return parseUnary();
    }

    private Expression parseUnary() {
        if (state.match(TokenType.AMPERSAND)
                || state.match(TokenType.STAR)
                || state.match(TokenType.BANG)
                || state.match(TokenType.TILDE)
                || state.match(TokenType.PLUS_PLUS)
                || state.match(TokenType.MINUS_MINUS)
                || state.match(TokenType.MINUS)
                || state.match(TokenType.PLUS)) {
            Token operator = state.previous();
            Expression operand = parseUnary();
            if (operand == null) {
                return null;
            }
            UnaryExpr unaryExpr = new UnaryExpr(
                    operator.type(),
                    operand,
                    SourceRange.span(operator.range(), operand.range())
            );
            state.build(unaryExpr, "UnaryExpr " + unaryExpr.operator(), unaryExpr.range());
            return unaryExpr;
        }
        if (state.match(TokenType.SIZEOF)) {
            return parseSizeof(state.previous());
        }
        return parsePostfix();
    }

    private Expression parseSizeof(Token sizeofToken) {
        if (state.match(TokenType.LEFT_PAREN)) {
            if (typeReader.canStartType()) {
                Parser.ParsedType type = typeReader.parseType("期望 sizeof 类型");
                Token endToken = state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
                if (type == null || endToken == null) {
                    return null;
                }
                SizeofExpr sizeofExpr = new SizeofExpr(
                        null,
                        type.type(),
                        SourceRange.span(sizeofToken.range(), endToken.range())
                );
                state.build(sizeofExpr, "SizeofExpr type", sizeofExpr.range());
                return sizeofExpr;
            }
            Expression grouped = parseExpression();
            Token endToken = state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
            if (grouped == null || endToken == null) {
                return grouped;
            }
            GroupingExpr groupingExpr = new GroupingExpr(grouped, SourceRange.span(sizeofToken.range(), endToken.range()));
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
                SourceRange.span(sizeofToken.range(), expression.range())
        );
        state.build(sizeofExpr, "SizeofExpr expression", sizeofExpr.range());
        return sizeofExpr;
    }

    private Expression parsePostfix() {
        Expression expression = parsePrimary();
        while (expression != null) {
            if (state.match(TokenType.LEFT_BRACKET)) {
                Expression index = parseExpression();
                Token endToken = state.consume(TokenType.RIGHT_BRACKET, "期望 ']'");
                if (index == null || endToken == null) {
                    return expression;
                }
                expression = new IndexExpr(
                        expression,
                        index,
                        SourceRange.span(expression.range(), endToken.range())
                );
                state.build(expression, "IndexExpr", expression.range());
                continue;
            }
            if (state.match(TokenType.DOT)) {
                expression = finishFieldAccess(expression, false);
                continue;
            }
            if (state.match(TokenType.ARROW)) {
                expression = finishFieldAccess(expression, true);
                continue;
            }
            if (state.match(TokenType.LEFT_PAREN)) {
                expression = finishCall(expression);
                continue;
            }
            if (state.match(TokenType.PLUS_PLUS) || state.match(TokenType.MINUS_MINUS)) {
                Token operatorToken = state.previous();
                if (!isAssignmentTarget(expression)) {
                    state.report(operatorToken, "自增自减操作数必须是可赋值表达式");
                    continue;
                }
                IntegerLiteralExpr one = new IntegerLiteralExpr(1, "1", operatorToken.range());
                TokenType binaryOperator = operatorToken.type() == TokenType.PLUS_PLUS ? TokenType.PLUS : TokenType.MINUS;
                BinaryExpr updatedValue = new BinaryExpr(
                        expression,
                        binaryOperator,
                        one,
                        SourceRange.span(expression.range(), operatorToken.range())
                );
                state.build(updatedValue, "BinaryExpr " + updatedValue.operator(), updatedValue.range());
                expression = buildAssignment(expression, TokenType.EQUAL, updatedValue);
                continue;
            }
            break;
        }
        return expression;
    }

    private Expression finishFieldAccess(Expression target, boolean viaPointer) {
        Token fieldToken = state.consume(TokenType.IDENTIFIER, "期望字段名");
        if (fieldToken == null) {
            return target;
        }
        FieldAccessExpr fieldAccessExpr = new FieldAccessExpr(
                target,
                fieldToken.lexeme(),
                viaPointer,
                SourceRange.span(target.range(), fieldToken.range())
        );
        state.build(fieldAccessExpr, "FieldAccessExpr " + fieldAccessExpr.fieldName(), fieldAccessExpr.range());
        return fieldAccessExpr;
    }

    private Expression parsePrimary() {
        if (state.match(TokenType.INTEGER_LITERAL)) {
            Token integerToken = state.previous();
            if (integerToken.literalValue() instanceof IntegerLiteralValue literal) {
                IntegerConstantExpr expr = new IntegerConstantExpr(
                        literal.value(), literalType(literal.kind()), integerToken.lexeme(), integerToken.range());
                state.build(expr, "IntegerConstantExpr " + expr.value(), expr.range());
                return expr;
            }
            IntegerLiteralExpr expr = new IntegerLiteralExpr(
                    (Integer) integerToken.literalValue(),
                    integerToken.lexeme(),
                    integerToken.range()
            );
            state.build(expr, "IntegerLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenType.LONG_LITERAL)) {
            Token longToken = state.previous();
            if (longToken.literalValue() instanceof IntegerLiteralValue literal) {
                IntegerConstantExpr expr = new IntegerConstantExpr(
                        literal.value(), literalType(literal.kind()), longToken.lexeme(), longToken.range());
                state.build(expr, "IntegerConstantExpr " + expr.value(), expr.range());
                return expr;
            }
            LongLiteralExpr expr = new LongLiteralExpr(
                    (Long) longToken.literalValue(),
                    longToken.lexeme(),
                    longToken.range()
            );
            state.build(expr, "LongLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenType.FLOAT_LITERAL)) {
            Token floatToken = state.previous();
            FloatLiteralExpr expr = new FloatLiteralExpr(
                    (Float) floatToken.literalValue(),
                    floatToken.lexeme(),
                    floatToken.range()
            );
            state.build(expr, "FloatLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenType.DOUBLE_LITERAL)) {
            Token doubleToken = state.previous();
            DoubleLiteralExpr expr = new DoubleLiteralExpr(
                    (Double) doubleToken.literalValue(),
                    doubleToken.lexeme(),
                    doubleToken.range()
            );
            state.build(expr, "DoubleLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenType.CHAR_LITERAL)) {
            Token charToken = state.previous();
            CharLiteralExpr expr = new CharLiteralExpr(
                    (Character) charToken.literalValue(),
                    charToken.lexeme(),
                    charToken.range()
            );
            state.build(expr, "CharLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenType.BOOL_LITERAL)) {
            Token boolToken = state.previous();
            BoolLiteralExpr expr = new BoolLiteralExpr(
                    (Boolean) boolToken.literalValue(),
                    boolToken.lexeme(),
                    boolToken.range()
            );
            state.build(expr, "BoolLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenType.NULL_LITERAL)) {
            Token nullToken = state.previous();
            NullLiteralExpr expr = new NullLiteralExpr(nullToken.lexeme(), nullToken.range());
            state.build(expr, "NullLiteralExpr " + expr.lexeme(), expr.range());
            return expr;
        }
        if (state.match(TokenType.STRING_LITERAL)) {
            Token stringToken = state.previous();
            StringLiteralExpr expr = new StringLiteralExpr(
                    (String) stringToken.literalValue(),
                    stringToken.lexeme(),
                    stringToken.range()
            );
            state.build(expr, "StringLiteralExpr " + expr.value(), expr.range());
            return expr;
        }
        if (state.match(TokenType.IDENTIFIER)) {
            Token nameToken = state.previous();
            Long enumValue = enumConstants.get(nameToken.lexeme());
            if (enumValue != null) {
                IntegerConstantExpr expr = new IntegerConstantExpr(enumValue, MiniType.INT,
                        nameToken.lexeme(), nameToken.range());
                state.build(expr, "EnumConstantExpr " + nameToken.lexeme(), expr.range());
                return expr;
            }
            NameExpr expr = new NameExpr(nameToken.lexeme(), nameToken.range());
            state.build(expr, "NameExpr " + expr.name(), expr.range());
            return expr;
        }
        if (state.match(TokenType.LEFT_PAREN)) {
            Token startToken = state.previous();
            Expression expression = parseExpression();
            Token endToken = state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
            if (expression == null || endToken == null) {
                return expression;
            }
            GroupingExpr groupingExpr = new GroupingExpr(
                    expression,
                    SourceRange.span(startToken.range(), endToken.range())
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
        if (!state.check(TokenType.RIGHT_PAREN)) {
            do {
                Expression argument = parseAssignment();
                if (argument != null) {
                    arguments.add(argument);
                }
            } while (state.match(TokenType.COMMA));
        }

        Token endToken = state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
        if (endToken == null) {
            return null;
        }
        CallExpr callExpr = new CallExpr(
                callee,
                arguments,
                SourceRange.span(callee.range(), endToken.range())
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
                operator.type(),
                right,
                SourceRange.span(left.range(), right.range())
        ));
    }

    private MiniType literalType(IntegerLiteralKind kind) {
        return switch (kind) {
            case LONG -> MiniType.LONG;
            case UNSIGNED_INT -> MiniType.UNSIGNED_INT;
            case UNSIGNED_LONG -> MiniType.UNSIGNED_LONG;
            case LONG_LONG -> MiniType.LONG_LONG;
            case UNSIGNED_LONG_LONG -> MiniType.UNSIGNED_LONG_LONG;
        };
    }

    private Expression traceBinary(BinaryExpr binaryExpr) {
        state.build(binaryExpr, "BinaryExpr " + binaryExpr.operator(), binaryExpr.range());
        return binaryExpr;
    }

    private AssignmentExpr buildAssignment(Expression target, TokenType operator, Expression value) {
        AssignmentExpr assignmentExpr = new AssignmentExpr(
                target,
                operator,
                value,
                SourceRange.span(target.range(), value.range())
        );
        state.build(assignmentExpr, "AssignmentExpr", assignmentExpr.range());
        return assignmentExpr;
    }
}
