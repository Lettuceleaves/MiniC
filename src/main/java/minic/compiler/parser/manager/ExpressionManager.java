package minic.compiler.parser.manager;

import minic.compiler.parser.Parser;
import minic.compiler.LanguageMode;

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
import minic.compiler.parser.node.Expression.AlignofExpr;
import minic.compiler.parser.node.Expression.StringLiteralExpr;
import minic.compiler.parser.node.Expression.UnaryExpr;
import minic.compiler.parser.node.Expression.VaArgExpr;
import minic.compiler.parser.node.Expression.VaCopyExpr;
import minic.compiler.parser.node.Expression.VaEndExpr;
import minic.compiler.parser.node.Expression.VaStartExpr;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.lexer.token.Token.IntegerLiteralKind;
import minic.compiler.lexer.token.Token.IntegerLiteralValue;
import minic.SourceRange;
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
        // C++ lvalue results (calls returning references, comma, conditional and
        // assignment expressions) are classified after name/type binding.
        if (state.languageMode() == LanguageMode.CPP17_ALGORITHM) return expression != null;
        if (expression instanceof GroupingExpr grouping) {
            return isAssignmentTarget(grouping.expression());
        }
        if (expression instanceof NameExpr || expression instanceof Expression.QualifiedNameExpr) {
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
        Expression elseExpression = state.languageMode() == LanguageMode.CPP17_ALGORITHM
                ? parseAssignment() : parseConditional();
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
        if (state.check(TokenType.LEFT_PAREN) && typeReader.canStartTypeAt(1)
                && typeReader.cppTypeOperandAt(1, false)) {
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
            // Unary operators accept a cast-expression. C prefix updates alone
            // require a unary-expression; C++ prefix updates accept casts too.
            boolean cPrefixUpdate = state.languageMode() == LanguageMode.C
                    && (operator.type() == TokenType.PLUS_PLUS || operator.type() == TokenType.MINUS_MINUS);
            Expression operand = cPrefixUpdate ? parseUnary() : parseCast();
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
        if (state.match(TokenType.ALIGNOF)) {
            return parseAlignof(state.previous());
        }
        return parsePostfix();
    }

    private Expression parseAlignof(Token alignofToken) {
        if (!state.match(TokenType.LEFT_PAREN)) {
            state.report(state.peek(), "alignof 后期望 '('");
            return null;
        }
        if (typeReader.canStartType() && typeReader.cppTypeOperandAt(0, true)) {
            Parser.ParsedType type = typeReader.parseType("期望 alignof 类型");
            Token endToken = state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
            if (type == null || endToken == null) return null;
            AlignofExpr expression = new AlignofExpr(
                    null, type.type(), SourceRange.span(alignofToken.range(), endToken.range()));
            state.build(expression, "AlignofExpr type", expression.range());
            return expression;
        }
        Expression operand = parseExpression();
        Token endToken = state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
        if (operand == null || endToken == null) return operand;
        AlignofExpr expression = new AlignofExpr(
                operand, null, SourceRange.span(alignofToken.range(), endToken.range()));
        state.build(expression, "AlignofExpr expression", expression.range());
        return expression;
    }

    private Expression parseSizeof(Token sizeofToken) {
        if (state.match(TokenType.LEFT_PAREN)) {
            if (typeReader.canStartType() && typeReader.cppTypeOperandAt(0, true)) {
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
                expression = new Expression.PostfixUpdateExpr(expression, operatorToken.type(),
                        SourceRange.span(expression.range(), operatorToken.range()));
                state.build(expression, "PostfixUpdateExpr " + operatorToken.type(), expression.range());
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
        if (typeReader.cppConstructionDelimiterAt(0) >= 0) {
            Parser.ParsedType type = typeReader.parseCppConstructionType();
            var initializer = parseConstructionInitializer();
            if (type == null || initializer == null) return null;
            var construction = new minic.compiler.parser.node.CppConstructionExpr(type.type(), initializer,
                    type.range(), SourceRange.span(type.range(), initializer.range()));
            state.build(construction, "CppConstructionExpr", construction.range());
            return construction;
        }
        if (state.languageMode() == LanguageMode.CPP17_ALGORITHM && state.match(TokenType.THIS)) {
            var expression = new Expression.ThisExpr(state.previous().range());
            state.build(expression, "ThisExpr", expression.range());
            return expression;
        }
        if (state.languageMode() == minic.compiler.LanguageMode.CPP17_ALGORITHM
                && (state.check(TokenType.SCOPE) || state.check(TokenType.IDENTIFIER)
                && state.peekAt(1).type() == TokenType.SCOPE)) {
            var name = minic.compiler.parser.CppNameParser.parseName(state);
            if (name == null) return null;
            var expression = new Expression.QualifiedNameExpr(name);
            state.build(expression, "QualifiedNameExpr", expression.range());
            return expression;
        }
        if (state.match(TokenType.VA_START)) {
            return parseVaStart(state.previous());
        }
        if (state.match(TokenType.VA_ARG)) {
            return parseVaArg(state.previous());
        }
        if (state.match(TokenType.VA_COPY)) {
            return parseVaCopy(state.previous());
        }
        if (state.match(TokenType.VA_END)) {
            return parseVaEnd(state.previous());
        }
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
            int charValue;
            minic.compiler.parser.node.Expression.LiteralEncoding charEncoding;
            if (charToken.literalValue() instanceof Character character) {
                charValue = character;
                charEncoding = minic.compiler.parser.node.Expression.LiteralEncoding.ORDINARY;
            } else {
                Token.CharacterLiteralValue literal = (Token.CharacterLiteralValue) charToken.literalValue();
                charValue = literal.value();
                charEncoding = switch (literal.encoding()) {
                    case ORDINARY -> minic.compiler.parser.node.Expression.LiteralEncoding.ORDINARY;
                    case UTF8 -> minic.compiler.parser.node.Expression.LiteralEncoding.UTF8;
                    case UTF16 -> minic.compiler.parser.node.Expression.LiteralEncoding.UTF16;
                    case UTF32 -> minic.compiler.parser.node.Expression.LiteralEncoding.UTF32;
                };
            }
            CharLiteralExpr expr = new CharLiteralExpr(
                    charValue,
                    charEncoding,
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
        if (state.match(TokenType.NULL_LITERAL)
                || state.languageMode() == LanguageMode.CPP17_ALGORITHM && state.match(TokenType.NULLPTR)) {
            Token nullToken = state.previous();
            NullLiteralExpr expr = new NullLiteralExpr(nullToken.lexeme(), nullToken.range());
            state.build(expr, "NullLiteralExpr " + expr.lexeme(), expr.range());
            return expr;
        }
        if (state.match(TokenType.STRING_LITERAL)) {
            Token firstToken = state.previous();
            Token lastToken = firstToken;
            Token.StringLiteralValue first = stringLiteral(firstToken);
            StringBuilder value = new StringBuilder(first.value());
            minic.compiler.parser.node.Expression.LiteralEncoding encoding = literalEncoding(first.encoding());
            StringBuilder lexeme = new StringBuilder(firstToken.lexeme());
            // ISO C translation phase 6 concatenates adjacent string literal
            // tokens.  Format macros such as "%" PRId64 depend on this being
            // done after preprocessing but before expression semantics.
            while (state.match(TokenType.STRING_LITERAL)) {
                lastToken = state.previous();
                Token.StringLiteralValue next = stringLiteral(lastToken);
                var nextEncoding = literalEncoding(next.encoding());
                if ((encoding == minic.compiler.parser.node.Expression.LiteralEncoding.UTF8
                        && nextEncoding != minic.compiler.parser.node.Expression.LiteralEncoding.ORDINARY
                        && nextEncoding != minic.compiler.parser.node.Expression.LiteralEncoding.UTF8)
                        || (nextEncoding == minic.compiler.parser.node.Expression.LiteralEncoding.UTF8
                        && encoding != minic.compiler.parser.node.Expression.LiteralEncoding.ORDINARY
                        && encoding != minic.compiler.parser.node.Expression.LiteralEncoding.UTF8)) {
                    state.report(lastToken, "u8 字符串不能与宽字符串拼接");
                }
                if (encoding == minic.compiler.parser.node.Expression.LiteralEncoding.ORDINARY) encoding = nextEncoding;
                value.append(next.value());
                lexeme.append(lastToken.lexeme());
            }
            StringLiteralExpr expr = new StringLiteralExpr(
                    value.toString(),
                    encoding,
                    lexeme.toString(),
                    SourceRange.span(firstToken.range(), lastToken.range())
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

    private static Token.StringLiteralValue stringLiteral(Token token) {
        return token.literalValue() instanceof String value
                ? new Token.StringLiteralValue(value, Token.LiteralEncoding.ORDINARY)
                : (Token.StringLiteralValue) token.literalValue();
    }

    private static minic.compiler.parser.node.Expression.LiteralEncoding literalEncoding(Token.LiteralEncoding encoding) {
        return switch (encoding) {
            case ORDINARY -> minic.compiler.parser.node.Expression.LiteralEncoding.ORDINARY;
            case UTF8 -> minic.compiler.parser.node.Expression.LiteralEncoding.UTF8;
            case UTF16 -> minic.compiler.parser.node.Expression.LiteralEncoding.UTF16;
            case UTF32 -> minic.compiler.parser.node.Expression.LiteralEncoding.UTF32;
        };
    }

    private Expression parseVaStart(Token intrinsic) {
        state.consume(TokenType.LEFT_PAREN, "va_start 后期望 '('");
        Expression list = parseAssignment();
        state.consume(TokenType.COMMA, "va_start 期望 ','");
        Expression lastParameter = parseAssignment();
        Token end = state.consume(TokenType.RIGHT_PAREN, "va_start 期望 ')'");
        if (list == null || lastParameter == null || end == null) return null;
        VaStartExpr expression = new VaStartExpr(
                list, lastParameter, SourceRange.span(intrinsic.range(), end.range()));
        state.build(expression, "VaStartExpr", expression.range());
        return expression;
    }

    private Expression parseVaArg(Token intrinsic) {
        state.consume(TokenType.LEFT_PAREN, "va_arg 后期望 '('");
        Expression list = parseAssignment();
        state.consume(TokenType.COMMA, "va_arg 期望 ','");
        Parser.ParsedType requestedType = typeReader.parseType("va_arg 期望类型");
        Token end = state.consume(TokenType.RIGHT_PAREN, "va_arg 期望 ')'");
        if (list == null || requestedType == null || end == null) return null;
        VaArgExpr expression = new VaArgExpr(
                list, requestedType.type(), SourceRange.span(intrinsic.range(), end.range()));
        state.build(expression, "VaArgExpr " + requestedType.type(), expression.range());
        return expression;
    }

    private Expression parseVaCopy(Token intrinsic) {
        state.consume(TokenType.LEFT_PAREN, "va_copy 后期望 '('");
        Expression destination = parseAssignment();
        state.consume(TokenType.COMMA, "va_copy 期望 ','");
        Expression source = parseAssignment();
        Token end = state.consume(TokenType.RIGHT_PAREN, "va_copy 期望 ')'");
        if (destination == null || source == null || end == null) return null;
        VaCopyExpr expression = new VaCopyExpr(
                destination, source, SourceRange.span(intrinsic.range(), end.range()));
        state.build(expression, "VaCopyExpr", expression.range());
        return expression;
    }

    private Expression parseVaEnd(Token intrinsic) {
        state.consume(TokenType.LEFT_PAREN, "va_end 后期望 '('");
        Expression list = parseAssignment();
        Token end = state.consume(TokenType.RIGHT_PAREN, "va_end 期望 ')'");
        if (list == null || end == null) return null;
        VaEndExpr expression = new VaEndExpr(list, SourceRange.span(intrinsic.range(), end.range()));
        state.build(expression, "VaEndExpr", expression.range());
        return expression;
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

    private minic.compiler.parser.node.CppInitializer parseConstructionInitializer() {
        Token start = state.advance();
        boolean list = start.type() == TokenType.LEFT_BRACE;
        TokenType close = list ? TokenType.RIGHT_BRACE : TokenType.RIGHT_PAREN;
        ArrayList<Expression> arguments = new ArrayList<>();
        if (!state.check(close)) {
            do {
                Expression value = state.check(TokenType.LEFT_BRACE) ? parseConstructionInitializer() : parseAssignment();
                if (value == null) return null;
                arguments.add(value);
                if (!state.match(TokenType.COMMA)) break;
                if (list && state.check(close)) break;
            } while (!state.isAtEnd());
        }
        Token end = state.consume(close, list ? "构造初始化期望 '}'" : "构造初始化期望 ')'");
        return end == null ? null : new minic.compiler.parser.node.CppInitializer(list
                ? minic.compiler.parser.node.CppInitializer.Kind.DIRECT_LIST
                : minic.compiler.parser.node.CppInitializer.Kind.DIRECT_PAREN,
                arguments, SourceRange.span(start.range(), end.range()));
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
