package minic.compiler.lexer;

import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LexerTest {
    @Test
    void skipsWhitespaceAndEmitsEof() {
        SourceFile sourceFile = new SourceFile("empty.mc", " \t\r\n");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(TokenKind.EOF);
        assertThat(result.tokens().getFirst().range())
                .isEqualTo(new SourceRange(sourceFile, 4, 4));
    }

    @Test
    void lexesSingleCharacterTokens() {
        SourceFile sourceFile = new SourceFile("operators.mc", "+-*/=(){};,");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.PLUS,
                        TokenKind.MINUS,
                        TokenKind.STAR,
                        TokenKind.SLASH,
                        TokenKind.EQUAL,
                        TokenKind.LEFT_PAREN,
                        TokenKind.RIGHT_PAREN,
                        TokenKind.LEFT_BRACE,
                        TokenKind.RIGHT_BRACE,
                        TokenKind.SEMICOLON,
                        TokenKind.COMMA,
                        TokenKind.EOF
                );
        assertThat(result.tokens())
                .extracting(Token::lexeme)
                .containsExactly("+", "-", "*", "/", "=", "(", ")", "{", "}", ";", ",", "");
    }

    @Test
    void keepsCorrectTokenRangesAcrossWhitespace() {
        SourceFile sourceFile = new SourceFile("ranges.mc", " \n+ \t;");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.tokens().get(0).range()).isEqualTo(new SourceRange(sourceFile, 2, 3));
        assertThat(result.tokens().get(1).range()).isEqualTo(new SourceRange(sourceFile, 5, 6));
        assertThat(result.tokens().get(2).range()).isEqualTo(new SourceRange(sourceFile, 6, 6));
    }

    @Test
    void lexesIdentifiersWithUnderscoresAndDigitsAfterFirstCharacter() {
        SourceFile sourceFile = new SourceFile("identifiers.mc", "_main value2 snake_case");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.EOF
                );
        assertThat(result.tokens())
                .extracting(Token::lexeme)
                .containsExactly("_main", "value2", "snake_case", "");
    }

    @Test
    void lexesKeywordsSeparatelyFromIdentifiers() {
        SourceFile sourceFile = new SourceFile("keywords.mc", "int return integer returnValue");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.INT,
                        TokenKind.RETURN,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.EOF
                );
    }

    @Test
    void keepsIdentifierRanges() {
        SourceFile sourceFile = new SourceFile("identifier-ranges.mc", "  main\nreturn");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.tokens().get(0).range()).isEqualTo(new SourceRange(sourceFile, 2, 6));
        assertThat(result.tokens().get(1).range()).isEqualTo(new SourceRange(sourceFile, 7, 13));
        assertThat(result.tokens().get(2).range()).isEqualTo(new SourceRange(sourceFile, 13, 13));
    }
}
