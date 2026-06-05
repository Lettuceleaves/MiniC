package minic.compiler.lexer;

import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LexerRegressionTest {
    @Test
    void lexesRepresentativeTokensCommentsLiteralsOperatorsAndStrings() {
        SourceFile source = new SourceFile("tokens.mc", """
                extern int printf(char *fmt, ...);
                struct Point { int x; int y; };
                int main() {
                    // comments are ignored
                    bool ok = true && false || NULL == NULL;
                    long n = 123L;
                    float f = 1.25f;
                    double d = 2.5;
                    char c = '\\n';
                    char *s = "hi";
                    int i = 0;
                    i++;
                    i += 2;
                    return point.x >= 7 ? i : 0;
                }
                """);

        LexResult result = new Lexer(source).lex();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(Token::kind)
                .contains(
                        TokenKind.EXTERN,
                        TokenKind.ELLIPSIS,
                        TokenKind.STRUCT,
                        TokenKind.BOOL,
                        TokenKind.BOOL_LITERAL,
                        TokenKind.NULL_LITERAL,
                        TokenKind.LONG_LITERAL,
                        TokenKind.FLOAT_LITERAL,
                        TokenKind.DOUBLE_LITERAL,
                        TokenKind.CHAR_LITERAL,
                        TokenKind.STRING_LITERAL,
                        TokenKind.PLUS_PLUS,
                        TokenKind.PLUS_EQUAL,
                        TokenKind.GREATER_EQUAL,
                        TokenKind.QUESTION,
                        TokenKind.COLON,
                        TokenKind.EOF
                )
                .doesNotContain(TokenKind.SLASH);
    }

    @Test
    void reportsRecoverableLexicalDiagnosticsWithoutThrowing() {
        SourceFile source = new SourceFile("bad.mc", """
                int main() {
                    return 2147483648 + @ + ..;
                }
                """);

        LexResult result = new Lexer(source).lex();

        assertThat(result.tokens()).extracting(Token::kind).contains(TokenKind.INT, TokenKind.RETURN, TokenKind.EOF);
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .contains("LEX001", "LEX005");
        assertThat(result.diagnostics()).hasSizeGreaterThanOrEqualTo(3);
    }
}
