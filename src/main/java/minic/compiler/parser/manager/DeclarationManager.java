package minic.compiler.parser.manager;

import minic.compiler.parser.Parser;

import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Parameter;
import minic.compiler.parser.node.Declaration.StructDecl;
import minic.compiler.parser.node.Declaration.StructField;
import minic.compiler.parser.node.Declaration.EnumDecl;
import minic.compiler.parser.node.Declaration.Enumerator;
import minic.compiler.parser.node.Declaration.TypedefDecl;
import minic.compiler.parser.node.Declaration.GlobalVarDecl;
import minic.compiler.parser.node.Declaration;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.type.MiniType;
import minic.SourceRange;

import java.util.ArrayList;

public final class DeclarationManager {
    private final Parser.Context state;
    private final StatementManager statementManager;
    private final ExpressionManager expressionManager;
    private final Parser.TypeReader typeReader;
    private final java.util.Map<String, Long> enumConstants;
    private final minic.compiler.parser.CppRecordParser cppRecordParser;

    public DeclarationManager(Parser.Context state, StatementManager statementManager,
                              ExpressionManager expressionManager, Parser.TypeReader typeReader) {
        this(state, statementManager, expressionManager, typeReader, new java.util.LinkedHashMap<>());
    }

    public DeclarationManager(Parser.Context state, StatementManager statementManager,
                              ExpressionManager expressionManager, Parser.TypeReader typeReader,
                              java.util.Map<String, Long> enumConstants) {
        this.state = state;
        this.statementManager = statementManager;
        this.expressionManager = expressionManager;
        this.typeReader = typeReader;
        this.enumConstants = enumConstants;
        cppRecordParser = typeReader.isCpp()
                ? new minic.compiler.parser.CppRecordParser(state, typeReader, statementManager) : null;
        if (cppRecordParser != null) typeReader.setCppRecordParser(cppRecordParser);
    }

    public EnumDecl parseEnumDecl() {
        Token start = state.consume(TokenType.ENUM, "期望 enum");
        Token name = state.consume(TokenType.IDENTIFIER, "期望枚举名称");
        state.consume(TokenType.LEFT_BRACE, "期望 '{'");
        ArrayList<Enumerator> values = new ArrayList<>();
        long next = 0;
        while (!state.check(TokenType.RIGHT_BRACE) && !state.isAtEnd()) {
            Token item = state.consume(TokenType.IDENTIFIER, "期望枚举常量名称");
            if (item == null) break;
            long value = next;
            if (state.match(TokenType.EQUAL)) {
                Expression expression = expressionManager.parseAssignmentExpression();
                try { value = enumConstant(expression); }
                catch (IllegalArgumentException error) { state.report(item, "枚举值必须是整数常量表达式"); }
            }
            if (enumConstants.putIfAbsent(item.lexeme(), value) != null) state.report(item, "重复枚举常量");
            values.add(new Enumerator(item.lexeme(), value, item.range()));
            next = value + 1;
            if (!state.match(TokenType.COMMA)) break;
            if (state.check(TokenType.RIGHT_BRACE)) break;
        }
        Token close = state.consume(TokenType.RIGHT_BRACE, "期望 '}'");
        Token semicolon = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (start == null || name == null || close == null || semicolon == null) return null;
        return new EnumDecl(name.lexeme(), values, SourceRange.span(start.range(), semicolon.range()));
    }

    private long enumConstant(Expression expression) {
        return switch (expression) {
            case minic.compiler.parser.node.Expression.IntegerLiteralExpr value -> value.value();
            case minic.compiler.parser.node.Expression.IntegerConstantExpr value -> value.value();
            case minic.compiler.parser.node.Expression.LongLiteralExpr value -> value.value();
            case minic.compiler.parser.node.Expression.CharLiteralExpr value -> value.value();
            case minic.compiler.parser.node.Expression.BoolLiteralExpr value -> value.value() ? 1 : 0;
            case minic.compiler.parser.node.Expression.GroupingExpr group -> enumConstant(group.expression());
            case minic.compiler.parser.node.Expression.CastExpr cast -> enumConstant(cast.operand());
            case minic.compiler.parser.node.Expression.UnaryExpr unary -> switch (unary.operator()) {
                case PLUS -> enumConstant(unary.operand());
                case MINUS -> -enumConstant(unary.operand());
                case TILDE -> ~enumConstant(unary.operand());
                case BANG -> enumConstant(unary.operand()) == 0 ? 1 : 0;
                default -> throw new IllegalArgumentException();
            };
            case minic.compiler.parser.node.Expression.BinaryExpr binary -> enumBinary(
                    binary.operator(), enumConstant(binary.left()), enumConstant(binary.right()));
            case minic.compiler.parser.node.Expression.ConditionalExpr conditional ->
                    enumConstant(conditional.condition()) != 0
                            ? enumConstant(conditional.thenExpression())
                            : enumConstant(conditional.elseExpression());
            default -> throw new IllegalArgumentException();
        };
    }

    private long enumBinary(TokenType operator, long left, long right) {
        return switch (operator) {
            case PLUS -> left + right; case MINUS -> left - right; case STAR -> left * right;
            case SLASH -> left / right; case PERCENT -> left % right;
            case AMPERSAND -> left & right; case PIPE -> left | right; case CARET -> left ^ right;
            case LESS_LESS -> left << right; case GREATER_GREATER -> left >> right;
            case EQUAL_EQUAL -> left == right ? 1 : 0; case BANG_EQUAL -> left != right ? 1 : 0;
            case LESS -> left < right ? 1 : 0; case LESS_EQUAL -> left <= right ? 1 : 0;
            case GREATER -> left > right ? 1 : 0; case GREATER_EQUAL -> left >= right ? 1 : 0;
            case AMPERSAND_AMPERSAND -> left != 0 && right != 0 ? 1 : 0;
            case PIPE_PIPE -> left != 0 || right != 0 ? 1 : 0;
            default -> throw new IllegalArgumentException();
        };
    }

    public TypedefDecl parseTypedefDecl() {
        Token start = state.consume(TokenType.TYPEDEF, "期望 typedef");
        Parser.ParsedNamedType declaration = typeReader.parseNamedType("期望 typedef 类型", "期望 typedef 名称");
        Token semicolon = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (start == null || declaration == null || semicolon == null) {
            return null;
        }
        if (!declaration.alignmentSpecs().isEmpty()) {
            state.report(declaration.range(), "typedef 声明不能使用 alignas");
        }
        SourceRange range = SourceRange.span(start.range(), semicolon.range());
        typeReader.defineTypedef(declaration.name(), declaration.type(), range);
        TypedefDecl typedefDecl = new TypedefDecl(declaration.name(), declaration.type(), range);
        state.build(typedefDecl, "TypedefDecl " + typedefDecl.name(), typedefDecl.range());
        return typedefDecl;
    }

    public Declaration parseFunctionOrGlobalDecl() {
        if (cppRecordParser != null && cppRecordParser.startsOutOfLineConstructor())
            return cppRecordParser.parseOutOfLineConstructor();
        state.enter("functionDecl");
        Token startToken = state.peek();
        boolean external = false;
        boolean noReturn = false;
        while (state.check(TokenType.EXTERN) || state.check(TokenType.NORETURN)) {
            Token specifier = state.advance();
            if (specifier.type() == TokenType.EXTERN) {
                if (external) state.report(specifier, "extern 函数说明符重复");
                external = true;
            } else {
                if (noReturn) state.report(specifier, "noreturn 函数说明符重复");
                noReturn = true;
            }
        }
        Parser.ParsedNamedType declaration = typeReader.parseNamedType(
                "期望函数返回类型",
                "期望函数名",
                typeReader.isCpp()
        );
        if (declaration == null) {
            return null;
        }
        // In C++, a declarator hides an outer type name in its own initializer/body.
        boolean qualified = declaration.qualifiedName() != null;
        if (qualified && declaration.qualifiedName().segments().size() < 2) {
            state.unsupportedCpp(declaration.nameRange(), "此限定声明需要所属类名称");
            return null;
        }
        if (typeReader.isCpp() && !qualified) typeReader.declareOrdinaryName(declaration.name(), declaration.range());
        if (!(declaration.type() instanceof MiniType.FunctionType functionType)) {
            if (qualified) {
                state.unsupportedCpp(declaration.nameRange(), "类外限定数据成员声明尚未实现");
                return null;
            }
            if (noReturn) state.report(startToken, "noreturn 只能用于函数");
            var initialization = statementManager.parseVariableInitializer(declaration.type(), declaration.range());
            Token semicolon = state.consume(TokenType.SEMICOLON, "期望 ';'");
            if (semicolon == null) return null;
            GlobalVarDecl global = new GlobalVarDecl(
                    declaration.name(), declaration.type(), initialization.expression(), external,
                    declaration.alignmentSpecs(), initialization.cppInitializer(), SourceRange.span(startToken.range(), semicolon.range()));
            if (!typeReader.isCpp()) typeReader.declareOrdinaryName(global.name(), global.range());
            state.build(global, "GlobalVarDecl " + global.name(), global.range());
            state.exit("functionDecl", global.range());
            return global;
        }
        if (!declaration.alignmentSpecs().isEmpty()) {
            state.report(declaration.range(), "函数声明不能使用 alignas");
        }
        boolean constQualified = qualified && state.match(TokenType.CONST);
        if (qualified && external) state.unsupportedCpp(startToken.range(), "类外成员定义不能使用 extern");
        Token semicolonToken = null;
        BlockStmt body = null;
        if (state.match(TokenType.SEMICOLON)) {
            semicolonToken = state.previous();
        } else {
            if (!state.check(TokenType.LEFT_BRACE)) {
                state.report(state.peek(), "期望 '{'");
                state.synchronizeMissingFunctionBody();
                return null;
            }
            if (qualified) typeReader.enterMemberDefinitionScope(declaration.qualifiedName());
            try {
                body = statementManager.parseFunctionBlock(declaration.parameters().stream()
                        .map(Parser.ParsedParameter::name)
                        .filter(name -> !name.isEmpty())
                        .toList());
            } finally {
                if (qualified) typeReader.exitMemberDefinitionScope();
            }
        }

        if (body == null && semicolonToken == null) {
            return null;
        }
        ArrayList<Parameter> parameters = new ArrayList<>();
        for (int index = 0; index < declaration.parameters().size(); index++) {
            Parser.ParsedParameter parameter = declaration.parameters().get(index);
            if (body != null && parameter.name().isEmpty()) {
                state.report(parameter.range(), "函数定义中的参数必须命名");
                return null;
            }
            String name = parameter.name().isEmpty() ? "__unnamed" + index : parameter.name();
            parameters.add(new Parameter(name, parameter.type(), parameter.range()));
        }
        SourceRange endRange = body != null ? body.range() : semicolonToken.range();
        FunctionDecl functionDecl = new FunctionDecl(
                declaration.name(),
                functionType.returnType(),
                parameters,
                functionType.variadic(),
                body,
                external,
                noReturn,
                SourceRange.span(startToken.range(), endRange)
        );
        if (!typeReader.isCpp()) typeReader.declareOrdinaryName(functionDecl.name(), functionDecl.range());
        state.build(functionDecl, "FunctionDecl " + functionDecl.name(), functionDecl.range());
        state.exit("functionDecl", functionDecl.range());
        if (qualified) {
            return new Declaration.OutOfLineMethodDecl(declaration.qualifiedName(), functionDecl,
                    constQualified, declaration.nameRange());
        }
        return functionDecl;
    }

    /** 兼容旧调用方；新代码应使用 parseFunctionOrGlobalDecl。 */
    public FunctionDecl parseFunctionDecl() {
        Declaration declaration = parseFunctionOrGlobalDecl();
        return declaration instanceof FunctionDecl function ? function : null;
    }

    public StructDecl parseStructDecl() {
        if (cppRecordParser != null) return cppRecordParser.parseDeclaration();
        state.enter("structDecl");
        boolean union = state.check(TokenType.UNION);
        Token startToken = state.advance();
        Token nameToken = state.consume(TokenType.IDENTIFIER, "期望结构体名");
        String internalName = nameToken == null ? "" : union ? unionName(nameToken.lexeme()) : nameToken.lexeme();
        if (typeReader.isCpp() && nameToken != null) {
            // Register before parsing fields so injected names and self pointers are visible.
            MiniType aggregate = typeReader.declareAggregate(nameToken.lexeme(), union,
                    state.check(TokenType.LEFT_BRACE), nameToken.range());
            if (aggregate != null && aggregate.unqualified() instanceof MiniType.StructType struct) {
                internalName = struct.name();
            }
        }
        if (state.match(TokenType.SEMICOLON)) {
            Token end = state.previous();
            if (startToken == null || nameToken == null) return null;
            StructDecl forward = new StructDecl(internalName, java.util.List.of(), false, union,
                    SourceRange.span(startToken.range(), end.range()));
            state.build(forward, "StructForwardDecl " + forward.name(), forward.range());
            state.exit("structDecl", forward.range());
            return forward;
        }
        state.consume(TokenType.LEFT_BRACE, "期望 '{'");
        ArrayList<StructField> fields = new ArrayList<>();
        if (typeReader.isCpp()) typeReader.enterMemberScope(MiniType.struct(internalName));
        try {
            while (!state.check(TokenType.RIGHT_BRACE) && !state.isAtEnd()) {
                StructField field = parseStructField();
                if (field != null) {
                    fields.add(field);
                    typeReader.declareMemberField(field);
                } else {
                    state.synchronizeStatement();
                }
            }
        } finally {
            if (typeReader.isCpp()) typeReader.exitMemberScope();
        }
        state.consume(TokenType.RIGHT_BRACE, "期望 '}'");
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (startToken == null || nameToken == null || semicolonToken == null) {
            return null;
        }
        StructDecl structDecl = new StructDecl(
                internalName,
                fields,
                true,
                union,
                SourceRange.span(startToken.range(), semicolonToken.range())
        );
        typeReader.recordAggregateFields(structDecl);
        state.build(structDecl, "StructDecl " + structDecl.name(), structDecl.range());
        state.exit("structDecl", structDecl.range());
        return structDecl;
    }

    private static String unionName(String sourceName) {
        return "$union$" + sourceName;
    }

    private StructField parseStructField() {
        if ((state.check(TokenType.STRUCT) || state.check(TokenType.UNION))
                && state.peekAt(1).type() == TokenType.LEFT_BRACE) {
            Parser.ParsedType anonymousType = typeReader.parseType("期望匿名聚合类型");
            Token semicolon = state.consume(TokenType.SEMICOLON, "期望 ';'");
            if (anonymousType == null || semicolon == null) return null;
            return new StructField("", anonymousType.type(), true, java.util.List.of(),
                    SourceRange.span(anonymousType.range(), semicolon.range()));
        }
        Parser.ParsedNamedType declaration = typeReader.parseNamedType("期望字段类型", "期望字段名");
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (declaration == null || semicolonToken == null) {
            return null;
        }
        return new StructField(
                declaration.name(),
                declaration.type(),
                declaration.alignmentSpecs(),
                SourceRange.span(declaration.range(), semicolonToken.range())
        );
    }
}
