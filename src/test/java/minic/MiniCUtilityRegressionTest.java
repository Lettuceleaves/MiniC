package minic;

import minic.cli.MiniCli;
import minic.color.ThemeCssGenerator;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;
import minic.ui.MiniCSamplePrograms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCUtilityRegressionTest {
    @TempDir
    Path tempDir;

    @Test
    void runsCliMainSourceSettingsDiagnosticsSamplesAndThemeSmoke() throws Exception {
        Path source = tempDir.resolve("bad.mc");
        Files.writeString(source, "int main() { return @; }");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new MiniCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8))
                .run(new String[]{"compile", source.toString(), "--out-dir", tempDir.toString(), "--show", "diagnostics"});

        assertThat(Main.name()).isEqualTo("MiniC");
        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("LEX001");
        assertThat(MiniCSamplePrograms.defaultSample().source()).contains("int main");
        assertThat(ThemeCssGenerator.generate()).contains(".workbench-root", ".mc-text-code-keyword");
    }

    @Test
    void mapsSourceOffsetsRangesAndReportsToolDiagnostics() {
        SourceFile source = new SourceFile("source.mc", "one\ntwo\n");
        SourceRange range = new SourceRange(source, 4, 7);
        Diagnostic diagnostic = new Diagnostic("T001", DiagnosticSeverity.ERROR, "message", range);

        assertThat(source.positionAt(4).line()).isEqualTo(2);
        assertThat(source.positionAt(4).column()).isEqualTo(1);
        assertThat(range.text()).isEqualTo("two");
        assertThat(range.startPosition().line()).isEqualTo(2);
        assertThat(diagnostic.code()).isEqualTo("T001");
    }
}
