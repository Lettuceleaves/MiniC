package minic.cli;

import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.compiler.pipeline.CompileResult;
import minic.compiler.toolchain.ExecutableArtifact;
import minic.compiler.toolchain.ToolchainResult;
import minic.runtime.execution.ExecutionResult;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    @Test
    void compileRunPrintsCapturedExecutionResult() throws Exception {
        Path sourcePath = tempDir.resolve("main.mc");
        Path outputDirectory = tempDir.resolve("out");
        Files.writeString(sourcePath, "int main() { return 7; }");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        SourceFile capturedSourceFile = new SourceFile(sourcePath.toString(), Files.readString(sourcePath));
        CompileResult compileResult = new CompileResult(
                new LexResult(List.of(new Token(
                        TokenKind.EOF,
                        "",
                        new SourceRange(capturedSourceFile, capturedSourceFile.content().length(), capturedSourceFile.content().length())
                )), List.of()),
                null,
                null,
                null,
                null,
                new ToolchainResult(
                        outputDirectory.resolve("main.asm"),
                        outputDirectory.resolve("main.obj"),
                        new ExecutableArtifact(outputDirectory.resolve("main.exe")),
                        List.of()
                ),
                new ExecutionResult("hello\n", "warning\n", 7, List.of())
        );

        int exitCode = new MiniCli(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                (sourceFile, options) -> compileResult
        ).run(new String[]{
                "compile-run",
                sourcePath.toString(),
                "--out-dir",
                outputDirectory.toString()
        });

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains(
                "executable=",
                "run.stdout=hello",
                "run.stderr=warning",
                "run.exitCode=7"
        );
        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
    }
}
