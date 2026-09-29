package minic.compiler.parser;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
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
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);
        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> "parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics()
                + ", link=" + session.linker().diagnostics());
        var execution = new ExecutableRunner().run(
                sourceFile, session.linker().result().executableArtifactOptional().orElseThrow());
        assertEquals(0, execution.exitCode());
    }
}
