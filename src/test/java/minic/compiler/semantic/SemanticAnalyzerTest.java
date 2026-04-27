package minic.compiler.semantic;

import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticAnalyzerTest {
    @Test
    void reportsDuplicateFunctions() {
        SemanticResult result = analyze("int main() {} int main() {}");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("重复函数定义：main");
    }

    @Test
    void reportsDuplicateLocalVariables() {
        SemanticResult result = analyze("int main() { int x; int x; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("重复局部变量定义：x");
    }

    @Test
    void reportsUnresolvedVariables() {
        SemanticResult result = analyze("int main() { return x; }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未解析变量：x");
    }

    @Test
    void reportsUnresolvedFunctionCalls() {
        SemanticResult result = analyze("int main() { missing(); }");

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未解析函数调用：missing");
    }

    @Test
    void resolvesFunctionsParametersAndLocals() {
        SemanticResult result = analyze("""
                int id(int x) { return x; }
                int main() { int y = id(1); y = y + 1; }
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.globalScope().resolve("id")).isPresent();
        assertThat(result.globalScope().resolve("main")).isPresent();
    }

    @Test
    void allowsNestedBlockToShadowOuterVariable() {
        SemanticResult result = analyze("int main() { int x; { int x; } }");

        assertThat(result.diagnostics()).isEmpty();
    }

    private SemanticResult analyze(String source) {
        SourceFile sourceFile = new SourceFile("semantic.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        return new SemanticAnalyzer().analyze(parseResult.program());
    }
}
