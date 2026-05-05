package minic.compiler.parser;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.decl.StructDecl;
import minic.compiler.ast.decl.StructField;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;

final class DeclarationParser {
    private final ParserState state;
    private final StatementParser statementParser;
    private final TypeParser typeParser;

    DeclarationParser(ParserState state, StatementParser statementParser) {
        this.state = state;
        this.statementParser = statementParser;
        typeParser = new TypeParser(state);
    }

    FunctionDecl parseFunctionDecl() {
        state.enter("functionDecl");
        Token startToken = state.peek();
        boolean external = state.match(TokenKind.EXTERN);
        if (external) {
            startToken = state.previous();
        }
        ParsedType returnType = typeParser.parseType("期望函数声明以 int 开始");
        if (returnType == null) {
            return null;
        }
        if (state.check(TokenKind.LEFT_PAREN)) {
            rejectFunctionPointerReturnType();
            return null;
        }

        Token nameToken = state.consume(TokenKind.IDENTIFIER, "期望函数名");
        state.consume(TokenKind.LEFT_PAREN, "期望 '('");
        List<Parameter> parameters = parseParameters();
        state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        Token semicolonToken = null;
        BlockStmt body = null;
        if (state.match(TokenKind.SEMICOLON)) {
            semicolonToken = state.previous();
        } else {
            body = statementParser.parseBlock();
        }

        if (nameToken == null || (body == null && semicolonToken == null)) {
            return null;
        }
        int endOffset = body != null ? body.range().endOffset() : semicolonToken.range().endOffset();
            FunctionDecl functionDecl = new FunctionDecl(
                nameToken.lexeme(),
                returnType.type(),
                parameters,
                body,
                external,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endOffset
                )
        );
        state.build(functionDecl, "FunctionDecl " + functionDecl.name(), functionDecl.range());
        state.exit("functionDecl", functionDecl.range());
        return functionDecl;
    }

    StructDecl parseStructDecl() {
        state.enter("structDecl");
        Token startToken = state.consume(TokenKind.STRUCT, "期望 struct");
        Token nameToken = state.consume(TokenKind.IDENTIFIER, "期望结构体名");
        state.consume(TokenKind.LEFT_BRACE, "期望 '{'");
        ArrayList<StructField> fields = new ArrayList<>();
        while (!state.check(TokenKind.RIGHT_BRACE) && !state.isAtEnd()) {
            StructField field = parseStructField();
            if (field != null) {
                fields.add(field);
            } else {
                state.synchronizeStatement();
            }
        }
        state.consume(TokenKind.RIGHT_BRACE, "期望 '}'");
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");
        if (startToken == null || nameToken == null || semicolonToken == null) {
            return null;
        }
        StructDecl structDecl = new StructDecl(
                nameToken.lexeme(),
                fields,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
        state.build(structDecl, "StructDecl " + structDecl.name(), structDecl.range());
        state.exit("structDecl", structDecl.range());
        return structDecl;
    }

    private StructField parseStructField() {
        ParsedType type = typeParser.parseType("期望字段类型");
        ParsedNamedType functionPointer = null;
        Token nameToken = null;
        MiniType declaredType = null;
        SourceRange declarationRange = null;
        if (type != null && state.check(TokenKind.LEFT_PAREN)) {
            functionPointer = typeParser.parseFunctionPointerDeclarator(type, "期望字段名");
            if (functionPointer != null) {
                declaredType = functionPointer.type();
                declarationRange = functionPointer.range();
            }
        } else {
            nameToken = state.consume(TokenKind.IDENTIFIER, "期望字段名");
            declaredType = type != null ? parseArraySuffix(type.type()) : null;
        }
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");
        if (type == null || (nameToken == null && functionPointer == null) || semicolonToken == null) {
            return null;
        }
        String name = functionPointer != null ? functionPointer.name() : nameToken.lexeme();
        SourceRange range = declarationRange != null
                ? new SourceRange(
                        declarationRange.sourceFile(),
                        declarationRange.startOffset(),
                        semicolonToken.range().endOffset()
                )
                : new SourceRange(
                        type.range().sourceFile(),
                        type.range().startOffset(),
                        semicolonToken.range().endOffset()
                );
        return new StructField(
                name,
                declaredType,
                range
        );
    }

    private MiniType parseArraySuffix(MiniType baseType) {
        if (!state.match(TokenKind.LEFT_BRACKET)) {
            return baseType;
        }
        Token lengthToken = state.consume(TokenKind.INTEGER_LITERAL, "期望数组长度");
        state.consume(TokenKind.RIGHT_BRACKET, "期望 ']'");
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

    private List<Parameter> parseParameters() {
        ArrayList<Parameter> parameters = new ArrayList<>();
        if (state.check(TokenKind.RIGHT_PAREN)) {
            return parameters;
        }

        do {
            ParsedType type = typeParser.parseType("期望参数类型 int");
            if (type != null && state.check(TokenKind.LEFT_PAREN)) {
                ParsedNamedType functionPointer = typeParser.parseFunctionPointerDeclarator(type, "期望参数名");
                if (functionPointer != null) {
                    parameters.add(new Parameter(functionPointer.name(), functionPointer.type(), functionPointer.range()));
                }
            } else {
                Token nameToken = state.consume(TokenKind.IDENTIFIER, "期望参数名");
                if (type != null && nameToken != null) {
                    parameters.add(new Parameter(
                            nameToken.lexeme(),
                            type.type(),
                            new SourceRange(
                                    type.range().sourceFile(),
                                    type.range().startOffset(),
                                    nameToken.range().endOffset()
                            )
                    ));
                }
            }
        } while (state.match(TokenKind.COMMA));

        return parameters;
    }

    private void rejectFunctionPointerReturnType() {
        state.report(state.peek(), "暂不支持函数指针返回值");
        int parenthesisDepth = 0;
        while (!state.isAtEnd()) {
            if (state.check(TokenKind.LEFT_PAREN)) {
                parenthesisDepth++;
            } else if (state.check(TokenKind.RIGHT_PAREN)) {
                parenthesisDepth = Math.max(0, parenthesisDepth - 1);
            }
            if (parenthesisDepth == 0 && (state.check(TokenKind.LEFT_BRACE) || state.check(TokenKind.SEMICOLON))) {
                break;
            }
            state.advance();
        }
    }
}
