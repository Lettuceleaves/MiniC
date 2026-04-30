package minic.runtime.execution;

import minic.compiler.toolchain.ExecutableArtifact;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutableRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void capturesStdoutStderrAndExitCode() throws Exception {
        Path script = tempDir.resolve("program.cmd");
        Files.writeString(script, """
                @echo off
                echo hello
                echo warning 1>&2
                exit /b 7
                """);

        ExecutionResult result = new ExecutableRunner().run(
                new SourceFile("program.mc", "int main() { return 7; }"),
                new ExecutableArtifact(script)
        );

        assertThat(result.stdout()).contains("hello");
        assertThat(result.stderr()).contains("warning");
        assertThat(result.exitCodeOptional()).hasValue(7);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void reportsDiagnosticWhenExecutableCannotStart() {
        ExecutionResult result = new ExecutableRunner().run(
                new SourceFile("missing.mc", "int main() { return 0; }"),
                new ExecutableArtifact(tempDir.resolve("missing.exe"))
        );

        assertThat(result.exitCodeOptional()).isEmpty();
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("RUN001");
    }
}
