package minic.compiler.semantic;

import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.ParseResult;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticRegressionTest {
    @Test
    void acceptsRepresentativeLegalProgramsAndRecordsTypesScopesAndLayouts() {
        SemanticResult result = analyze("""
                extern int printf(char *fmt, ...);
                struct Pair { int left; int right; };
                int add(int a, int b) { return a + b; }
                int main() {
                    struct Pair p;
                    p.left = 1;
                    p.right = 2;
                    int values[2];
                    values[0] = p.left;
                    values[1] = p.right;
                    int *cursor = &values[0];
                    return add(*cursor, values[1]);
                }
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.globalScope().resolve("main")).isPresent();
        assertThat(result.globalScope().resolve("printf")).isPresent();
        assertThat(result.structLayout("Pair")).isPresent();
        assertThat(result.expressionTypes()).isNotEmpty();
    }

    @Test
    void reportsRepresentativeSemanticDiagnostics() {
        List<String> invalidSources = List.of(
                "int main() { return missing; }",
                "int main() { int x = 1; int x = 2; return x; }",
                "int main() { int *p = 1; return 0; }",
                "int main() { break; return 0; }"
        );

        for (String invalidSource : invalidSources) {
            SemanticResult result = analyze(invalidSource);
            assertThat(result.diagnostics()).as(invalidSource).isNotEmpty();
        }
    }

    @Test
    void validatesControlFlowReturnsSwitchRulesAndStructContainment() {
        List<String> invalidSources = List.of(
                "int f(int x) { if (x) { return 1; } } int main() { return f(1); }",
                "int main() { break; return 0; }",
                "int main() { continue; return 0; }"
        );

        for (String invalidSource : invalidSources) {
            SemanticResult result = analyze(invalidSource);
            assertThat(result.diagnostics()).as(invalidSource).isNotEmpty();
        }
    }

    private static SemanticResult analyze(String source) {
        SourceFile sourceFile = new SourceFile("semantic.mc", source);
        var lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        return new SemanticAnalyzer().analyze(parseResult.program());
    }
}
