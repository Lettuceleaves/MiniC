package minic.compiler.parser;

import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

record ParsedType(MiniType type, Token startToken, SourceRange range) {
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

    private BaseType parseBaseType(String expectedMessage) {
        if (state.check(TokenKind.INT)) {
            Token token = state.advance();
            return new BaseType(MiniType.INT, token, token);
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
