package craken.ui.editor.realtime;

import craken.compiler.Diagnostic;
import craken.compiler.SourceFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
final class RealtimeSyntaxAnalyzerTest {
    @Test void missingSemicolonProducesAParserErrorWithRepairAdvice() {
        String source = "int main() { return 1 }";
        var analysis = RealtimeSyntaxAnalyzer.analyzeNow("live.mc", source, 1);

        assertFalse(analysis.diagnostics().isEmpty(), "broken syntax must publish a diagnostic");
        var diagnostic = analysis.diagnostics().getFirst();
        assertEquals(Diagnostic.Severity.ERROR, diagnostic.severity());
        assertTrue(diagnostic.code().startsWith("PAR"), "unexpected diagnostic code " + diagnostic.code());
        assertFalse(diagnostic.message().isBlank());
        assertFalse(diagnostic.solution().isBlank(), "correction advice must be present");
        var offsets = new SourceFile("live.mc", source);
        int start = offsets.offsetAt(diagnostic.range().startLine(), diagnostic.range().startByte());
        int end = offsets.offsetAt(diagnostic.range().endLine(), diagnostic.range().endByte());
        assertTrue(start >= 0 && start <= end && end <= source.length(), "range must stay inside the source");
    }

    @Test void unterminatedStringStopsBeforeTheParser() {
        String source = "int main(){ const char* s = \"abc; }";
        var analysis = RealtimeSyntaxAnalyzer.analyzeNow("live.mc", source, 2);

        assertFalse(analysis.diagnostics().isEmpty());
        assertTrue(analysis.diagnostics().stream().allMatch(d -> d.code().startsWith("LEX")),
                "a lexer failure must not produce later-stage diagnostics: " + analysis.diagnostics());
    }

    @Test void aValidProgramHasNoDiagnostics() {
        var analysis = RealtimeSyntaxAnalyzer.analyzeNow("live.mc", "int main(){ return 0; }", 3);
        assertEquals(List.of(), analysis.diagnostics());
    }

    @Test void semanticErrorsFollowACleanParse() {
        String source = "int main(){ return missing_name; }";
        var analysis = RealtimeSyntaxAnalyzer.analyzeNow("live.mc", source, 4);

        assertFalse(analysis.diagnostics().isEmpty());
        assertTrue(analysis.diagnostics().stream().allMatch(d -> d.severity() == Diagnostic.Severity.ERROR));
        assertTrue(analysis.diagnostics().stream().noneMatch(d -> d.code().startsWith("LEX") || d.code().startsWith("PAR")),
                "clean syntax must surface semantic diagnostics: " + analysis.diagnostics());
    }

    @Test void rapidEditsCoalesceToTheLatestText() throws Exception {
        var latest = new AtomicReference<RealtimeSyntaxAnalysis>();
        var newestDelivered = new CountDownLatch(1);
        try (var analyzer = new RealtimeSyntaxAnalyzer(analysis -> {
            latest.set(analysis);
            if (analysis.version() == 3) newestDelivered.countDown();
        })) {
            analyzer.submit("live.mc", "int main(){ return 1; }");
            analyzer.submit("live.mc", "int main(){ return 2; }");
            analyzer.submit("live.mc", "int main(){ return 3 }");
            assertTrue(newestDelivered.await(20, TimeUnit.SECONDS), "the newest text must be analyzed");
        }
        var delivered = latest.get();
        assertEquals(3, delivered.version());
        assertEquals("int main(){ return 3 }", delivered.sourceText());
        assertFalse(delivered.diagnostics().isEmpty());
    }

    @Test void abortBeforeTheFirstStepAbandonsTheAnalysis() {
        assertNull(RealtimeSyntaxAnalyzer.analyzeNow("live.mc", "int main(){ return 1 }", 5, () -> true));
    }

    @Test void abortAfterOneStepAbandonsTheAnalysis() {
        var checks = new AtomicInteger();
        var analysis = RealtimeSyntaxAnalyzer.analyzeNow("live.mc", "int main(){ return 1 }", 6,
                () -> checks.incrementAndGet() > 1);
        assertNull(analysis);
        assertTrue(checks.get() >= 2, "the step-wise path must consult the abort condition repeatedly");
    }

    @Test void aNewerEditAbandonsAnAnalysisThatAlreadyStarted() throws Exception {
        String big = ("int a=0;\n").repeat(20_000) + "int main(){ return 0; }";
        var delivered = new CopyOnWriteArrayList<RealtimeSyntaxAnalysis>();
        var started = new CountDownLatch(1);
        var newest = new CountDownLatch(1);
        try (var analyzer = new RealtimeSyntaxAnalyzer(analysis -> {
            delivered.add(analysis);
            if (analysis.version() == 2) newest.countDown();
        }, started::countDown)) {
            analyzer.submit("live.mc", big);
            assertTrue(started.await(20, TimeUnit.SECONDS), "the first analysis must have started");
            analyzer.submit("live.mc", "int main(){ return 1 }");
            assertTrue(newest.await(20, TimeUnit.SECONDS), "the newest edit must be analyzed");
        }
        assertTrue(delivered.stream().noneMatch(analysis -> analysis.version() == 1),
                "an abandoned in-flight analysis must not publish");
    }

    @Test void includedHeaderErrorsMapBackIntoTheEditedFile(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("bad.mh"), "int ok = 1;\nint ok2 = 2;\nint broken = ;\n");
        Path main = directory.resolve("live.mc");
        String source = "int a=0;\n#include \"bad.mh\"\nint main(){ return 0; }";
        var analysis = RealtimeSyntaxAnalyzer.analyzeNow(main.toString(), source, 7);

        assertFalse(analysis.diagnostics().isEmpty());
        var offsets = new SourceFile(main.toString(), source);
        for (var diagnostic : analysis.diagnostics()) {
            assertDoesNotThrow(() -> {
                offsets.offsetAt(diagnostic.range().startLine(), diagnostic.range().startByte());
                offsets.offsetAt(diagnostic.range().endLine(), diagnostic.range().endByte());
            }, "every realtime range must resolve inside the edited file: " + diagnostic);
        }
    }
}
