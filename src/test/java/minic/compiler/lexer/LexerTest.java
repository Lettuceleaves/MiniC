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
        SourceFile sourceFile = new SourceFile("operators.mc", "+-*/&=(){}[];,<>");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.PLUS,
                        TokenKind.MINUS,
                        TokenKind.STAR,
                        TokenKind.SLASH,
                        TokenKind.AMPERSAND,
                        TokenKind.EQUAL,
                        TokenKind.LEFT_PAREN,
                        TokenKind.RIGHT_PAREN,
                        TokenKind.LEFT_BRACE,
                        TokenKind.RIGHT_BRACE,
                        TokenKind.LEFT_BRACKET,
                        TokenKind.RIGHT_BRACKET,
                        TokenKind.SEMICOLON,
                        TokenKind.COMMA,
                        TokenKind.LESS,
                        TokenKind.GREATER,
                        TokenKind.EOF
                );
        assertThat(result.tokens())
                .extracting(Token::lexeme)
                .containsExactly("+", "-", "*", "/", "&", "=", "(", ")", "{", "}", "[", "]", ";", ",", "<", ">", "");
    }

    @Test
    void lexesComparisonTokens() {
        SourceFile sourceFile = new SourceFile("comparison.mc", "== != <= >= < >");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.EQUAL_EQUAL,
                        TokenKind.BANG_EQUAL,
                        TokenKind.LESS_EQUAL,
                        TokenKind.GREATER_EQUAL,
                        TokenKind.LESS,
                        TokenKind.GREATER,
                        TokenKind.EOF
                );
        assertThat(result.tokens())
                .extracting(Token::lexeme)
                .containsExactly("==", "!=", "<=", ">=", "<", ">", "");
    }

    @Test
    void lexesStructFieldAccessTokens() {
        SourceFile sourceFile = new SourceFile("field.mc", ". -> -");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(TokenKind.DOT, TokenKind.ARROW, TokenKind.MINUS, TokenKind.EOF);
        assertThat(result.tokens())
                .extracting(Token::lexeme)
                .containsExactly(".", "->", "-", "");
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
        SourceFile sourceFile = new SourceFile(
                "keywords.mc",
                "int struct return if else while for integer structValue returnValue ifValue elseValue whileValue forValue"
        );

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.INT,
                        TokenKind.STRUCT,
                        TokenKind.RETURN,
                        TokenKind.IF,
                        TokenKind.ELSE,
                        TokenKind.WHILE,
                        TokenKind.FOR,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.EOF
                );
    }

    @Test
    void lexesLoopControlKeywordsSeparatelyFromIdentifiers() {
        SourceFile sourceFile = new SourceFile("loop-control.mc", "break continue breakValue continueValue");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.BREAK,
                        TokenKind.CONTINUE,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.EOF
                );
    }

    @Test
    void lexesExternKeywordSeparatelyFromIdentifiers() {
        SourceFile sourceFile = new SourceFile("extern.mc", "extern external externValue");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.EXTERN,
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

    @Test
    void lexesIntegerLiterals() {
        SourceFile sourceFile = new SourceFile("integers.mc", "0 7 12345");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.INTEGER_LITERAL,
                        TokenKind.INTEGER_LITERAL,
                        TokenKind.INTEGER_LITERAL,
                        TokenKind.EOF
                );
        assertThat(result.tokens())
                .extracting(Token::lexeme)
                .containsExactly("0", "7", "12345", "");
        assertThat(result.tokens())
                .extracting(Token::literalValue)
                .containsExactly(0, 7, 12345, null);
    }

    @Test
    void lexesStringLiteralsWithEscapes() {
        SourceFile sourceFile = new SourceFile("strings.mc", "\"hello\\n\\\"MiniC\\\"\"");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(TokenKind.STRING_LITERAL, TokenKind.EOF);
        assertThat(result.tokens().getFirst().lexeme()).isEqualTo("\"hello\\n\\\"MiniC\\\"\"");
        assertThat(result.tokens().getFirst().literalValue()).isEqualTo("hello\n\"MiniC\"");
        assertThat(result.tokens().getFirst().range()).isEqualTo(new SourceRange(sourceFile, 0, 18));
    }

    @Test
    void reportsUnterminatedStringLiteral() {
        SourceFile sourceFile = new SourceFile("bad-string.mc", "\"hello");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(TokenKind.EOF);
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst().code()).isEqualTo("LEX002");
        assertThat(result.diagnostics().getFirst().range()).isEqualTo(new SourceRange(sourceFile, 0, 6));
    }

    @Test
    void keepsIntegerLiteralRanges() {
        SourceFile sourceFile = new SourceFile("integer-ranges.mc", "\n123 + 0");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.tokens().get(0).range()).isEqualTo(new SourceRange(sourceFile, 1, 4));
        assertThat(result.tokens().get(2).range()).isEqualTo(new SourceRange(sourceFile, 7, 8));
    }

    @Test
    void skipsLineCommentsUntilNewline() {
        SourceFile sourceFile = new SourceFile("comment.mc", "int // ignored + 123\nreturn");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(TokenKind.INT, TokenKind.RETURN, TokenKind.EOF);
        assertThat(result.tokens().get(1).range()).isEqualTo(new SourceRange(sourceFile, 21, 27));
    }

    @Test
    void skipsLineCommentsUntilEof() {
        SourceFile sourceFile = new SourceFile("comment-eof.mc", "int // ignored");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(TokenKind.INT, TokenKind.EOF);
        assertThat(result.tokens().get(1).range()).isEqualTo(new SourceRange(sourceFile, 14, 14));
    }

    @Test
    void reportsInvalidCharactersAndContinuesLexing() {
        SourceFile sourceFile = new SourceFile("invalid.mc", "int @ return");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(TokenKind.INT, TokenKind.RETURN, TokenKind.EOF);
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst().code()).isEqualTo("LEX001");
        assertThat(result.diagnostics().getFirst().severity().name()).isEqualTo("ERROR");
        assertThat(result.diagnostics().getFirst().range()).isEqualTo(new SourceRange(sourceFile, 4, 5));
    }
}
