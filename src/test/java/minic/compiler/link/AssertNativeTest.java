package minic.compiler.link;

import minic.compiler.CompilerApi;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
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
        CompilerApi session = new CompilerApi(source);
        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> session.stage(SemanticAnalyzer.class).errors() + " "
                + session.stage(Linker.class).errors());
        Map<String, Set<String>> imports = PeImportIntegrationTest.readImports(
                session.stage(Linker.class).peImage().orElseThrow().bytes()
        );
        assertEquals(Set.of("_assert"), imports.get("msvcrt.dll"));
        assertEquals(Set.of("ExitProcess", "SetErrorMode"), imports.get("KERNEL32.dll"));

        var executionStage = new ExecutableRunner(Duration.ofSeconds(3));
        var execution = executionStage.run(
                source,
                session.stage(Linker.class).result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(executionStage.errors().isEmpty(), executionStage.errors()::toString);
        assertEquals(3, execution.exitCode());
        assertTrue(execution.stderr().contains("2 + 2 == 5"), execution.stderr());
        assertTrue(execution.stderr().contains("assert-native.mc"), execution.stderr());
    }
}
