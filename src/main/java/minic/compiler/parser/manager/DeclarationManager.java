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
import minic.compiler.parser.node.Declaration.StaticAssertDecl;

import java.util.ArrayList;

public final class DeclarationManager {
    private final Parser.Context state;
    private final StatementManager statementManager;
    private final ExpressionManager expressionManager;
    private final Parser.TypeReader typeReader;
    private final java.util.Map<String, Long> enumConstants;
    private final minic.compiler.parser.RecordParser recordParser;

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
        recordParser = new minic.compiler.parser.RecordParser(state, typeReader, statementManager);
        typeReader.setRecordParser(recordParser);
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

    public Declaration parseTypedefDecl() {
        Token start=state.consume(TokenType.TYPEDEF,"期望 typedef");
        var specifiers=typeReader.parseDeclarationSpecifiers("期望 typedef 类型");
        if(start==null||specifiers==null)return null;
        var declarations=new ArrayList<Declaration>();boolean first=true;
        do {
            var declaration=typeReader.parseNamedDeclarator(specifiers,"期望 typedef 名称",first);
            if(declaration==null)return null;
            if(!declaration.alignmentSpecs().isEmpty())state.report(declaration.range(),"typedef 声明不能使用 alignas");
            SourceRange end=declaration.range();
            if(!state.check(TokenType.COMMA)){
                Token semicolon=state.consume(TokenType.SEMICOLON,"期望 ';'");if(semicolon==null)return null;end=semicolon.range();
            }
            SourceRange range=SourceRange.span(first?start.range():declaration.range(),end);
            typeReader.defineTypedef(declaration.name(),declaration.type(),range);
            var typedef=new TypedefDecl(declaration.name(),declaration.type(),range);
            declarations.add(typedef);state.build(typedef,"TypedefDecl "+typedef.name(),range);first=false;
            if(state.previous().type()==TokenType.SEMICOLON)break;
        }while(state.match(TokenType.COMMA));
        return declarationGroup(declarations,start.range());
    }

    private Declaration declarationGroup(java.util.List<Declaration> declarations,SourceRange start){
        if(declarations.size()==1)return declarations.getFirst();
        var group=new Declaration.DeclGroupDecl(declarations,SourceRange.span(start,declarations.getLast().range()));
        state.build(group,"DeclGroupDecl",group.range());return group;
    }

    public minic.compiler.parser.node.Declaration.StaticAssertDecl parseStaticAssert(){return statementManager.parseStaticAssert();}
    private Declaration constexprDeclaration(Declaration declaration,boolean enabled){
        if(!enabled||declaration==null)return declaration;
        if(declaration instanceof Declaration.OutOfLineConstructorDecl ctor)return new Declaration.OutOfLineConstructorDecl(ctor.qualifiedName(),ctor.constructor().withConstexprSpecifier(true),ctor.nameRange());
        if(declaration instanceof Declaration.OutOfLineMethodDecl method)return new Declaration.OutOfLineMethodDecl(method.qualifiedName(),method.method().withConstexprSpecifier(true),method.constQualified(),method.nameRange());
        state.report(declaration.range(),"A destructor cannot be constexpr");return declaration;
    }
    public Declaration parseFunctionOrGlobalDecl() {
        Token firstSpecifier=state.peek();boolean constexpr=false;
        while((state.check(TokenType.CONSTEXPR)||state.check(TokenType.INLINE))){
            Token specifier=state.advance();if(specifier.type()==TokenType.CONSTEXPR){if(constexpr)state.report(specifier,"Repeated constexpr specifier");constexpr=true;}
        }
        if (state.match(TokenType.EXPLICIT))
            state.report(state.previous().range(), "explicit 只能用于类内构造函数或转换函数声明");
        if(recordParser!=null && recordParser.startsTemplateSpecialMember())
            return constexprDeclaration(recordParser.parseTemplateSpecialMember(),constexpr);
        if (recordParser != null && recordParser.startsOutOfLineConversion())
            return constexprDeclaration(recordParser.parseOutOfLineConversion(),constexpr);
        if (recordParser != null && recordParser.startsOutOfLineDestructor())
            return constexprDeclaration(recordParser.parseOutOfLineDestructor(),constexpr);
        if (recordParser != null && recordParser.startsOutOfLineConstructor())
            return constexprDeclaration(recordParser.parseOutOfLineConstructor(),constexpr);
        state.enter("functionDecl");
        Token startToken = firstSpecifier;
        boolean external = false;
        boolean noReturn = false;
        boolean internal = false;
        while (state.check(TokenType.EXTERN) || state.check(TokenType.NORETURN)
                || (state.check(TokenType.STATIC)||state.check(TokenType.CONSTEXPR)||state.check(TokenType.INLINE))) {
            Token specifier = state.advance();
            if(specifier.type()==TokenType.CONSTEXPR){if(constexpr)state.report(specifier,"Repeated constexpr specifier");constexpr=true;continue;}
            if(specifier.type()==TokenType.INLINE)continue;
            if (specifier.type() == TokenType.EXTERN) {
                if (external) state.report(specifier, "extern 函数说明符重复");
                external = true;
            } else if (specifier.type() == TokenType.STATIC) {
                if (internal) state.report(specifier, "static 说明符重复");
                internal = true;
            } else {
                if (noReturn) state.report(specifier, "noreturn 函数说明符重复");
                noReturn = true;
            }
        }
        if (internal && external) state.report(startToken, "static 与 extern 不能用于同一声明");
        if(typeReader.startsStructuredBinding()) {
            if(external||internal||noReturn||constexpr)state.report(startToken,"Structured bindings cannot have storage or function specifiers");
            var binding=statementManager.parseStructuredBinding(false);
            if(binding!=null)state.exit("functionDecl",binding.range());
            return binding;
        }
        var specifiers=typeReader.parseDeclarationSpecifiers("期望函数返回类型");
        if(specifiers==null)return null;
        var declarations=new ArrayList<Declaration>();boolean first=true;
        do {
            Token declaratorStart=first?startToken:state.peek();
            var declaration=typeReader.parseGlobalDeclarator(specifiers,"期望函数或变量名称",first);
            if(declaration==null)return null;
            if(!first)state.enter("functionDecl");
            Declaration parsed=finishFunctionOrGlobalDecl(declaration,declaratorStart,external,noReturn,internal,constexpr);
            if(parsed==null)return null;
            declarations.add(parsed);first=false;
            if(state.previous().type()==TokenType.SEMICOLON)break;
        }while(state.match(TokenType.COMMA));
        if(declarations.size()>1)for(Declaration item:declarations){
            Declaration unwrapped=item instanceof Declaration.InternalLinkageDecl linkage?linkage.declaration():item;
            FunctionDecl function=unwrapped instanceof FunctionDecl f?f:unwrapped instanceof Declaration.OutOfLineMethodDecl method?method.method():null;
            if(function!=null&&function.hasDefinition())state.report(function.range(),"A function definition cannot share a declaration with another declarator");
        }
        return declarationGroup(declarations,startToken.range());
    }

    private Declaration finishFunctionOrGlobalDecl(Parser.ParsedNamedType declaration,Token startToken,
                                                   boolean external,boolean noReturn,boolean internal,boolean constexpr){
        // A declarator hides an outer type name in its own initializer/body.
        boolean qualified = declaration.qualifiedName() != null;
        if (qualified && internal) state.report(startToken, "类外成员定义不能重复 static 说明符");
        if (qualified && declaration.qualifiedName().segments().size() < 2) {
            state.unsupportedSyntax(declaration.nameRange(), "此限定声明需要所属类名称");
            return null;
        }
        if (!qualified) typeReader.declareOrdinaryName(declaration.name(), declaration.range());
        if (!(declaration.type() instanceof MiniType.FunctionType functionType)) {
            if (noReturn) state.report(startToken, "noreturn 只能用于函数");
            StatementManager.ParsedInitializer initialization;
            if (qualified) typeReader.enterMemberDefinitionScope(declaration.qualifiedName());
            try {
                initialization = statementManager.parseVariableInitializer(declaration.type(), declaration.range());
            } finally {
                if (qualified) typeReader.exitMemberDefinitionScope();
            }
            SourceRange end=initialization.expression()!=null?initialization.expression().range():declaration.range();
            if(!state.check(TokenType.COMMA)){
                Token semicolon=state.consume(TokenType.SEMICOLON,"期望 ';'");if(semicolon==null)return null;end=semicolon.range();
            }
            GlobalVarDecl global = new GlobalVarDecl(
                    declaration.name(), declaration.type(), initialization.expression(), external,
                    declaration.alignmentSpecs(), initialization.initializerSyntax(), SourceRange.span(startToken.range(), end)).withConstexprSpecifier(constexpr);
            state.build(global, "GlobalVarDecl " + global.name(), global.range());
            state.exit("functionDecl", global.range());
            if (qualified) {
                if (external) state.report(startToken, "类外 static 数据成员定义不能使用 extern");
                return new Declaration.OutOfLineStaticFieldDecl(declaration.qualifiedName(), global, declaration.nameRange());
            }
            return internal ? new Declaration.InternalLinkageDecl(global, global.range()) : global;
        }
        if (!declaration.alignmentSpecs().isEmpty()) {
            state.report(declaration.range(), "函数声明不能使用 alignas");
        }
        typeReader.registerPendingFunctionTemplate(declaration.name());
        boolean constQualified;
        if(qualified)typeReader.enterMemberDefinitionScope(declaration.qualifiedName());
        try {
            constQualified = qualified && state.match(TokenType.CONST);
            functionType = typeReader.parseTrailingReturn(typeReader.parseFunctionException(functionType));
        } finally {if(qualified)typeReader.exitMemberDefinitionScope();}
        if (qualified && external) state.unsupportedSyntax(startToken.range(), "类外成员定义不能使用 extern");
        Token semicolonToken = null;
        Declaration.DefinitionKind definitionKind=Declaration.DefinitionKind.ORDINARY;
        BlockStmt body = null;
        if(state.check(TokenType.EQUAL)) {
            definitionKind=minic.compiler.parser.FunctionDefinitionParser.parse(state);semicolonToken=state.previous();
        } else if (state.match(TokenType.SEMICOLON)) {
            semicolonToken = state.previous();
        } else if(state.check(TokenType.COMMA)) {
            semicolonToken=state.previous();
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
            String name = parameter.name().isEmpty() ? "__unnamed" + index : parameter.name();
            parameters.add(new Parameter(name, parameter.type(), parameter.defaultValue(), parameter.range()));
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
                SourceRange.span(startToken.range(), endRange),
                declaration.operatorName()
        ).withDefinitionKind(definitionKind).withExceptionSpecification(functionType.exceptionSpecification()).withConstexprSpecifier(constexpr);
        state.build(functionDecl, "FunctionDecl " + functionDecl.name(), functionDecl.range());
        state.exit("functionDecl", functionDecl.range());
        if (qualified) {
            return new Declaration.OutOfLineMethodDecl(declaration.qualifiedName(), functionDecl,
                    constQualified, declaration.nameRange());
        }
        return internal ? new Declaration.InternalLinkageDecl(functionDecl, functionDecl.range()) : functionDecl;
    }

    /** 兼容旧调用方；新代码应使用 parseFunctionOrGlobalDecl。 */
    public FunctionDecl parseFunctionDecl() {
        Declaration declaration = parseFunctionOrGlobalDecl();
        return declaration instanceof FunctionDecl function ? function : null;
    }

    public StructDecl parseStructDecl() {
        if (recordParser != null) return recordParser.parseDeclaration();
        state.enter("structDecl");
        boolean union = state.check(TokenType.UNION);
        Token startToken = state.advance();
        Token nameToken = state.consume(TokenType.IDENTIFIER, "期望结构体名");
        String internalName = nameToken == null ? "" : union ? unionName(nameToken.lexeme()) : nameToken.lexeme();
        if (nameToken != null) {
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
        typeReader.enterMemberScope(MiniType.struct(internalName));
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
            typeReader.exitMemberScope();
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
