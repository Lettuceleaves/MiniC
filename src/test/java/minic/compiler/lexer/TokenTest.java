package minic.compiler.lexer;

import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TokenTest {
    @Test
    void storesTokenFields() {
        SourceFile sourceFile = new SourceFile("token.mc", "123");
        SourceRange range = new SourceRange(sourceFile, 0, 3);

        Token token = new Token(TokenKind.INTEGER_LITERAL, "123", range, 123);

        assertThat(token.kind()).isEqualTo(TokenKind.INTEGER_LITERAL);
        assertThat(token.lexeme()).isEqualTo("123");
        assertThat(token.range()).isSameAs(range);
        assertThat(token.literalValue()).isEqualTo(123);
        assertThat(token.literalValueOptional()).contains(123);
    }

    @Test
    void supportsTokensWithoutLiteralValue() {
        SourceFile sourceFile = new SourceFile("identifier.mc", "main");
        SourceRange range = new SourceRange(sourceFile, 0, 4);

        Token token = new Token(TokenKind.IDENTIFIER, "main", range);

        assertThat(token.literalValue()).isNull();
        assertThat(token.literalValueOptional()).isEmpty();
    }

    @Test
    void tokenKindsCoverMiniCV01() {
        assertThat(Arrays.asList(TokenKind.values()))
                .contains(
                        TokenKind.EOF,
                        TokenKind.IDENTIFIER,
                        TokenKind.INTEGER_LITERAL,
                        TokenKind.INT,
                        TokenKind.EXTERN,
                        TokenKind.STRUCT,
                        TokenKind.RETURN,
                        TokenKind.IF,
                        TokenKind.ELSE,
                        TokenKind.WHILE,
                        TokenKind.FOR,
                        TokenKind.BREAK,
                        TokenKind.CONTINUE,
                        TokenKind.PLUS,
                        TokenKind.MINUS,
                        TokenKind.STAR,
                        TokenKind.SLASH,
                        TokenKind.EQUAL,
                        TokenKind.EQUAL_EQUAL,
                        TokenKind.BANG_EQUAL,
                        TokenKind.LESS,
                        TokenKind.LESS_EQUAL,
                        TokenKind.GREATER,
                        TokenKind.GREATER_EQUAL,
                        TokenKind.LEFT_PAREN,
                        TokenKind.RIGHT_PAREN,
                        TokenKind.LEFT_BRACE,
                        TokenKind.RIGHT_BRACE,
                        TokenKind.SEMICOLON,
                        TokenKind.COMMA
                );
    }
}
