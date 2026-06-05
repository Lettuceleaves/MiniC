package minic.compiler.parser;

import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParserRegressionTest {
    @Test
    void parsesRepresentativeDeclarationsControlFlowAndExpressions() {
        ParseResult result = parse("""
                extern int printf(char *fmt, ...);
                struct Node { int value; struct Node *next; };
                int apply(int (*fn)(int), int value) { return fn(value); }
                int inc(int value) { return value + 1; }
                int main() {
                    int x = 0;
                    do { x++; } while (x < 2);
                    for (int i = 0; i < 3; i++) { x += i; }
                    switch (x) {
                        case 1: return apply(inc, x);
                        default: return x ? x : 0;
                    }
                }
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.program().structs()).extracting("name").containsExactly("Node");
        assertThat(result.program().functions())
                .extracting(function -> function.name())
                .contains("printf", "apply", "inc", "main");
    }

    @Test
    void reportsRepresentativeSyntaxErrorsAndRecovers() {
        List<String> invalidSources = List.of(
                "int main( { return 0; }",
                "extern int printf(..., int value);",
                "int main() { if (1) { return 1; }"
        );

        for (String invalidSource : invalidSources) {
            ParseResult result = parse(invalidSource);
            assertThat(result.diagnostics()).as(invalidSource).isNotEmpty();
            assertThat(result.program()).as(invalidSource).isNotNull();
        }
    }

    private static ParseResult parse(String source) {
        LexResult lexResult = new Lexer(new SourceFile("parser.mc", source)).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        return new Parser(lexResult.tokens()).parse();
    }
}
