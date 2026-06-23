package minic;

import minic.cli.MiniCli;
import minic.color.ThemeCssGenerator;
import minic.compiler.pipeline.CompileResult;
import minic.compiler.pipeline.MiniCompiler;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.runtime.debug.visual.VisualAnnotationParser;
import minic.runtime.debug.visual.VisualKind;
import minic.runtime.debug.visual.typed.VisualSpec;
import minic.source.SourceFile;
import minic.source.SourceRange;
import minic.uilocal.MiniCSamplePrograms;
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

    @Test
    void visualSamplesIncludeBinaryTreeAndRedBlackTreePrograms() throws Exception {
        Path binaryTree = Path.of("samples", "visual_binary_tree.mc");
        Path redBlackTree = Path.of("samples", "visual_red_black_tree.mc");

        assertVisualSampleCompilesAndDeclaresKind(binaryTree, VisualKind.BINARY_TREE);
        assertVisualSampleCompilesAndDeclaresKind(redBlackTree, VisualKind.BINARY_TREE);
        assertThat(Files.readString(redBlackTree)).contains("rotateLeft", "rotateRight", "color");
    }

    private static void assertVisualSampleCompilesAndDeclaresKind(Path path, VisualKind kind) throws Exception {
        assertThat(path).exists();
        String sourceText = Files.readString(path);
        SourceFile source = new SourceFile(path.getFileName().toString(), sourceText);

        assertThat(new VisualAnnotationParser().parse(source).specs())
                .extracting(VisualSpec::kind)
                .contains(kind);

        CompileResult result = new MiniCompiler().compile(source);
        assertThat(result.diagnostics()).as(path.toString()).isEmpty();
        assertThat(result.irModuleOptional()).as(path.toString()).isPresent();
    }
}
