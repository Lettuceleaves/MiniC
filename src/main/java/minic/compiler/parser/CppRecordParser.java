package minic.compiler.parser;

import minic.compiler.parser.node.Declaration;
import minic.SourceRange;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.manager.StatementManager;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.CppInitializer;
import minic.compiler.parser.node.ConversionName;
import minic.compiler.parser.node.QualifiedName;
import minic.compiler.parser.node.ClassTemplateDecl;
import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.type.MiniType;

import java.util.ArrayList;
import java.util.List;

/** C++ record syntax only; member binding and executable lowering remain separate stages. */
public final class CppRecordParser {
    private final Parser.Context state;
    private final Parser.TypeReader types;
    private final StatementManager statements;

    public CppRecordParser(Parser.Context state, Parser.TypeReader types, StatementManager statements) {
        this.state = state;
        this.types = types;
        this.statements = statements;
    }

    public StructDecl parseDeclaration() {
        Token key = state.advance();
        boolean union = key.type() == TokenType.UNION;
        Token name = state.consume(TokenType.IDENTIFIER, "期望类或结构体名称");
        if (name == null) return null;
        if(state.check(TokenType.LESS) && types.parseSpecializedRecordName(name)==null)return null;
        List<CppBase> bases=new ArrayList<>();
        if (state.match(TokenType.COLON)) do {
            Token start=state.peek();Access access=key.type()==TokenType.CLASS?Access.PRIVATE:Access.PUBLIC;
            boolean virtualBase=state.match(TokenType.VIRTUAL);
            if(state.match(TokenType.PUBLIC))access=Access.PUBLIC;
            else if(state.match(TokenType.PROTECTED))access=Access.PROTECTED;
            else if(state.match(TokenType.PRIVATE))access=Access.PRIVATE;
            virtualBase=state.match(TokenType.VIRTUAL)||virtualBase;
            var base=types.parseType("期望基类类型");if(base==null)return null;
            bases.add(new CppBase(base.type(),access,virtualBase,SourceRange.span(start.range(),state.previous().range())));
        } while(state.match(TokenType.COMMA));
        MiniType type = types.declareAggregate(name.lexeme(), union, state.check(TokenType.LEFT_BRACE), name.range());
        String identity = ((MiniType.StructType) type.unqualified()).name();
        if (state.match(TokenType.SEMICOLON)) {
            if(!bases.isEmpty())state.report(name.range(),"基类列表需要类定义");
            var forward = new StructDecl(identity, List.of(), false, union, metadata(key, union, List.of()),
                    SourceRange.span(key.range(), state.previous().range()));
            state.build(forward, "CppRecordForward " + identity, forward.range());
            return forward;
        }
        if (state.consume(TokenType.LEFT_BRACE, "期望 '{'") == null) return null;
        StructDecl definition = parseDefinition(type, union, key, bases);
        Token semicolon = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (definition == null || semicolon == null) return null;
        if(union&&!bases.isEmpty())state.report(name.range(),"union 不能拥有基类");
        CppRecordInfo info=definition.cppInfo();
        if(info!=null)info=new CppRecordInfo(info.key(),info.members(),bases,info.keyRange());
        var result = new StructDecl(identity, definition.fields(), true, union, info,
                SourceRange.span(key.range(), semicolon.range()));
        types.recordAggregateFields(result);
        state.build(result, "CppRecord " + identity, result.range());
        return result;
    }

    /** Opening brace was consumed by the surrounding declaration/type parser. */
    public StructDecl parseDefinition(MiniType type, boolean union, Token key) {
        return parseDefinition(type,union,key,List.of());
    }

    private StructDecl parseDefinition(MiniType type,boolean union,Token key,List<CppBase> bases) {
        String identity = ((MiniType.StructType) type.unqualified()).name();
        String simpleName = identity.substring(identity.lastIndexOf("::") + 2);
        List<StructField> fields = new ArrayList<>();
        List<CppMember> members = new ArrayList<>();
        List<DeferredMethod> deferred = new ArrayList<>();
        List<DeferredConstructor> constructors = new ArrayList<>();
        List<DeferredDestructor> destructors = new ArrayList<>();
        List<DeferredField> defaults = new ArrayList<>();
        List<DeferredMemberTemplate> templates = new ArrayList<>();
        types.enterMemberScope(type);
        try {
            types.inheritMemberNames(bases);
            while (!state.check(TokenType.RIGHT_BRACE) && !state.isAtEnd()) {
                if (state.match(TokenType.SEMICOLON)) continue;
                if(state.check(TokenType.STATIC_ASSERT)){var assertion=statements.parseStaticAssert();if(assertion!=null)members.add(assertion);continue;}
                if(state.check(TokenType.TEMPLATE)) {
                    DeferredMemberTemplate item=readMemberTemplate(simpleName,members.size());
                    if(item==null)recoverMember();
                    else {
                        templates.add(item);
                        if(item.constructor()!=null)members.add(new TemplateConstructorMember(item.parameters(),item.constructor().signature()));
                        else {
                            types.registerFunctionTemplate(item.method().name(),item.parameters());
                            types.declareOrdinaryName(item.method().name(),item.nameRange());
                            members.add(new TemplateMethodMember(item.parameters(),new MethodMember(item.method(),item.constant(),item.statik(),item.nameRange())));
                        }
                    }
                    continue;
                }
                int before = state.currentIndex();
                Token declarationStart = state.peek();
                boolean staticMember=false,explicitSpecifier=false,constexprSpecifier=false;
                while(state.check(TokenType.STATIC)||state.check(TokenType.EXPLICIT)||state.check(TokenType.CONSTEXPR)||state.check(TokenType.INLINE)){
                    Token specifier=state.advance();
                    if(specifier.type()==TokenType.STATIC){if(staticMember)state.report(specifier,"Repeated static");staticMember=true;}
                    else if(specifier.type()==TokenType.EXPLICIT){if(explicitSpecifier)state.report(specifier,"Repeated explicit");explicitSpecifier=true;}
                    else if(specifier.type()==TokenType.CONSTEXPR){if(constexprSpecifier)state.report(specifier,"Repeated constexpr");constexprSpecifier=true;}
                }
                Token start = state.peek();
                boolean constructorStart = start.type() == TokenType.IDENTIFIER && start.lexeme().equals(simpleName)
                        && state.peekAt(1).type() == TokenType.LEFT_PAREN;
                if (staticMember && (constructorStart || start.type() == TokenType.TILDE || start.type() == TokenType.OPERATOR)) {
                    state.report(declarationStart.range(), "构造、析构和转换函数不能声明为 static");
                    recoverMember();
                    continue;
                }
                if (explicitSpecifier && !constructorStart && start.type() != TokenType.OPERATOR) {
                    state.report(declarationStart.range(), "explicit 只能用于类内构造函数或转换函数声明");
                    recoverMember();
                    continue;
                }
                if (start.type() == TokenType.TYPEDEF) {
                    state.advance();
                    var alias=types.parseNamedType("期望成员 typedef 类型","期望成员 typedef 名称");
                    Token end=state.consume(TokenType.SEMICOLON,"期望 ';'");
                    if(alias==null||end==null)recoverMember();
                    else {
                        SourceRange range=SourceRange.span(start.range(),end.range());
                        types.defineTypedef(alias.name(),alias.type(),range);
                        members.add(new MemberTypedef(new TypedefDecl(alias.name(),alias.type(),range)));
                    }
                } else if (staticMember && isAccess(start.type())) {
                    state.report(declarationStart.range(), "static 必须修饰数据成员或成员函数");
                    recoverMember();
                } else if (isAccess(start.type())) {
                    state.advance();
                    Token colon = state.consume(TokenType.COLON, "访问说明符后期望 ':'");
                    if (union) state.unsupportedCpp(start.range(), "union 访问说明符尚未实现");
                    else if (colon != null) members.add(new AccessLabel(access(start.type()), SourceRange.span(start.range(), colon.range())));
                } else if (constructorStart) {
                    if (union) {
                        state.unsupportedCpp(start.range(), "union 构造函数尚未实现");
                        recoverMember();
                    } else {
                        state.advance();
                        DeferredConstructor constructor = readConstructor(start.lexeme(), start.range(), declarationStart.range(),
                                members.size(), explicitSpecifier);
                        constructor=constexprConstructor(constructor,constexprSpecifier);
                        if (constructor == null) recoverMember();
                        else { members.add(constructor.signature()); constructors.add(constructor); }
                    }
                } else if (start.type() == TokenType.OPERATOR) {
                    if (union) {
                        state.unsupportedCpp(start.range(), "union 转换函数尚未实现");
                        recoverMember();
                    } else {
                        ParsedConversion conversion = readConversion(declarationStart.range(), explicitSpecifier);
                        if(conversion!=null&&constexprSpecifier)conversion=new ParsedConversion(new MethodMember(conversion.member().method().withConstexprSpecifier(true),conversion.member().constQualified(),conversion.member().nameRange()),conversion.body());
                        if (conversion == null) recoverMember();
                        else {
                            int index = members.size();
                            members.add(conversion.member());
                            if (conversion.body() != null) deferred.add(new DeferredMethod(index,
                                    conversion.member().method(), conversion.body(), List.of()));
                        }
                    }
                } else if (start.type() == TokenType.STATIC && (state.peekAt(1).type() == TokenType.OPERATOR
                        || state.peekAt(1).type() == TokenType.IDENTIFIER && state.peekAt(1).lexeme().equals(simpleName))) {
                    state.report(start.range(), "构造函数和转换函数不能声明为 static");
                    recoverMember();
                } else if (start.type() == TokenType.TILDE) {
                    if(constexprSpecifier)state.report(declarationStart,"A destructor cannot be constexpr in C++17");
                    state.advance();
                    Token name = state.consume(TokenType.IDENTIFIER, "析构函数期望类名称");
                    if (union) {
                        state.unsupportedCpp(start.range(), "union 析构函数尚未实现");
                        recoverMember();
                    } else if (name == null) recoverMember();
                    else {
                        SourceRange nameRange = SourceRange.span(start.range(), name.range());
                        if (!name.lexeme().equals(simpleName)) state.report(nameRange, "析构名称必须是所属类的名称");
                        var destructor = readDestructor(name.lexeme(), nameRange, start.range(), members.size());
                        if (destructor == null) recoverMember();
                        else { members.add(destructor.signature()); destructors.add(destructor); }
                    }
                } else if (unsupportedPrefix(start)) {
                    state.unsupportedCpp(start.range(), "此类成员语法尚未实现：" + start.lexeme());
                    recoverMember();
                } else if (isAnonymousMember()) {
                    if (staticMember) {
                        state.report(declarationStart.range(), "匿名聚合成员不能声明为 static");
                        recoverMember();
                        continue;
                    }
                    var anonymous = types.parseType("期望匿名聚合类型");
                    Token end = state.consume(TokenType.SEMICOLON, "期望 ';'");
                    if (anonymous != null && end != null) {
                        addField(new StructField("", anonymous.type(), true, List.of(),
                                SourceRange.span(start.range(), end.range())), fields, members);
                    } else recoverMember();
                } else {
                    var staticSpecifiers = staticMember ? types.parseDeclarationSpecifiers("期望成员类型") : null;
                    var declaration = staticMember
                            ? staticSpecifiers == null ? null : types.parseMemberDeclarator(staticSpecifiers, "期望成员名称", true)
                            : types.parseNamedType("期望成员类型", "期望成员名称", false, true);
                    if (declaration == null) recoverMember();
                    else if (declaration.name().equals(simpleName) && declaration.type().unqualified().isFunction()) {
                        state.unsupportedCpp(declaration.nameRange(), "构造函数不能声明返回类型");
                        recoverMember();
                    }
                    else if (declaration.type().unqualified() instanceof MiniType.FunctionType function) {
                        types.declareOrdinaryName(declaration.name(), declaration.nameRange());
                        boolean constQualified = state.match(TokenType.CONST);
                        function = types.parseTrailingReturn(types.parseFunctionException(function));
                        if (staticMember && constQualified) state.report(declaration.nameRange(), "static 成员函数不能带 const 限定符");
                        if (union) {
                            state.unsupportedCpp(declaration.nameRange(), "union 成员方法尚未实现");
                            recoverMember();
                        } else if (!declaration.alignmentSpecs().isEmpty()) {
                            state.unsupportedCpp(declaration.range(), "成员方法不能使用 alignas");
                            recoverMember();
                        } else if (!state.check(TokenType.LEFT_BRACE) && !state.check(TokenType.SEMICOLON) && !state.check(TokenType.EQUAL)) {
                            state.unsupportedCpp(state.peek().range(), "成员方法限定符或说明符尚未实现");
                            recoverMember();
                        } else {
                            Parser.Context.TokenWindow body = state.check(TokenType.LEFT_BRACE) ? state.deferBlock() : null;
                            DefinitionKind kind=DefinitionKind.ORDINARY;
                            if(body==null){if(state.check(TokenType.EQUAL))kind=CppFunctionDefinitionParser.parse(state);else state.advance();}
                            var method = makeMethod(declaration, function, null, state.previous().range()).withDefinitionKind(kind).withConstexprSpecifier(constexprSpecifier);
                            int index = members.size();
                            members.add(new MethodMember(method, constQualified, staticMember, declaration.nameRange()));
                            if (body != null) deferred.add(new DeferredMethod(index, method, body,
                                    declaration.parameters().stream().filter(p -> p.name().isEmpty()).map(Parser.ParsedParameter::range).toList()));
                        }
                    } else if (staticMember) {
                        boolean first = true;
                        while (declaration != null) {
                            types.declareOrdinaryName(declaration.name(), declaration.nameRange());
                            var initialization = statements.parseVariableInitializer(declaration.type(), declaration.range());
                            Token end = state.previous();
                            boolean more = state.check(TokenType.COMMA);
                            if (!more) end = state.consume(TokenType.SEMICOLON, "期望 ';'");
                            if (union) state.unsupportedCpp(declaration.nameRange(), "union 不能具有 static 数据成员");
                            else if (end != null) members.add(new StaticFieldMember(new GlobalVarDecl(declaration.name(), declaration.type(),
                                    initialization.expression(), false, declaration.alignmentSpecs(), initialization.cppInitializer(),
                                    SourceRange.span(first ? declarationStart.range() : declaration.range(), end.range()))
                                    .withConstexprSpecifier(constexprSpecifier)));
                            if (!more || end == null) break;
                            state.advance();
                            first = false;
                            declaration = types.parseMemberDeclarator(staticSpecifiers, "期望成员名称", false);
                            if (declaration == null) { recoverMember(); break; }
                            if (declaration.type().unqualified().isFunction()) {
                                state.unsupportedCpp(declaration.range(), "同一声明中的函数与数据成员混合声明尚未实现");
                                recoverMember(); break;
                            }
                        }
                    } else if (declaration.type().containsAuto()) {
                        state.report(declaration.range(), "A non-static data member cannot have an auto or decltype(auto) placeholder type");
                        recoverMember();
                    } else if (state.check(TokenType.EQUAL) || state.check(TokenType.LEFT_BRACE)) {
                        if(constexprSpecifier)state.report(declarationStart,"A non-static data member cannot be constexpr");
                        if (union) {
                            state.unsupportedCpp(state.peek().range(), "union 默认成员初始化尚未实现");
                            deferFieldInitializer();
                            state.match(TokenType.SEMICOLON);
                        } else {
                            var initializer = deferFieldInitializer();
                            Token end = state.consume(TokenType.SEMICOLON, "期望 ';'");
                            if (end != null) {
                                int index = members.size();
                                addField(new StructField(declaration.name(), declaration.type(), declaration.alignmentSpecs(),
                                        SourceRange.span(declaration.range(), end.range())), fields, members);
                                defaults.add(new DeferredField(index, initializer));
                            }
                        }
                    } else if (!state.check(TokenType.SEMICOLON)) {
                        state.unsupportedCpp(state.peek().range(), "成员位域或多声明器尚未实现");
                        recoverMember();
                    } else {
                        if(constexprSpecifier)state.report(declarationStart,"A non-static data member cannot be constexpr");
                        Token end = state.advance();
                        addField(new StructField(declaration.name(), declaration.type(), declaration.alignmentSpecs(),
                                SourceRange.span(declaration.range(), end.range())), fields, members);
                    }
                }
                if (state.currentIndex() == before && !state.isAtEnd() && !state.check(TokenType.RIGHT_BRACE)) state.advance();
            }
            Token close = state.consume(TokenType.RIGHT_BRACE, "期望 '}'");
            if (close == null) return null;
            // Member declarations are now complete. Every body still owns a separate parameter/local scope.
            for (DeferredMethod item : deferred) {
                FunctionDecl signature = item.signature();
                BlockStmt body = state.inTokenWindow(item.body(), () -> statements.parseFunctionBlock(
                        signature.parameters().stream().map(Parameter::name).toList()));
                var method = new FunctionDecl(signature.name(), signature.returnType(), signature.parameters(),
                        signature.variadic(), body, false, false, signature.range(), signature.operatorName(), signature.conversionName(),signature.definitionKind(),signature.exceptionSpecification(),signature.constexprSpecifier());
                MethodMember old = (MethodMember) members.get(item.memberIndex());
                var member = new MethodMember(method, old.constQualified(), old.staticMember(), old.nameRange());
                members.set(item.memberIndex(), member);
                state.build(method, "MethodDecl " + method.name(), method.range());
            }
            for(var item:templates) {
                types.restoreFunctionTemplateParameters(item.parameters());
                try {
                    if(item.constructor()!=null)members.set(item.index(),new TemplateConstructorMember(item.parameters(),completeConstructor(item.constructor())));
                    else {
                        FunctionDecl signature=item.method();
                        BlockStmt body=item.body()==null?null:state.inTokenWindow(item.body(),()->statements.parseFunctionBlock(signature.parameters().stream().map(Parameter::name).toList()));
                        FunctionDecl method=new FunctionDecl(signature.name(),signature.returnType(),signature.parameters(),signature.variadic(),body,false,false,signature.range(),signature.operatorName(),signature.conversionName(),signature.definitionKind(),signature.exceptionSpecification(),signature.constexprSpecifier());
                        members.set(item.index(),new TemplateMethodMember(item.parameters(),new MethodMember(method,item.constant(),item.statik(),item.nameRange())));
                    }
                } finally {types.exitFunctionTemplate();}
            }
            for (DeferredConstructor item : constructors) members.set(item.memberIndex(), completeConstructor(item));
            for (DeferredDestructor item : destructors) members.set(item.memberIndex(), completeDestructor(item));
            for (DeferredField item : defaults) {
                FieldMember field = (FieldMember) members.get(item.memberIndex());
                members.set(item.memberIndex(), new FieldMember(field.field(), parseDeferredInitializer(item.initializer())));
            }
            return new StructDecl(identity, fields, true, union, metadata(key, union, members),
                    SourceRange.span(key.range(), close.range()));
        } finally {
            types.exitMemberScope();
        }
    }

    private DeferredMemberTemplate readMemberTemplate(String className,int index) {
        Token start=state.advance();if(state.consume(TokenType.LESS,"template 后期望 '<'")==null)return null;
        String owner=types.beginFunctionTemplate(start);
        try {
            var parameters=types.readTemplateParameters(start,owner);
            if(parameters==null||state.consumeTemplateGreater("模板参数后期望 '>'")==null)return null;
            if(parameters.isEmpty()){state.report(start,"成员模板不能在类内显式特化");return null;}
            boolean explicit=false,statik=false,constexpr=false;
            while(state.check(TokenType.STATIC)||state.check(TokenType.EXPLICIT)||state.check(TokenType.CONSTEXPR)||state.check(TokenType.INLINE)){
                Token specifier=state.advance();
                if(specifier.type()==TokenType.EXPLICIT)explicit=true;
                else if(specifier.type()==TokenType.STATIC)statik=true;
                else if(specifier.type()==TokenType.CONSTEXPR)constexpr=true;
            }
            if(state.check(TokenType.IDENTIFIER)&&state.peek().lexeme().equals(className)&&state.peekAt(1).type()==TokenType.LEFT_PAREN) {
                Token name=state.advance();
                if(statik)state.report(name,"构造模板不能是 static");
                var constructor=constexprConstructor(readConstructor(name.lexeme(),name.range(),start.range(),index,explicit),constexpr);
                return constructor==null?null:new DeferredMemberTemplate(index,parameters,null,constructor,null,false,false,name.range());
            }
            if(explicit){state.report(start,"此成员模板不能使用 explicit");return null;}
            var declaration=types.parseNamedType("期望成员模板返回类型","期望成员模板名称",false,true);
            if(declaration==null)return null;
            if(!(declaration.type().unqualified() instanceof MiniType.FunctionType function)){state.report(start,"成员模板需要函数或构造函数");return null;}
            boolean constant=state.match(TokenType.CONST);
            function=types.parseTrailingReturn(types.parseFunctionException(function));
            if(statik&&constant)state.report(start,"static 成员模板不能 cv 限定");
            Parser.Context.TokenWindow body=state.check(TokenType.LEFT_BRACE)?state.deferBlock():null;
            DefinitionKind kind=DefinitionKind.ORDINARY;
            if(body==null){if(state.check(TokenType.EQUAL))kind=CppFunctionDefinitionParser.parse(state);
                else if(state.consume(TokenType.SEMICOLON,"期望成员模板函数体或 ';'")==null)return null;}
            FunctionDecl method=makeMethod(declaration,function,null,state.previous().range()).withDefinitionKind(kind).withConstexprSpecifier(constexpr);
            return new DeferredMemberTemplate(index,parameters,method,null,body,constant,statik,declaration.nameRange());
        } finally {types.exitFunctionTemplate();}
    }

    private void addField(StructField field, List<StructField> fields, List<CppMember> members) {
        fields.add(field);
        members.add(new FieldMember(field));
        types.declareMemberField(field);
    }

    private FunctionDecl makeMethod(Parser.ParsedNamedType declaration, MiniType.FunctionType function,
                                    BlockStmt body, SourceRange end) {
        List<Parameter> parameters = new ArrayList<>();
        for (int index = 0; index < declaration.parameters().size(); index++) {
            var parsed = declaration.parameters().get(index);
            parameters.add(new Parameter(parsed.name().isEmpty() ? "__unnamed" + index : parsed.name(), parsed.type(), parsed.defaultValue(), parsed.range()));
        }
        return new FunctionDecl(declaration.name(), function.returnType(), parameters, function.variadic(), body,
                false, false, SourceRange.span(declaration.range(), end), declaration.operatorName()).withExceptionSpecification(function.exceptionSpecification());
    }

    public boolean startsTemplateSpecialMember() {
        int end=types.templateMemberDelimiter();if(end<0)return false;
        TokenType next=state.peekAt(end+1).type();
        if(next==TokenType.TILDE || next==TokenType.OPERATOR)return true;
        if(next!=TokenType.IDENTIFIER || state.peekAt(end+2).type()!=TokenType.LEFT_PAREN)return false;
        String owner=null;
        for(int i=0;i<end;i++)if(state.peekAt(i).type()==TokenType.LESS){owner=state.peekAt(i-1).lexeme();break;}
        return state.peekAt(end+1).lexeme().equals(owner);
    }

    public Declaration parseTemplateSpecialMember() {
        Token start=state.peek();QualifiedName prefix=types.parseTemplateMemberOwner();if(prefix==null)return null;
        var segments=new ArrayList<>(prefix.segments());
        boolean conversion=state.check(TokenType.OPERATOR),destructor=state.match(TokenType.TILDE);
        Token name=conversion?state.peek():state.consume(TokenType.IDENTIFIER,"期望类成员名称");if(name==null)return null;
        SourceRange nameRange=destructor?SourceRange.span(start.range(),name.range()):name.range();
        String spelling=conversion?"operator":(destructor?"~":"")+name.lexeme();segments.add(spelling);
        QualifiedName qualified=new QualifiedName(true,segments,SourceRange.span(start.range(),name.range()));
        types.enterMemberDefinitionScope(qualified);
        try {
            if(conversion) {
                ParsedConversion parsed=readConversion(start.range(),false);if(parsed==null)return null;
                FunctionDecl signature=parsed.member().method();
                BlockStmt body=parsed.body()==null?null:state.inTokenWindow(parsed.body(),()->statements.parseFunctionBlock(List.of()));
                FunctionDecl function=new FunctionDecl(signature.name(),signature.returnType(),signature.parameters(),signature.variadic(),body,
                        false,false,signature.range(),signature.operatorName(),signature.conversionName(),signature.definitionKind(),signature.exceptionSpecification()).withConstexprSpecifier(signature.constexprSpecifier());
                segments.set(segments.size()-1,function.name());
                return new OutOfLineMethodDecl(new QualifiedName(true,segments,qualified.range()),function,parsed.member().constQualified(),parsed.member().nameRange());
            }
            String expected=prefix.segments().getLast();if(!name.lexeme().equals(expected))state.report(nameRange,"构造/析构名称必须是所属类名称");
            if(destructor) {
                var item=readDestructor(name.lexeme(),nameRange,start.range(),-1);
                return item==null?null:new OutOfLineDestructorDecl(qualified,completeDestructor(item),nameRange);
            }
            var item=readConstructor(name.lexeme(),nameRange,start.range(),-1,false);
            return item==null?null:new OutOfLineConstructorDecl(qualified,completeConstructor(item),nameRange);
        } finally {types.exitMemberDefinitionScope();}
    }

    /** Lookup distinguishes injected constructor names from namespace-qualified ordinary types. */
    public boolean startsOutOfLineConstructor() {
        QualifiedName name = CppNameParser.peekName(state, 0);
        if (name == null || !types.namesConstructor(name)) return false;
        var parts = name.segments();
        int after = parts.size() * 2 - 1 + (name.global() ? 1 : 0);
        return state.peekAt(after).type() == TokenType.LEFT_PAREN;
    }

    public OutOfLineConstructorDecl parseOutOfLineConstructor() {
        QualifiedName name = CppNameParser.parseName(state);
        if (name == null) return null;
        SourceRange nameRange = state.previous().range();
        types.enterMemberDefinitionScope(name);
        try {
            var deferred = readConstructor(name.segments().getLast(), nameRange, name.range(), -1, false);
            if (deferred == null) {
                recoverMember();
                state.markDeclarationBoundaryRecovered();
                return null;
            }
            if (deferred.body() == null && !deferred.signature().hasDefinition()) state.unsupportedCpp(name.range(), "类外构造声明必须提供定义");
            ConstructorMember constructor = completeConstructor(deferred);
            var definition = new OutOfLineConstructorDecl(name, constructor, nameRange);
            state.build(definition, "OutOfLineConstructor " + constructor.name(), definition.range());
            return definition;
        } finally {
            types.exitMemberDefinitionScope();
        }
    }

    /** A no-return-type qualified operator declaration is a conversion function. */
    public boolean startsOutOfLineConversion() {
        int offset = state.peekAt(0).type() == TokenType.SCOPE ? 1 : 0;
        boolean owner = false;
        while (state.peekAt(offset).type() == TokenType.IDENTIFIER
                && state.peekAt(offset + 1).type() == TokenType.SCOPE) {
            owner = true;
            offset += 2;
        }
        return owner && state.peekAt(offset).type() == TokenType.OPERATOR;
    }

    public OutOfLineMethodDecl parseOutOfLineConversion() {
        Token start = state.peek();
        boolean global = state.match(TokenType.SCOPE);
        List<String> segments = new ArrayList<>();
        while (state.check(TokenType.IDENTIFIER) && state.peekAt(1).type() == TokenType.SCOPE) {
            segments.add(state.advance().lexeme());
            state.advance();
        }
        segments.add("operator");
        // Member-definition lookup uses the owner prefix; the final name is resolved below.
        types.enterMemberDefinitionScope(new QualifiedName(global, segments,
                SourceRange.span(start.range(), state.peek().range())));
        try {
            ParsedConversion conversion = readConversion(start.range(), false);
            if (conversion == null) {
                recoverMember();
                state.markDeclarationBoundaryRecovered();
                return null;
            }
            MethodMember member = conversion.member();
            if (conversion.body() == null && !conversion.member().method().hasDefinition())
                state.report(member.nameRange(), "类外转换声明必须提供定义");
            BlockStmt body = conversion.body() == null ? null : state.inTokenWindow(conversion.body(),
                    () -> statements.parseFunctionBlock(List.of()));
            FunctionDecl signature = member.method();
            FunctionDecl function = new FunctionDecl(signature.name(), signature.returnType(), signature.parameters(),
                    signature.variadic(), body, false, false, signature.range(), null, signature.conversionName(),signature.definitionKind(),signature.exceptionSpecification(),signature.constexprSpecifier());
            segments.set(segments.size() - 1, function.name());
            QualifiedName name = new QualifiedName(global, segments, SourceRange.span(start.range(), member.nameRange()));
            var result = new OutOfLineMethodDecl(name, function, member.constQualified(), member.nameRange());
            state.build(result, "OutOfLineConversion " + function.name(), result.range());
            return result;
        } finally {
            types.exitMemberDefinitionScope();
        }
    }

    private ParsedConversion readConversion(SourceRange start, boolean explicitSpecifier) {
        Token operator = state.consume(TokenType.OPERATOR, "转换函数期望 operator");
        if (operator == null) return null;
        var target = types.parseCppTypeWithoutFunctionSuffix("期望转换目标类型");
        if (target == null) return null;
        if (target.type().unqualified().isArray() || target.type().unqualified().isFunction())
            state.report(target.range(), "转换函数不能转换到数组或函数类型");
        SourceRange nameRange = SourceRange.span(operator.range(), target.range());
        Token open = state.consume(TokenType.LEFT_PAREN, "转换函数期望 '('");
        if (open == null) return null;
        var parameters = types.parseParameterList();
        Token close = state.consume(TokenType.RIGHT_PAREN, "转换函数期望 ')'");
        if (close == null) return null;
        if (!parameters.parameters().isEmpty() || parameters.variadic())
            state.report(SourceRange.span(open.range(), close.range()), "转换函数不能声明参数");
        boolean constQualified = state.match(TokenType.CONST);
        var exceptionSpecification=types.parseExceptionSpecification();
        if (state.check(TokenType.CONST)) {
            state.report(state.peek().range(), "转换函数 const 限定符重复");
            return null;
        }
        Parser.Context.TokenWindow body = null;
        DefinitionKind kind=DefinitionKind.ORDINARY;
        if (state.check(TokenType.LEFT_BRACE)) body = state.deferBlock();
        else if(state.check(TokenType.EQUAL))kind=CppFunctionDefinitionParser.parse(state);
        else if (!state.match(TokenType.SEMICOLON)) {
            state.unsupportedCpp(state.peek().range(), "转换函数限定符或说明符尚未实现");
            return null;
        }
        ConversionName name = new ConversionName(target.type(), explicitSpecifier, nameRange);
        FunctionDecl method = new FunctionDecl(name.spelling(), target.type(), List.of(), false, null,
                false, false, SourceRange.span(start, state.previous().range()), null, name,kind,exceptionSpecification);
        return new ParsedConversion(new MethodMember(method, constQualified, nameRange), body);
    }

    /** This spelling cannot be a variable declarator, so classification needs no speculative type mutation. */
    public boolean startsOutOfLineDestructor() {
        int offset = state.peekAt(0).type() == TokenType.SCOPE ? 1 : 0;
        boolean owner = false;
        while (state.peekAt(offset).type() == TokenType.IDENTIFIER
                && state.peekAt(offset + 1).type() == TokenType.SCOPE) {
            owner = true;
            offset += 2;
        }
        return owner && state.peekAt(offset).type() == TokenType.TILDE;
    }

    public OutOfLineDestructorDecl parseOutOfLineDestructor() {
        Token start = state.peek();
        boolean global = state.match(TokenType.SCOPE);
        List<String> segments = new ArrayList<>();
        while (state.check(TokenType.IDENTIFIER) && state.peekAt(1).type() == TokenType.SCOPE) {
            segments.add(state.advance().lexeme());
            state.advance();
        }
        Token tilde = state.consume(TokenType.TILDE, "析构函数期望 '~'");
        Token name = state.consume(TokenType.IDENTIFIER, "析构函数期望类名称");
        if (tilde == null || name == null) {
            recoverMember();
            state.markDeclarationBoundaryRecovered();
            return null;
        }
        SourceRange nameRange = SourceRange.span(tilde.range(), name.range());
        segments.add(name.lexeme());
        QualifiedName injectedName = new QualifiedName(global, segments, SourceRange.span(start.range(), name.range()));
        if (!types.namesConstructor(injectedName)) state.report(nameRange, "析构名称必须是所属类的名称");
        segments.set(segments.size() - 1, "~" + name.lexeme());
        QualifiedName qualifiedName = new QualifiedName(global, segments, injectedName.range());
        types.enterMemberDefinitionScope(qualifiedName);
        try {
            var deferred = readDestructor(name.lexeme(), nameRange, start.range(), -1);
            if (deferred == null) {
                recoverMember();
                state.markDeclarationBoundaryRecovered();
                return null;
            }
            if (deferred.body() == null && !deferred.signature().hasDefinition()) state.unsupportedCpp(qualifiedName.range(), "类外析构声明必须提供定义");
            var definition = new OutOfLineDestructorDecl(qualifiedName, completeDestructor(deferred), nameRange);
            state.build(definition, "OutOfLineDestructor " + name.lexeme(), definition.range());
            return definition;
        } finally {
            types.exitMemberDefinitionScope();
        }
    }

    private DeferredDestructor readDestructor(String name, SourceRange nameRange, SourceRange start, int index) {
        Token open = state.consume(TokenType.LEFT_PAREN, "析构函数期望 '('");
        if (open == null) return null;
        var parameters = types.parseParameterList();
        Token close = state.consume(TokenType.RIGHT_PAREN, "析构函数期望 ')'");
        if (close == null) return null;
        if (!parameters.parameters().isEmpty() || parameters.variadic())
            state.report(SourceRange.span(open.range(), close.range()), "析构函数不能声明参数");
        var exceptionSpecification=types.parseExceptionSpecification();
        Parser.Context.TokenWindow body = null;
        DefinitionKind kind=DefinitionKind.ORDINARY;
        if (state.check(TokenType.LEFT_BRACE)) body = state.deferBlock();
        else if(state.check(TokenType.EQUAL))kind=CppFunctionDefinitionParser.parse(state);
        else if (!state.match(TokenType.SEMICOLON)) {
            state.unsupportedCpp(state.peek().range(), "析构函数限定符、说明符或成员初始化列表尚未支持或不合法");
            return null;
        }
        var signature = new DestructorMember(name, null, nameRange, SourceRange.span(start, state.previous().range()),kind,exceptionSpecification);
        return new DeferredDestructor(index, signature, body);
    }

    private DestructorMember completeDestructor(DeferredDestructor item) {
        DestructorMember signature = item.signature();
        BlockStmt body = item.body() == null ? null : state.inTokenWindow(item.body(), () -> statements.parseFunctionBlock(List.of()));
        var destructor = new DestructorMember(signature.name(), body, signature.nameRange(), signature.range(),signature.definitionKind(),signature.exceptionSpecification());
        state.build(destructor, "DestructorDecl " + destructor.name(), destructor.range());
        return destructor;
    }

    private DeferredConstructor readConstructor(String name, SourceRange nameRange, SourceRange start, int index,
                                                boolean explicitSpecifier) {
        if (state.consume(TokenType.LEFT_PAREN, "构造参数期望 '('") == null) return null;
        var parameters = types.parseParameterList();
        if (state.consume(TokenType.RIGHT_PAREN, "构造参数期望 ')'") == null) return null;
        var exceptionSpecification=types.parseExceptionSpecification();
        List<DeferredInitializer> initializers = new ArrayList<>();
        if (state.match(TokenType.COLON)) {
            do {
                QualifiedName target = CppNameParser.parseName(state);
                if (target == null) return null;
                if (target.segments().size() == 1 && target.segments().getFirst().equals(name))
                    state.unsupportedCpp(target.range(), "委托构造函数尚未实现");
                Parser.Context.TokenWindow arguments;
                if (state.check(TokenType.LEFT_PAREN)) arguments = state.deferParentheses();
                else if (state.check(TokenType.LEFT_BRACE)) arguments = state.deferBlock();
                else { state.report(state.peek(), "成员初始化期望 '(' 或 '{'"); return null; }
                initializers.add(new DeferredInitializer(target, arguments,
                        SourceRange.span(target.range(), state.previous().range())));
            } while (state.match(TokenType.COMMA));
        }
        Parser.Context.TokenWindow body = null;
        DefinitionKind kind=DefinitionKind.ORDINARY;
        if (state.check(TokenType.LEFT_BRACE)) body = state.deferBlock();
        else if(state.check(TokenType.EQUAL)) {
            if(!initializers.isEmpty())state.report(nameRange,"A defaulted/deleted constructor cannot have member initializers");
            kind=CppFunctionDefinitionParser.parse(state);
        }
        else if (state.match(TokenType.SEMICOLON)) {
            if (!initializers.isEmpty()) state.report(state.previous(), "成员初始化列表必须具有构造函数体");
        } else if (state.check(TokenType.CONST) || state.check(TokenType.VOLATILE)
                || state.check(TokenType.AMPERSAND) || state.check(TokenType.AMPERSAND_AMPERSAND)) {
            state.report(state.peek().range(), "构造函数不能声明 cv 或引用限定符");
            return null;
        } else {
            state.unsupportedCpp(state.peek().range(), "此构造函数限定符或说明符尚未实现");
            return null;
        }
        List<Parameter> resolved = new ArrayList<>();
        List<SourceRange> unnamed = new ArrayList<>();
        for (int i = 0; i < parameters.parameters().size(); i++) {
            var parameter = parameters.parameters().get(i);
            if (parameter.name().isEmpty()) unnamed.add(parameter.range());
            resolved.add(new Parameter(parameter.name().isEmpty() ? "__unnamed" + i : parameter.name(), parameter.type(), parameter.defaultValue(), parameter.range()));
        }
        var signature = new ConstructorMember(name, resolved, parameters.variadic(), List.of(), null,
                nameRange, SourceRange.span(start, state.previous().range()), explicitSpecifier,kind,exceptionSpecification);
        return new DeferredConstructor(index, signature, initializers, body, unnamed);
    }

    private DeferredConstructor constexprConstructor(DeferredConstructor constructor,boolean value){
        return constructor==null?null:new DeferredConstructor(constructor.memberIndex(),constructor.signature().withConstexprSpecifier(value),constructor.initializers(),constructor.body(),constructor.unnamedParameters());
    }
    private ConstructorMember completeConstructor(DeferredConstructor deferred) {
        var signature = deferred.signature();
        List<MemberInitializer> initializers = new ArrayList<>();
        BlockStmt body = null;
        // The same parameter scope is available to mem-initializers and the function body only.
        types.enterScope(signature.parameters().stream().map(Parameter::name).toList());
        try {
            for (var initializer : deferred.initializers()) {
                CppInitializer value = parseDeferredInitializer(initializer.arguments());
                if (value != null) initializers.add(new MemberInitializer(initializer.target(), value, initializer.range()));
            }
            if (deferred.body() != null) {
                body = state.inTokenWindow(deferred.body(), statements::parseBlock);
            }
        } finally {
            types.exitScope();
        }
        var constructor = new ConstructorMember(signature.name(), signature.parameters(), signature.variadic(),
                initializers, body, signature.nameRange(), signature.range(), signature.explicitSpecifier(),signature.definitionKind(),signature.exceptionSpecification(),signature.constexprSpecifier());
        state.build(constructor, "ConstructorDecl " + signature.name(), constructor.range());
        return constructor;
    }

    private CppInitializer parseDeferredInitializer(Parser.Context.TokenWindow window) {
        return state.inTokenWindow(window, () -> {
            CppInitializer initializer = statements.parseCppInitializer();
            if (!state.isAtEnd()) state.report(state.peek(), "初始化器后存在未解析的语法");
            return initializer;
        });
    }

    /** A member initializer cannot contain an unparenthesized declaration separator. */
    private Parser.Context.TokenWindow deferFieldInitializer() {
        int start = state.currentIndex(), braces = 0;
        while (!state.isAtEnd() && !state.check(TokenType.SEMICOLON)) {
            if (state.check(TokenType.RIGHT_BRACE) && braces == 0) break;
            TokenType token = state.advance().type();
            if (token == TokenType.LEFT_BRACE) braces++;
            if (token == TokenType.RIGHT_BRACE) braces--;
        }
        return new Parser.Context.TokenWindow(start, state.currentIndex());
    }

    private boolean unsupportedPrefix(Token token) {
        return token.type() == TokenType.TILDE
                || token.type() == TokenType.TYPEDEF || token.type() == TokenType.USING
                || token.type().isCppToken() && token.type() != TokenType.CLASS && token.type() != TokenType.SCOPE
                    && token.type() != TokenType.TYPENAME && token.type() != TokenType.AUTO && token.type() != TokenType.DECLTYPE;
    }

    /** An anonymous type can still have a named, array, or pointer field declarator. */
    private boolean isAnonymousMember() {
        if ((!state.check(TokenType.STRUCT) && !state.check(TokenType.UNION))
                || state.peekAt(1).type() != TokenType.LEFT_BRACE) return false;
        int depth = 0;
        for (int offset = 1; state.peekAt(offset).type() != TokenType.EOF; offset++) {
            TokenType token = state.peekAt(offset).type();
            if (token == TokenType.LEFT_BRACE) depth++;
            if (token == TokenType.RIGHT_BRACE && --depth == 0) {
                return state.peekAt(offset + 1).type() == TokenType.SEMICOLON;
            }
        }
        return false;
    }

    /** Skip one unsupported member, never the containing record's closing brace. */
    private void recoverMember() {
        while (!state.isAtEnd() && !state.check(TokenType.RIGHT_BRACE)) {
            if (state.match(TokenType.SEMICOLON)) return;
            if (state.check(TokenType.LEFT_BRACE)) {
                state.deferBlock();
                state.match(TokenType.SEMICOLON);
                return;
            }
            state.advance();
        }
    }

    private static CppRecordInfo metadata(Token key, boolean union, List<CppMember> members) {
        return union ? null : new CppRecordInfo(key.type() == TokenType.CLASS ? RecordKey.CLASS : RecordKey.STRUCT, members, key.range());
    }
    private static boolean isAccess(TokenType token) { return token == TokenType.PUBLIC || token == TokenType.PROTECTED || token == TokenType.PRIVATE; }
    private static Access access(TokenType token) { return switch (token) { case PUBLIC -> Access.PUBLIC; case PROTECTED -> Access.PROTECTED; default -> Access.PRIVATE; }; }
    private record DeferredMemberTemplate(int index,List<ClassTemplateDecl.Parameter> parameters,FunctionDecl method,
                                           DeferredConstructor constructor,Parser.Context.TokenWindow body,
                                           boolean constant,boolean statik,SourceRange nameRange) {}
    private record DeferredMethod(int memberIndex, FunctionDecl signature, Parser.Context.TokenWindow body,
                                  List<SourceRange> unnamedParameters) { }
    private record DeferredInitializer(QualifiedName target, Parser.Context.TokenWindow arguments, SourceRange range) { }
    private record DeferredConstructor(int memberIndex, ConstructorMember signature, List<DeferredInitializer> initializers,
                                       Parser.Context.TokenWindow body, List<SourceRange> unnamedParameters) { }
    private record DeferredDestructor(int memberIndex, DestructorMember signature, Parser.Context.TokenWindow body) { }
    private record DeferredField(int memberIndex, Parser.Context.TokenWindow initializer) { }
    private record ParsedConversion(MethodMember member, Parser.Context.TokenWindow body) { }
}
