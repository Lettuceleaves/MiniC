package minic.compiler.parser;

import minic.compiler.CompilerApi;
import minic.compiler.link.Linker;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CastAndCommaExpressionTest {
    @Test
    void parsesAndExecutesCastsAndCommaExpressionsWithoutConsumingArgumentSeparators() {
        String source = """
                int difference(int left, int right) { return left - right; }
                int main(void) {
                    unsigned long long wide = 0x100000002ULL;
                    int narrowed = (int)wide;
                    int left = 0;
                    int right = 0;
                    int result = (left = 4, right = 1, difference((left, left + 1), right));
                    return narrowed == 2 && result == 4 ? 0 : 1;
                }
                """;
        SourceFile sourceFile = new SourceFile("cast-comma.mc", source);
        CompilerApi session = new CompilerApi(sourceFile);
        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> "parse=" + session.stage(Parser.class).errors()
                + ", semantic=" + session.stage(SemanticAnalyzer.class).errors()
                + ", link=" + session.stage(Linker.class).errors());
        var execution = new ExecutableRunner().run(
                sourceFile, session.stage(Linker.class).result().executableArtifactOptional().orElseThrow());
        assertEquals(0, execution.exitCode());
    }
}
