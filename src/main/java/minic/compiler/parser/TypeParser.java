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
        Token startToken = state.consume(TokenKind.INT, expectedMessage);
        if (startToken == null) {
            return null;
        }
        MiniType type = MiniType.INT;
        Token endToken = startToken;
        while (state.match(TokenKind.STAR)) {
            endToken = state.previous();
            type = type.pointerTo();
        }
        return new ParsedType(
                type,
                startToken,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endToken.range().endOffset()
                )
        );
    }
}
