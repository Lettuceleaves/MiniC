package minic.compiler.parser.manager;

import minic.compiler.parser.Parser;

import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Parameter;
import minic.compiler.parser.node.Declaration.StructDecl;
import minic.compiler.parser.node.Declaration.StructField;
import minic.compiler.parser.node.Declaration.EnumDecl;
import minic.compiler.parser.node.Declaration.Enumerator;
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
    private final java.util.Map<String, Long> enumConstants;

    public DeclarationManager(Parser.Context state, StatementManager statementManager, Parser.TypeReader typeReader) {
        this(state, statementManager, typeReader, new java.util.LinkedHashMap<>());
    }

    public DeclarationManager(Parser.Context state, StatementManager statementManager, Parser.TypeReader typeReader,
                              java.util.Map<String, Long> enumConstants) {
        this.state = state;
        this.statementManager = statementManager;
        this.typeReader = typeReader;
        this.enumConstants = enumConstants;
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
                boolean negative = state.match(TokenType.MINUS);
                Token literal = state.peek();
                if (state.match(TokenType.INTEGER_LITERAL)) {
                    Object raw = literal.literalValue();
                    value = raw instanceof Integer integer ? integer.longValue()
                            : raw instanceof minic.compiler.lexer.token.Token.IntegerLiteralValue integer ? integer.value() : 0;
                    if (negative) value = -value;
                } else if (state.match(TokenType.IDENTIFIER) && enumConstants.containsKey(literal.lexeme())) {
                    value = enumConstants.get(literal.lexeme());
                    if (negative) value = -value;
                } else {
                    state.report(literal, "枚举值必须是已知整数常量");
                }
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
        boolean union = state.check(TokenType.UNION);
        Token startToken = state.advance();
        Token nameToken = state.consume(TokenType.IDENTIFIER, "期望结构体名");
        if (state.match(TokenType.SEMICOLON)) {
            Token end = state.previous();
            if (startToken == null || nameToken == null) return null;
            String internalName = union ? unionName(nameToken.lexeme()) : nameToken.lexeme();
            StructDecl forward = new StructDecl(internalName, java.util.List.of(), false, union,
                    SourceRange.span(startToken.range(), end.range()));
            state.build(forward, "StructForwardDecl " + forward.name(), forward.range());
            state.exit("structDecl", forward.range());
            return forward;
        }
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
                union ? unionName(nameToken.lexeme()) : nameToken.lexeme(),
                fields,
                true,
                union,
                SourceRange.span(startToken.range(), semicolonToken.range())
        );
        state.build(structDecl, "StructDecl " + structDecl.name(), structDecl.range());
        state.exit("structDecl", structDecl.range());
        return structDecl;
    }

    private static String unionName(String sourceName) {
        return "$union$" + sourceName;
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
