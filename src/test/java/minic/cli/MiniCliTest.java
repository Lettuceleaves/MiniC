package minic.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCliTest {
    @TempDir
    Path tempDir;

    @Test
    void printsDiagnosticsForInvalidProgram() throws Exception {
        Path sourcePath = tempDir.resolve("bad.mc");
        Files.writeString(sourcePath, "int main() { return missing; }");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new MiniCli(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)
        ).run(new String[]{"compile", sourcePath.toString(), "--out-dir", tempDir.resolve("out").toString()});

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("SEM001", "未解析变量：missing");
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void writesAssemblyAndReportsToolDiagnosticWhenMsvcToolsAreUnavailable() throws Exception {
        Path sourcePath = tempDir.resolve("main.mc");
        Path outputDirectory = tempDir.resolve("out");
        Files.writeString(sourcePath, "int main() { return 1; }");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new MiniCli(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)
        ).run(new String[]{
                "compile",
                sourcePath.toString(),
                "--out-dir",
                outputDirectory.toString(),
                "--emit-asm",
                "--ml64",
                "missing-ml64-for-test",
                "--link",
                "missing-link-for-test"
        });

        assertThat(exitCode).isEqualTo(1);
        assertThat(outputDirectory.resolve("main.asm")).exists();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("assembly=");
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("TOOL001");
    }
}
