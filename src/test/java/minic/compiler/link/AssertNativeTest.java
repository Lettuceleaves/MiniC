package minic.compiler.link;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class AssertNativeTest {
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void failedAssertionUsesTheCrtDiagnosticAndTerminates() {
        SourceFile source = new SourceFile("assert-native.mc", "#include \"assert.mh\"\n"
                + "extern unsigned int minic_set_process_error_mode(unsigned int mode);\n"
                + "int main() {\n"
                + "    minic_set_process_error_mode(3U); assert(2 + 2 == 5); return 99;\n"
                + "}\n");
        CompileObservationSession session = CompileObservationSession.fromSource(source);
        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> session.semanticAnalyzer().diagnostics() + " "
                + session.linker().diagnostics());
        Map<String, Set<String>> imports = PeImportIntegrationTest.readImports(
                session.linker().peImage().orElseThrow().bytes()
        );
        assertEquals(Set.of("_assert"), imports.get("msvcrt.dll"));
        assertEquals(Set.of("ExitProcess", "SetErrorMode"), imports.get("KERNEL32.dll"));

        var execution = new ExecutableRunner(Duration.ofSeconds(3)).run(
                source,
                session.linker().result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(execution.diagnostics().isEmpty(), execution.diagnostics()::toString);
        assertEquals(3, execution.exitCode());
        assertTrue(execution.stderr().contains("2 + 2 == 5"), execution.stderr());
        assertTrue(execution.stderr().contains("assert-native.mc"), execution.stderr());
    }
}
