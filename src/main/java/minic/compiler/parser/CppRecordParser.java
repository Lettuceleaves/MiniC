package minic.compiler.parser;

import minic.SourceRange;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.manager.StatementManager;
import minic.compiler.parser.node.Declaration.*;
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
        if (state.match(TokenType.COLON)) {
            state.unsupportedCpp(state.previous().range(), "类继承尚未实现");
            while (!state.check(TokenType.LEFT_BRACE) && !state.check(TokenType.SEMICOLON) && !state.isAtEnd()) state.advance();
        }
        MiniType type = types.declareAggregate(name.lexeme(), union, state.check(TokenType.LEFT_BRACE), name.range());
        String identity = ((MiniType.StructType) type.unqualified()).name();
        if (state.match(TokenType.SEMICOLON)) {
            var forward = new StructDecl(identity, List.of(), false, union, metadata(key, union, List.of()),
                    SourceRange.span(key.range(), state.previous().range()));
            state.build(forward, "CppRecordForward " + identity, forward.range());
            return forward;
        }
        if (state.consume(TokenType.LEFT_BRACE, "期望 '{'") == null) return null;
        StructDecl definition = parseDefinition(type, union, key);
        Token semicolon = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (definition == null || semicolon == null) return null;
        var result = new StructDecl(identity, definition.fields(), true, union, definition.cppInfo(),
                SourceRange.span(key.range(), semicolon.range()));
        types.recordAggregateFields(result);
        state.build(result, "CppRecord " + identity, result.range());
        return result;
    }

    /** Opening brace was consumed by the surrounding declaration/type parser. */
    public StructDecl parseDefinition(MiniType type, boolean union, Token key) {
        String identity = ((MiniType.StructType) type.unqualified()).name();
        String simpleName = identity.substring(identity.lastIndexOf("::") + 2);
        List<StructField> fields = new ArrayList<>();
        List<CppMember> members = new ArrayList<>();
        List<DeferredMethod> deferred = new ArrayList<>();
        types.enterMemberScope(type);
        try {
            while (!state.check(TokenType.RIGHT_BRACE) && !state.isAtEnd()) {
                if (state.match(TokenType.SEMICOLON)) continue;
                int before = state.currentIndex();
                Token start = state.peek();
                if (isAccess(start.type())) {
                    state.advance();
                    Token colon = state.consume(TokenType.COLON, "访问说明符后期望 ':'");
                    if (union) state.unsupportedCpp(start.range(), "union 访问说明符尚未实现");
                    else if (colon != null) members.add(new AccessLabel(access(start.type()), SourceRange.span(start.range(), colon.range())));
                } else if (unsupportedPrefix(start, simpleName)) {
                    state.unsupportedCpp(start.range(), "此类成员语法尚未实现：" + start.lexeme());
                    recoverMember();
                } else if (isAnonymousMember()) {
                    var anonymous = types.parseType("期望匿名聚合类型");
                    Token end = state.consume(TokenType.SEMICOLON, "期望 ';'");
                    if (anonymous != null && end != null) {
                        addField(new StructField("", anonymous.type(), true, List.of(),
                                SourceRange.span(start.range(), end.range())), fields, members);
                    } else recoverMember();
                } else {
                    var declaration = types.parseNamedType("期望成员类型", "期望成员名称");
                    if (declaration == null) recoverMember();
                    else if (declaration.type().unqualified() instanceof MiniType.FunctionType function) {
                        types.declareOrdinaryName(declaration.name(), declaration.nameRange());
                        boolean constQualified = state.match(TokenType.CONST);
                        if (union) {
                            state.unsupportedCpp(declaration.nameRange(), "union 成员方法尚未实现");
                            recoverMember();
                        } else if (!declaration.alignmentSpecs().isEmpty()) {
                            state.unsupportedCpp(declaration.range(), "成员方法不能使用 alignas");
                            recoverMember();
                        } else if (!state.check(TokenType.LEFT_BRACE) && !state.check(TokenType.SEMICOLON)) {
                            state.unsupportedCpp(state.peek().range(), "成员方法限定符或说明符尚未实现");
                            recoverMember();
                        } else {
                            Parser.Context.TokenWindow body = state.check(TokenType.LEFT_BRACE) ? state.deferBlock() : null;
                            if (body == null) state.advance();
                            var method = makeMethod(declaration, function, null, state.previous().range());
                            int index = members.size();
                            members.add(new MethodMember(method, constQualified, declaration.nameRange()));
                            if (body != null) deferred.add(new DeferredMethod(index, method, body,
                                    declaration.parameters().stream().filter(p -> p.name().isEmpty()).map(Parser.ParsedParameter::range).toList()));
                        }
                    } else if (!state.check(TokenType.SEMICOLON)) {
                        state.unsupportedCpp(state.peek().range(), "成员默认初始化、位域或多声明器尚未实现");
                        recoverMember();
                    } else {
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
                item.unnamedParameters().forEach(range -> state.report(range, "方法定义中的参数必须命名"));
                BlockStmt body = state.inTokenWindow(item.body(), () -> statements.parseFunctionBlock(
                        signature.parameters().stream().map(Parameter::name).toList()));
                var method = new FunctionDecl(signature.name(), signature.returnType(), signature.parameters(),
                        signature.variadic(), body, false, false, signature.range());
                MethodMember old = (MethodMember) members.get(item.memberIndex());
                var member = new MethodMember(method, old.constQualified(), old.nameRange());
                members.set(item.memberIndex(), member);
                state.build(method, "MethodDecl " + method.name(), method.range());
            }
            return new StructDecl(identity, fields, true, union, metadata(key, union, members),
                    SourceRange.span(key.range(), close.range()));
        } finally {
            types.exitMemberScope();
        }
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
            parameters.add(new Parameter(parsed.name().isEmpty() ? "__unnamed" + index : parsed.name(), parsed.type(), parsed.range()));
        }
        return new FunctionDecl(declaration.name(), function.returnType(), parameters, function.variadic(), body,
                false, false, SourceRange.span(declaration.range(), end));
    }

    private boolean unsupportedPrefix(Token token, String simpleName) {
        return token.type() == TokenType.TILDE
                || token.type() == TokenType.IDENTIFIER && token.lexeme().equals(simpleName)
                && state.peekAt(1).type() == TokenType.LEFT_PAREN
                || token.type() == TokenType.TYPEDEF || token.type() == TokenType.USING
                || token.type().isCppToken() && token.type() != TokenType.CLASS && token.type() != TokenType.SCOPE;
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
    private record DeferredMethod(int memberIndex, FunctionDecl signature, Parser.Context.TokenWindow body,
                                  List<SourceRange> unnamedParameters) { }
}
