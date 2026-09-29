package minic.compiler.parser.manager;

import minic.compiler.parser.Parser;

import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Parameter;
import minic.compiler.parser.node.Declaration.StructDecl;
import minic.compiler.parser.node.Declaration.StructField;
import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.ArrayList;

public final class DeclarationManager {
    private final Parser.Context state;
    private final StatementManager statementManager;
    private final Parser.TypeReader typeReader;

    public DeclarationManager(Parser.Context state, StatementManager statementManager, Parser.TypeReader typeReader) {
        this.state = state;
        this.statementManager = statementManager;
        this.typeReader = typeReader;
    }

    public FunctionDecl parseFunctionDecl() {
        state.enter("functionDecl");
        Token startToken = state.peek();
        boolean external = state.match(TokenType.EXTERN);
        if (external) {
            startToken = state.previous();
        }
        Parser.ParsedNamedType declaration = typeReader.parseNamedType(
                "期望函数返回类型",
                "期望函数名"
        );
        if (declaration == null) {
            return null;
        }
        if (!(declaration.type() instanceof MiniType.FunctionType functionType)) {
            state.report(state.peek(), "顶层声明必须是函数");
            return null;
        }
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
            body = statementManager.parseBlock();
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
                SourceRange.span(startToken.range(), endRange)
        );
        state.build(functionDecl, "FunctionDecl " + functionDecl.name(), functionDecl.range());
        state.exit("functionDecl", functionDecl.range());
        return functionDecl;
    }

    public StructDecl parseStructDecl() {
        state.enter("structDecl");
        Token startToken = state.consume(TokenType.STRUCT, "期望 struct");
        Token nameToken = state.consume(TokenType.IDENTIFIER, "期望结构体名");
        state.consume(TokenType.LEFT_BRACE, "期望 '{'");
        ArrayList<StructField> fields = new ArrayList<>();
        while (!state.check(TokenType.RIGHT_BRACE) && !state.isAtEnd()) {
            StructField field = parseStructField();
            if (field != null) {
                fields.add(field);
            } else {
                state.synchronizeStatement();
            }
        }
        state.consume(TokenType.RIGHT_BRACE, "期望 '}'");
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (startToken == null || nameToken == null || semicolonToken == null) {
            return null;
        }
        StructDecl structDecl = new StructDecl(
                nameToken.lexeme(),
                fields,
                SourceRange.span(startToken.range(), semicolonToken.range())
        );
        state.build(structDecl, "StructDecl " + structDecl.name(), structDecl.range());
        state.exit("structDecl", structDecl.range());
        return structDecl;
    }

    private StructField parseStructField() {
        Parser.ParsedNamedType declaration = typeReader.parseNamedType("期望字段类型", "期望字段名");
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (declaration == null || semicolonToken == null) {
            return null;
        }
        return new StructField(
                declaration.name(),
                declaration.type(),
                SourceRange.span(declaration.range(), semicolonToken.range())
        );
    }
}
