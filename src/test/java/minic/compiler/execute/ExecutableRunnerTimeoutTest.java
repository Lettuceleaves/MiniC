package minic.compiler.execute;

import minic.compiler.SourceFile;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class ExecutableRunnerTimeoutTest {
    @Test
    void terminatesAProgramAndReportsADiagnosticWhenTheWallClockLimitExpires() {
        SourceFile sourceFile = new SourceFile("timeout.mc", "int main() { while (1) {} return 0; }");
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);
        session.compilerApi().run();
        assertTrue(session.linker().succeeded(), () -> session.linker().diagnostics().toString());

        var artifact = session.linker().result().executableArtifactOptional().orElseThrow();
        var result = new ExecutableRunner(Duration.ofMillis(200)).run(sourceFile, artifact, "");

        assertFalse(result.diagnostics().isEmpty());
        assertEquals("RUN002", result.diagnostics().getFirst().code());
        assertTrue(result.exitCodeOptional().isEmpty());
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        boolean rejected = false;
        try {
            new ExecutableRunner(Duration.ZERO);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }
}
