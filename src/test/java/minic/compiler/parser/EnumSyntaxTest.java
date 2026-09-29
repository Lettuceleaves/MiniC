package minic.compiler.parser;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnumSyntaxTest {
    @Test
    void supportsExplicitImplicitAndTrailingCommaEnumerators() {
        String source = """
                enum Color { RED = 0x10, GREEN, BLUE = (GREEN << 1) + 2, };
                int main(void) {
                    enum Color color = GREEN;
                    return color == 17 && BLUE == 36 ? 0 : 1;
                }
                """;
        SourceFile sourceFile = new SourceFile("enum.mc", source);
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);
        session.compilerApi().run();
        assertTrue(session.linker().succeeded(), () -> "parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics());
        var result = new ExecutableRunner().run(
                sourceFile, session.linker().result().executableArtifactOptional().orElseThrow());
        assertEquals(0, result.exitCode());
    }
}
