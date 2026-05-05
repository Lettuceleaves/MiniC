package minic.compiler.parser;

import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.ArrayList;

record ParsedType(MiniType type, Token startToken, SourceRange range) {
}

record ParsedNamedType(String name, MiniType type, SourceRange range) {
}

final class TypeParser {
    private final ParserState state;

    TypeParser(ParserState state) {
        this.state = state;
    }

    ParsedType parseType(String expectedMessage) {
        BaseType baseType = parseBaseType(expectedMessage);
        if (baseType == null) {
            return null;
        }
        MiniType type = baseType.type();
        Token endToken = baseType.endToken();
        while (state.match(TokenKind.STAR)) {
            endToken = state.previous();
            type = type.pointerTo();
        }
        return new ParsedType(
                type,
                baseType.startToken(),
                new SourceRange(
                        baseType.startToken().range().sourceFile(),
                        baseType.startToken().range().startOffset(),
                        endToken.range().endOffset()
                )
        );
    }

    boolean canStartType() {
        return state.check(TokenKind.BOOL)
                || state.check(TokenKind.CHAR)
                || state.check(TokenKind.INT)
                || state.check(TokenKind.LONG)
                || state.check(TokenKind.FLOAT)
                || state.check(TokenKind.DOUBLE)
                || state.check(TokenKind.STRUCT);
    }

    ParsedNamedType parseFunctionPointerDeclarator(ParsedType returnType, String expectedNameMessage) {
        Token startToken = returnType.startToken();
        state.consume(TokenKind.LEFT_PAREN, "期望 '('");
        state.consume(TokenKind.STAR, "期望 '*'");
        Token nameToken = state.consume(TokenKind.IDENTIFIER, expectedNameMessage);
        state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        state.consume(TokenKind.LEFT_PAREN, "期望 '('");
        ArrayList<MiniType> parameterTypes = parseFunctionPointerParameterTypes();
        Token endToken = state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        if (nameToken == null || endToken == null) {
            return null;
        }
        return new ParsedNamedType(
                nameToken.lexeme(),
                MiniType.function(returnType.type(), parameterTypes).pointerTo(),
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endToken.range().endOffset()
                )
        );
    }

    private ArrayList<MiniType> parseFunctionPointerParameterTypes() {
        ArrayList<MiniType> parameterTypes = new ArrayList<>();
        if (state.check(TokenKind.RIGHT_PAREN)) {
            return parameterTypes;
        }
        do {
            ParsedType parameterType = parseType("期望函数指针参数类型");
            if (parameterType != null) {
                parameterTypes.add(parameterType.type());
                if (state.check(TokenKind.IDENTIFIER)) {
                    state.advance();
                }
            }
        } while (state.match(TokenKind.COMMA));
        return parameterTypes;
    }

    private BaseType parseBaseType(String expectedMessage) {
        if (state.check(TokenKind.BOOL)) {
            Token token = state.advance();
            return new BaseType(MiniType.BOOL, token, token);
        }
        if (state.check(TokenKind.CHAR)) {
            Token token = state.advance();
            return new BaseType(MiniType.CHAR, token, token);
        }
        if (state.check(TokenKind.INT)) {
            Token token = state.advance();
            return new BaseType(MiniType.INT, token, token);
        }
        if (state.check(TokenKind.LONG)) {
            Token token = state.advance();
            return new BaseType(MiniType.LONG, token, token);
        }
        if (state.check(TokenKind.FLOAT)) {
            Token token = state.advance();
            return new BaseType(MiniType.FLOAT, token, token);
        }
        if (state.check(TokenKind.DOUBLE)) {
            Token token = state.advance();
            return new BaseType(MiniType.DOUBLE, token, token);
        }
        if (state.check(TokenKind.STRUCT)) {
            return parseStructType();
        }
        state.report(state.peek(), expectedMessage);
        return null;
    }

    private BaseType parseStructType() {
        Token startToken = state.advance();
        Token nameToken = state.consume(TokenKind.IDENTIFIER, "期望结构体类型名");
        if (nameToken == null) {
            return null;
        }
        return new BaseType(MiniType.struct(nameToken.lexeme()), startToken, nameToken);
    }

    private record BaseType(MiniType type, Token startToken, Token endToken) {
    }
}
