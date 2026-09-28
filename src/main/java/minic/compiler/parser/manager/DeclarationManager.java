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
import java.util.List;

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
        Parser.ParsedType returnType = typeReader.parseType("期望函数声明以 int 开始");
        if (returnType == null) {
            return null;
        }
        if (state.check(TokenType.LEFT_PAREN)) {
            rejectFunctionPointerReturnType();
            return null;
        }

        Token nameToken = state.consume(TokenType.IDENTIFIER, "期望函数名");
        state.consume(TokenType.LEFT_PAREN, "期望 '('");
        ParameterList parameters = parseParameters();
        state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
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

        if (nameToken == null || (body == null && semicolonToken == null)) {
            return null;
        }
        SourceRange endRange = body != null ? body.range() : semicolonToken.range();
        FunctionDecl functionDecl = new FunctionDecl(
                nameToken.lexeme(),
                returnType.type(),
                parameters.parameters(),
                parameters.variadic(),
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
        Parser.ParsedType type = typeReader.parseType("期望字段类型");
        Parser.ParsedNamedType functionPointer = null;
        Token nameToken = null;
        MiniType declaredType = null;
        SourceRange declarationRange = null;
        if (type != null && state.check(TokenType.LEFT_PAREN)) {
            functionPointer = typeReader.parseFunctionPointerDeclarator(type, "期望字段名");
            if (functionPointer != null) {
                declaredType = functionPointer.type();
                declarationRange = functionPointer.range();
            }
        } else {
            nameToken = state.consume(TokenType.IDENTIFIER, "期望字段名");
            declaredType = type != null ? parseArraySuffix(type.type()) : null;
        }
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (type == null || (nameToken == null && functionPointer == null) || semicolonToken == null) {
            return null;
        }
        String name = functionPointer != null ? functionPointer.name() : nameToken.lexeme();
        SourceRange range = declarationRange != null
                ? SourceRange.span(declarationRange, semicolonToken.range())
                : SourceRange.span(type.range(), semicolonToken.range());
        return new StructField(
                name,
                declaredType,
                range
        );
    }

    private MiniType parseArraySuffix(MiniType baseType) {
        if (!state.match(TokenType.LEFT_BRACKET)) {
            return baseType;
        }
        Token lengthToken = state.consume(TokenType.INTEGER_LITERAL, "期望数组长度");
        state.consume(TokenType.RIGHT_BRACKET, "期望 ']'");
        if (lengthToken == null) {
            return baseType;
        }
        int length = (Integer) lengthToken.literalValue();
        if (length <= 0) {
            state.report(lengthToken, "数组长度必须大于 0");
            return baseType;
        }
        return baseType.arrayOf(length);
    }

    private ParameterList parseParameters() {
        ArrayList<Parameter> parameters = new ArrayList<>();
        if (state.check(TokenType.RIGHT_PAREN)) {
            return new ParameterList(parameters, false);
        }

        boolean variadic = false;
        do {
            if (state.match(TokenType.ELLIPSIS)) {
                variadic = true;
                if (!state.check(TokenType.RIGHT_PAREN)) {
                    state.report(state.peek(), "可变参数标记必须位于参数列表末尾");
                }
                break;
            }
            Parser.ParsedType type = typeReader.parseType("期望参数类型 int");
            if (type != null && state.check(TokenType.LEFT_PAREN)) {
                Parser.ParsedNamedType functionPointer = typeReader.parseFunctionPointerDeclarator(type, "期望参数名");
                if (functionPointer != null) {
                    parameters.add(new Parameter(functionPointer.name(), functionPointer.type(), functionPointer.range()));
                }
            } else {
                Token nameToken = state.consume(TokenType.IDENTIFIER, "期望参数名");
                if (type != null && nameToken != null) {
                    parameters.add(new Parameter(
                            nameToken.lexeme(),
                            type.type(),
                            SourceRange.span(type.range(), nameToken.range())
                    ));
                }
            }
        } while (state.match(TokenType.COMMA));

        return new ParameterList(parameters, variadic);
    }

    private void rejectFunctionPointerReturnType() {
        state.report(state.peek(), "暂不支持函数指针返回值");
        int parenthesisDepth = 0;
        while (!state.isAtEnd()) {
            if (state.check(TokenType.LEFT_PAREN)) {
                parenthesisDepth++;
            } else if (state.check(TokenType.RIGHT_PAREN)) {
                parenthesisDepth = Math.max(0, parenthesisDepth - 1);
            }
            if (parenthesisDepth == 0 && (state.check(TokenType.LEFT_BRACE) || state.check(TokenType.SEMICOLON))) {
                break;
            }
            state.advance();
        }
    }

    private record ParameterList(List<Parameter> parameters, boolean variadic) {
        private ParameterList {
            parameters = List.copyOf(parameters);
        }
    }
}
