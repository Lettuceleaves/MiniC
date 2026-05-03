package minic.compiler.lexer;

import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LexerTest {
    @Test
    void lexesRepresentativeTokensAndSkipsComments() {
        SourceFile sourceFile = new SourceFile(
                "tokens.mc",
                """
                        extern int printf(char *fmt);
                        struct Point { int x; int y; };
                        int main() {
                          // ignored operators: + - * /
                          struct Point point;
                          point.x = 1 + 2 * 3;
                          return point.x >= 7 != false;
                        }
                        """
        );

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .contains(
                        TokenKind.EXTERN,
                        TokenKind.CHAR,
                        TokenKind.STAR,
                        TokenKind.STRUCT,
                        TokenKind.DOT,
                        TokenKind.EQUAL,
                        TokenKind.PLUS,
                        TokenKind.STAR,
                        TokenKind.GREATER_EQUAL,
                        TokenKind.BANG_EQUAL,
                        TokenKind.BOOL_LITERAL,
                        TokenKind.EOF
                )
                .doesNotContain(TokenKind.SLASH);
        assertThat(result.tokens())
                .extracting(Token::lexeme)
                .contains("extern", "printf", "Point", ".", ">=", "!=", "false", "");
    }

    @Test
    void distinguishesKeywordsFromIdentifierBoundaries() {
        SourceFile sourceFile = new SourceFile(
                "keywords.mc",
                "bool char int long float double struct return if else while for break continue extern "
                        + "_main integer structValue returnValue ifValue elseValue whileValue forValue breakValue continueValue external"
        );

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.BOOL,
                        TokenKind.CHAR,
                        TokenKind.INT,
                        TokenKind.LONG,
                        TokenKind.FLOAT,
                        TokenKind.DOUBLE,
                        TokenKind.STRUCT,
                        TokenKind.RETURN,
                        TokenKind.IF,
                        TokenKind.ELSE,
                        TokenKind.WHILE,
                        TokenKind.FOR,
                        TokenKind.BREAK,
                        TokenKind.CONTINUE,
                        TokenKind.EXTERN,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.IDENTIFIER,
                        TokenKind.EOF
                );
        assertThat(result.tokens())
                .extracting(Token::lexeme)
                .contains("_main", "integer", "structValue", "breakValue", "external");
    }

    @Test
    void lexesExtendedLiterals() {
        SourceFile sourceFile = new SourceFile("extended-literals.mc", "true false NULL 'a' '\\n' 123L 1.25f 2.5");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(
                        TokenKind.BOOL_LITERAL,
                        TokenKind.BOOL_LITERAL,
                        TokenKind.NULL_LITERAL,
                        TokenKind.CHAR_LITERAL,
                        TokenKind.CHAR_LITERAL,
                        TokenKind.LONG_LITERAL,
                        TokenKind.FLOAT_LITERAL,
                        TokenKind.DOUBLE_LITERAL,
                        TokenKind.EOF
                );
        assertThat(result.tokens())
                .extracting(Token::literalValue)
                .containsExactly(true, false, null, 'a', '\n', 123L, 1.25f, 2.5d, null);
    }

    @Test
    void reportsNumericLiteralOverflowInsteadOfThrowing() {
        SourceFile sourceFile = new SourceFile(
                "overflow.mc",
                "2147483648 9223372036854775808L"
        );

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(TokenKind.EOF);
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("LEX005", "LEX005");
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("整数字面量超出范围", "long 字面量超出范围");
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
    }

    @Test
    void skipsLineCommentsUntilEofOrNewline() {
        SourceFile sourceFile = new SourceFile("comment.mc", "int // ignored + 123\nreturn // ignored until eof");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(TokenKind.INT, TokenKind.RETURN, TokenKind.EOF);
    }

    @Test
    void reportsRepresentativeLexicalDiagnosticsAndContinuesWhenPossible() {
        SourceFile sourceFile = new SourceFile("invalid.mc", "int @ return \"unterminated");

        LexResult result = new Lexer(sourceFile).lex();

        assertThat(result.tokens())
                .extracting(Token::kind)
                .containsExactly(TokenKind.INT, TokenKind.RETURN, TokenKind.EOF);
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("LEX001", "LEX002");
    }
}
