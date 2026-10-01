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

    /** initializer-clause: braces are allowed here, but are not general primary expressions. */
    public Expression parseInitializerClause() {
        return state.languageMode() == LanguageMode.CPP17_ALGORITHM && state.check(TokenType.LEFT_BRACE)
                ? parseConstructionInitializer() : parseAssignment();
    }

    private Expression parseAssignment() {
        Expression expression = parseConditional();
        if (matchAssignmentOperator()) {
            Token operatorToken = state.previous();
            Expression value = parseInitializerClause();
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
        if (state.languageMode() == LanguageMode.CPP17_ALGORITHM
                && (state.check(TokenType.NEW) || state.check(TokenType.SCOPE) && state.peekAt(1).type() == TokenType.NEW)) {
            return parsePlacementNew();
        }
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

    private Expression parsePlacementNew() {
        Token start = state.advance();
        boolean global = start.type() == TokenType.SCOPE;
        if (global) state.consume(TokenType.NEW, "期望 new");
        if (!state.match(TokenType.LEFT_PAREN)) {
            state.report(state.peek(), "当前 new 语法需要显式 placement 参数；分配式 new 尚未实现");
            return null;
        }
        var placement = new ArrayList<Expression>();
        do {
            Expression argument = parseAssignment();
            if (argument == null) return null;
            placement.add(argument);
        } while (state.match(TokenType.COMMA));
        if (state.consume(TokenType.RIGHT_PAREN, "placement 参数后期望 ')' ") == null) return null;
        Parser.ParsedType type = typeReader.parseCppTypeWithoutFunctionSuffix("placement new 后期望对象类型");
        if (type == null) return null;
        if (state.check(TokenType.LEFT_BRACKET)) {
            state.report(state.peek(), "数组 new 的语法及生命周期尚未实现");
            return null;
        }
        var initializer = state.check(TokenType.LEFT_PAREN) || state.check(TokenType.LEFT_BRACE)
                ? parseConstructionInitializer()
                : new minic.compiler.parser.node.CppInitializer(minic.compiler.parser.node.CppInitializer.Kind.DEFAULT,
                        java.util.List.of(), type.range());
        if (initializer == null) return null;
        var expression = new minic.compiler.parser.node.CppNewExpr(type.type(), placement, initializer, global,
                type.range(), SourceRange.span(start.range(), initializer.range()));
        state.build(expression, "CppNewExpr", expression.range());
        return expression;
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
            if(typeReader.isCpp()&&typeReader.beginsFunctionTemplateArguments(expression)) {
                var arguments=typeReader.parseFunctionTemplateArguments();
                if(arguments==null)return null;
                expression=new minic.compiler.parser.node.CppTemplateIdExpr(expression,arguments,SourceRange.span(expression.range(),state.previous().range()));continue;
            }
            if (state.match(TokenType.LEFT_BRACKET)) {
                Expression index = state.languageMode() == LanguageMode.CPP17_ALGORITHM && state.check(TokenType.LEFT_BRACE)
                        ? parseInitializerClause() : parseExpression();
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
        if (state.languageMode() == LanguageMode.CPP17_ALGORITHM) {
            if(state.match(TokenType.TEMPLATE)) {
                Token name=state.consume(TokenType.IDENTIFIER,"template 后期望成员模板名称");if(name==null)return null;
                Expression member=new FieldAccessExpr(target,name.lexeme(),viaPointer,SourceRange.span(target.range(),name.range()));
                var arguments=typeReader.parseFunctionTemplateArguments();
                return arguments==null?null:new minic.compiler.parser.node.CppTemplateIdExpr(member,arguments,SourceRange.span(target.range(),state.previous().range()));
            }
            if (state.match(TokenType.TILDE)) return finishDestructorCall(target, viaPointer, state.previous());
            if (state.check(TokenType.OPERATOR)) {
                if (typeReader.canStartTypeAt(1)) {
                    Token keyword = state.advance();
                    Parser.ParsedType type = typeReader.parseCppTypeWithoutFunctionSuffix("operator 后期望转换目标类型");
                    if (type == null) return null;
                    var name = new minic.compiler.parser.node.ConversionName(type.type(), false,
                            SourceRange.span(keyword.range(), type.range()));
                    var member = new FieldAccessExpr(target, name.spelling(), viaPointer,
                            SourceRange.span(target.range(), name.range()));
                    state.build(member, "FieldAccessExpr " + member.fieldName(), member.range());
                    return member;
                }
                var operator = minic.compiler.parser.CppOperatorNameParser.parse(state);
                if (operator == null) return null;
                var member = new FieldAccessExpr(target, operator.spelling(), viaPointer,
                        SourceRange.span(target.range(), operator.range()));
                state.build(member, "FieldAccessExpr " + member.fieldName(), member.range());
                return member;
            }
            int offset = state.check(TokenType.SCOPE) ? 1 : 0;
            while (state.peekAt(offset).type() == TokenType.IDENTIFIER
                    && state.peekAt(offset + 1).type() == TokenType.SCOPE) {
                offset += 2;
                if (state.peekAt(offset).type() == TokenType.TILDE) {
                    state.unsupportedCpp(SourceRange.span(state.peek().range(), state.peekAt(offset).range()),
                            "显式析构调用的限定类型前缀尚未实现");
                    return null;
                }
            }
        }
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

    private Expression finishDestructorCall(Expression receiver, boolean viaPointer, Token tilde) {
        Token name = state.consume(TokenType.IDENTIFIER, "析构调用的 ~ 后期望类型名称");
        if (name == null) return null;
        var destructorName = new minic.compiler.parser.node.QualifiedName(false, java.util.List.of(name.lexeme()), name.range());
        MiniType ownerType = typeReader.resolveTypedef(name.lexeme());
        if (state.consume(TokenType.LEFT_PAREN, "显式析构调用期望 '('") == null) return null;
        Token end = state.consume(TokenType.RIGHT_PAREN, "显式析构调用不接受参数，期望 ')'");
        if (end == null) return null;
        var call = new minic.compiler.parser.node.CppDestructorCallExpr(receiver, ownerType, destructorName,
                viaPointer, SourceRange.span(tilde.range(), name.range()), SourceRange.span(receiver.range(), end.range()));
        state.build(call, "CppDestructorCallExpr", call.range());
        return call;
    }

    private Expression parseLambda() {
        Token start=state.advance();
        var defaultCapture=minic.compiler.parser.node.CppLambdaExpr.CaptureDefault.NONE;
        var captures=new ArrayList<minic.compiler.parser.node.CppLambdaExpr.Capture>();
        if(state.match(TokenType.EQUAL))defaultCapture=minic.compiler.parser.node.CppLambdaExpr.CaptureDefault.COPY;
        else if(state.check(TokenType.AMPERSAND)&&(state.peekAt(1).type()==TokenType.COMMA||state.peekAt(1).type()==TokenType.RIGHT_BRACKET)) {
            state.advance();defaultCapture=minic.compiler.parser.node.CppLambdaExpr.CaptureDefault.REFERENCE;
        }
        if(defaultCapture!=minic.compiler.parser.node.CppLambdaExpr.CaptureDefault.NONE && !state.check(TokenType.RIGHT_BRACKET))
            state.consume(TokenType.COMMA,"默认捕获后期望 ','");
        var statements=new StatementManager(state,this,typeReader);
        while(!state.check(TokenType.RIGHT_BRACKET)&&!state.isAtEnd()) {
            Token first=state.peek();
            var kind=state.match(TokenType.AMPERSAND)?minic.compiler.parser.node.CppLambdaExpr.CaptureKind.REFERENCE
                    :minic.compiler.parser.node.CppLambdaExpr.CaptureKind.COPY;
            boolean star=state.match(TokenType.STAR);
            Token name;
            if(state.match(TokenType.THIS)) {
                name=state.previous();
                if(kind==minic.compiler.parser.node.CppLambdaExpr.CaptureKind.REFERENCE)state.report(name,"this 捕获不能加 '&'");
                kind=star?minic.compiler.parser.node.CppLambdaExpr.CaptureKind.THIS_COPY:minic.compiler.parser.node.CppLambdaExpr.CaptureKind.THIS;
            } else {
                if(star)state.report(first,"'*' 捕获只能用于 this");
                name=state.consume(TokenType.IDENTIFIER,"期望捕获名称");
            }
            if(name==null)return null;
            minic.compiler.parser.node.CppInitializer initializer=null;
            if(state.check(TokenType.EQUAL)||state.check(TokenType.LEFT_BRACE)||state.check(TokenType.LEFT_PAREN))initializer=statements.parseCppInitializer();
            SourceRange range=SourceRange.span(first.range(),initializer==null?name.range():initializer.range());
            captures.add(new minic.compiler.parser.node.CppLambdaExpr.Capture(name.lexeme(),kind,initializer,range));
            if(!state.match(TokenType.COMMA))break;
            if(state.check(TokenType.RIGHT_BRACKET))state.report(state.peek(),"捕获列表不能以 ',' 结尾");
        }
        if(state.consume(TokenType.RIGHT_BRACKET,"lambda 捕获列表期望 ']'")==null)return null;
        typeReader.enterScope(captures.stream().map(minic.compiler.parser.node.CppLambdaExpr.Capture::name).filter(name->!name.equals("this")).toList());
        try {
            var parameters=new ArrayList<minic.compiler.parser.node.Declaration.Parameter>();
            boolean variadic=false;
            boolean parameterClause=state.match(TokenType.LEFT_PAREN);
            if(parameterClause) {
                var parsed=typeReader.parseParameterList();variadic=parsed.variadic();
                for(var parameter:parsed.parameters())parameters.add(new minic.compiler.parser.node.Declaration.Parameter(parameter.name(),parameter.type(),parameter.defaultValue(),parameter.range()));
                if(state.consume(TokenType.RIGHT_PAREN,"lambda 形参期望 ')'")==null)return null;
            }
            boolean mutable=state.match(TokenType.MUTABLE);
            if(mutable&&!parameterClause)state.report(state.previous(),"C++17 mutable lambda 需要形参括号");
            if(state.check(TokenType.CONSTEXPR)||state.check(TokenType.NOEXCEPT)) {
                state.unsupportedCpp(state.peek().range(),"lambda constexpr/noexcept 说明符尚未接入");return null;
            }
            for(var parameter:parameters)if(!parameter.name().isEmpty())typeReader.declareOrdinaryName(parameter.name(),parameter.range());
            MiniType returnType=MiniType.AUTO;
            if(state.match(TokenType.ARROW)) {
                if(!parameterClause)state.report(state.previous(),"C++17 lambda 尾置返回类型需要形参括号");
                var parsed=typeReader.parseType("lambda 尾置返回类型");if(parsed==null)return null;returnType=parsed.type();
            }
            var body=statements.parseFunctionBlock(parameters.stream().map(minic.compiler.parser.node.Declaration.Parameter::name).toList());
            if(body==null)return null;
            var lambda=new minic.compiler.parser.node.CppLambdaExpr(defaultCapture,captures,parameters,variadic,mutable,returnType,body,SourceRange.span(start.range(),body.range()));
            state.build(lambda,"CppLambdaExpr",lambda.range());return lambda;
        } finally {typeReader.exitScope();}
    }

    private Expression parseTypeQuery(minic.compiler.parser.node.CppTypeQueryExpr.Kind kind) {
        Token name = state.advance();
        if (state.consume(TokenType.LEFT_PAREN, "类型查询后期望 '('") == null) return null;
        var arguments = new ArrayList<minic.compiler.parser.node.CppTypeQueryExpr.TypeArgument>();
        if (!state.check(TokenType.RIGHT_PAREN)) {
            do {
                Parser.ParsedType type = typeReader.parseType("类型查询期望类型实参");
                if (type == null) return null;
                boolean expansion = state.match(TokenType.ELLIPSIS);
                arguments.add(new minic.compiler.parser.node.CppTypeQueryExpr.TypeArgument(type.type(), expansion,
                        expansion ? SourceRange.span(type.range(), state.previous().range()) : type.range()));
            } while (state.match(TokenType.COMMA));
        }
        Token close = state.consume(TokenType.RIGHT_PAREN, "类型查询后期望 ')'");
        if (close == null) return null;
        if (arguments.stream().noneMatch(minic.compiler.parser.node.CppTypeQueryExpr.TypeArgument::packExpansion) && !kind.acceptsArity(arguments.size())) {
            state.report(name.range(), kind.spelling() + (kind == minic.compiler.parser.node.CppTypeQueryExpr.Kind.CONSTRUCTIBLE
                    ? " 至少需要一个类型实参" : " 需要两个类型实参"));
            return null;
        }
        var result = new minic.compiler.parser.node.CppTypeQueryExpr(kind, arguments, name.range(),
                SourceRange.span(name.range(), close.range()));
        state.build(result, "CppTypeQueryExpr " + kind.spelling(), result.range());
        return result;
    }

    private Expression parsePrimary() {
        if(typeReader.isCpp() && state.check(TokenType.LEFT_BRACKET))return parseLambda();
        if (state.languageMode() == LanguageMode.CPP17_ALGORITHM && state.check(TokenType.IDENTIFIER)) {
            var query = minic.compiler.parser.node.CppTypeQueryExpr.Kind.fromSpelling(state.peek().lexeme());
            if (query != null) return parseTypeQuery(query);
        }
        if (typeReader.cppTypeMemberDelimiterAt(0) >= 0) {
            Parser.ParsedType type = typeReader.parseCppConstructionType();
            state.consume(TokenType.SCOPE, "期望 '::'");
            Token name = state.consume(TokenType.IDENTIFIER, "期望类成员名称");
            if (type == null || name == null) return null;
            var member = new minic.compiler.parser.node.CppTypeMemberExpr(type.type(), name.lexeme(), name.range(),
                    SourceRange.span(type.range(), name.range()));
            state.build(member, "CppTypeMemberExpr", member.range());
            return member;
        }
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
            var templateValue = typeReader.templateValueReference(nameToken);
            if (templateValue != null) {
                state.build(templateValue, "TemplateValue", templateValue.range());
                return templateValue;
            }
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
                Expression argument = parseInitializerClause();
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
