package minic.compiler.execute;

import minic.compiler.CompilerApi;
import minic.compiler.link.Linker;
import minic.compiler.SourceFile;
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
        CompilerApi session = new CompilerApi(sourceFile);
        session.runThrough(session.stage(Linker.class));
        assertTrue(session.stage(Linker.class).succeeded(), () -> session.stage(Linker.class).errors().toString());

        var artifact = session.stage(Linker.class).result().executableArtifactOptional().orElseThrow();
        var resultStage = new ExecutableRunner(Duration.ofMillis(200));
        var result = resultStage.run(sourceFile, artifact, "");

        assertFalse(resultStage.errors().isEmpty());
        assertEquals("RUN002", resultStage.errors().getFirst().code());
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
