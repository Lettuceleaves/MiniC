package craken.ui.run;

import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.Stage;
import craken.compiler.asm.Assembler;
import craken.compiler.execute.ExecutableRunner;
import craken.compiler.library.SystemLibraryCatalog;
import craken.compiler.link.Linker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RunCompilationTest {
    @TempDir Path temporary;

    @Test
    void progressValidatesItsBoundsAndUsesFractionalDivision() {
        assertEquals(0.0, new RunCompilation.Progress(0, 8).fraction());
        assertEquals(0.375, new RunCompilation.Progress(3, 8).fraction());
        assertEquals(1.0, new RunCompilation.Progress(8, 8).fraction());
        assertThrows(IllegalArgumentException.class, () -> new RunCompilation.Progress(-1, 8));
        assertThrows(IllegalArgumentException.class, () -> new RunCompilation.Progress(9, 8));
        assertThrows(IllegalArgumentException.class, () -> new RunCompilation.Progress(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunCompilation.Progress(0, -1));
    }

    @Test
    void reportsEverySuccessfulCompilationStageExactlyOnceWithoutExecutingTheProgram() throws Exception {
        Path marker = temporary.resolve("progress-not-executed.txt");
        Path movedMarker = temporary.resolve("progress-was-executed.txt");
        Files.writeString(marker, "not executed");
        SourceFile source = new SourceFile("progress.mc", """
                #include <stdio.h>
                int main() { return rename("%s", "%s"); }
                """.formatted(cPath(marker), cPath(movedMarker)));
        List<RunCompilation.Progress> observed = new ArrayList<>();
        Thread compilationThread = Thread.currentThread();

        var outcome = new RunCompilation().compile(source, temporary.resolve("progress-output"), progress -> {
            assertEquals(compilationThread, Thread.currentThread(), "callbacks belong to the compilation thread");
            observed.add(progress);
        });

        assertTrue(outcome.succeeded(), () -> outcome.diagnostics().toString());
        assertEquals(IntStream.rangeClosed(0, 8).mapToObj(count -> new RunCompilation.Progress(count, 8)).toList(),
                observed);
        assertEquals(1.0, observed.getLast().fraction());
        assertTrue(Files.isRegularFile(outcome.artifact().path()));
        assertEquals("not executed", Files.readString(marker));
        assertFalse(Files.exists(movedMarker), "100% means compiled, not that ExecutableRunner has executed");
    }

    @Test
    void failedCompilationDoesNotCountTheFailedStageOrReportCompletion() throws Exception {
        List<RunCompilation.Progress> observed = new ArrayList<>();

        var outcome = new RunCompilation().compile(
                new SourceFile("invalid-progress.mc", "int main() { return missing; }"),
                temporary.resolve("failed-progress"), observed::add);

        assertFalse(outcome.succeeded());
        assertNull(outcome.artifact());
        assertEquals("SemanticAnalyzer", outcome.failedStage());
        assertEquals(List.of(new RunCompilation.Progress(0, 8), new RunCompilation.Progress(1, 8),
                new RunCompilation.Progress(2, 8), new RunCompilation.Progress(3, 8)), observed);
        assertTrue(observed.stream().allMatch(progress -> progress.fraction() < 1));
    }

    @Test
    void cancellationFromTheInitialOrAStageCallbackStopsBeforeAnyFurtherStage() throws Exception {
        for (int cancelAfter : new int[] { 0, 2 }) {
            List<RunCompilation.Progress> observed = new ArrayList<>();
            Path output = temporary.resolve("cancel-progress-" + cancelAfter);
            try {
                assertThrows(InterruptedException.class, () -> new RunCompilation().compile(
                        new SourceFile("cancel-progress.mc", "int main() { return 0; }"), output, progress -> {
                            observed.add(progress);
                            if (progress.completedStages() == cancelAfter) Thread.currentThread().interrupt();
                        }));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertEquals(IntStream.rangeClosed(0, cancelAfter)
                    .mapToObj(count -> new RunCompilation.Progress(count, 8)).toList(), observed);
            try (var files = Files.walk(output)) {
                assertFalse(files.anyMatch(Files::isRegularFile), "no assembly or executable should be emitted after cancellation");
            }
        }
    }

    @Test
    void cancellationFromTheLastProgressCallbackDoesNotReturnASuccessfulOutcome() {
        List<RunCompilation.Progress> observed = new ArrayList<>();
        try {
            assertThrows(InterruptedException.class, () -> new RunCompilation().compile(
                    new SourceFile("last-progress.mc", "int main() { return 0; }"),
                    temporary.resolve("last-progress"), progress -> {
                        observed.add(progress);
                        if (progress.completedStages() == progress.totalStages()) Thread.currentThread().interrupt();
                    }));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(new RunCompilation.Progress(8, 8), observed.getLast());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsANullProgressCallbackBeforeCreatingOutput() {
        Path output = temporary.resolve("null-progress");
        assertThrows(NullPointerException.class, () -> new RunCompilation().compile(
                new SourceFile("null-progress.mc", "int main() { return 0; }"), output, null));
        assertFalse(Files.exists(output));
    }

    @Test
    void compilesTheUnsavedBufferWithoutReadingOrReplacingTheFileOnDisk() throws Exception {
        Path sourcePath = temporary.resolve("edited.mc");
        String savedText = "this disk content is intentionally not valid Craken\n";
        Files.writeString(sourcePath, savedText);
        SourceFile snapshot = new SourceFile(sourcePath.toString(), "int main() { return 17; }\n");

        var outcome = new RunCompilation().compile(snapshot, temporary.resolve("output"));

        assertTrue(outcome.succeeded(), () -> outcome.diagnostics().toString());
        assertTrue(Files.isRegularFile(outcome.artifact().path()));
        assertEquals(savedText, Files.readString(sourcePath));
        assertTrue(outcome.diagnostics().isEmpty());
        assertEquals("", outcome.failedStage());
    }

    @Test
    void compilesANewBufferWithoutCreatingItsSourceFile() throws Exception {
        Path sourcePath = temporary.resolve("not-saved.mc");
        var outcome = new RunCompilation().compile(
                new SourceFile(sourcePath.toString(), "int main() { return 0; }"),
                temporary.resolve("output"));

        assertTrue(outcome.succeeded(), () -> outcome.diagnostics().toString());
        assertFalse(Files.exists(sourcePath));
    }

    @Test
    void reportsCompilationErrorsWithoutReusingAPreviousExecutable() throws Exception {
        RunCompilation compilation = new RunCompilation();
        Path output = temporary.resolve("output");
        var successful = compilation.compile(new SourceFile("same.mc", "int main() { return 0; }"), output);
        assertTrue(successful.succeeded(), () -> successful.diagnostics().toString());

        var failed = compilation.compile(new SourceFile("same.mc", "int main() { return missing; }"), output);

        assertFalse(failed.succeeded());
        assertNull(failed.artifact());
        assertFalse(failed.diagnostics().isEmpty());
        assertFalse(failed.failedStage().isBlank());
        assertTrue(Files.isRegularFile(successful.artifact().path()));
    }

    @Test
    void resolvesRelativeIncludesBesideTheOriginalSourcePath() throws Exception {
        Path sourceDirectory = Files.createDirectories(temporary.resolve("sources"));
        Files.writeString(sourceDirectory.resolve("local.mh"), "#define LOCAL_VALUE 29\n");
        SourceFile source = new SourceFile(sourceDirectory.resolve("include.mc").toString(),
                "#include \"local.mh\"\nint main() { return LOCAL_VALUE; }\n");

        var outcome = new RunCompilation().compile(source, temporary.resolve("separate-output"));

        assertTrue(outcome.succeeded(), () -> outcome.diagnostics().toString());
        assertFalse(Files.exists(sourceDirectory.resolve("include.mc")));
    }

    @Test
    void usesUniqueOutputDirectoriesAndNeverExecutesTheCompiledProgram() throws Exception {
        Path marker = temporary.resolve("should-stay.txt");
        Path movedMarker = temporary.resolve("should-not-run.txt");
        Files.writeString(marker, "not executed");
        SourceFile source = new SourceFile("same.mc", """
                #include <stdio.h>
                int main() {
                    return rename("%s", "%s");
                }
                """.formatted(cPath(marker), cPath(movedMarker)));
        RunCompilation compilation = new RunCompilation();
        Path output = temporary.resolve("output");

        var first = compilation.compile(source, output);
        var second = compilation.compile(source, output);

        assertTrue(first.succeeded(), () -> first.diagnostics().toString());
        assertTrue(second.succeeded(), () -> second.diagnostics().toString());
        Path firstDirectory = first.artifact().path().getParent();
        Path secondDirectory = second.artifact().path().getParent();
        assertNotEquals(firstDirectory, secondDirectory);
        assertEquals(output, firstDirectory.getParent());
        assertEquals(output, secondDirectory.getParent());
        assertTrue(Files.isRegularFile(first.artifact().path()));
        assertTrue(Files.isRegularFile(second.artifact().path()));
        assertEquals("not executed", Files.readString(marker));
        assertFalse(Files.exists(movedMarker));
    }

    @Test
    void disablingRecordingRetainsOnlyTheFinalStageResultsAndLeavesExecutionUntouched() throws Exception {
        CompilerApi api = new CompilerApi(new SourceFile("recording.mc", "int main() { return 3; }"),
                temporary.resolve("explicit-output"));
        api.setResultRecording(Stage.class, true);
        api.setResultRecording(Stage.class, false);
        Linker linker = linker(api);

        api.runThrough(linker, () -> false);

        assertTrue(linker.succeeded(), () -> linker.errors().toString());
        assertEquals(temporary.resolve("explicit-output").resolve("recording.exe"),
                linker.result().executableArtifactOptional().orElseThrow().path());
        for (Stage stage : api.stages()) {
            assertFalse(stage.resultRecordingEnabled());
            assertTrue(api.results(stage).size() <= 1);
            if (stage instanceof ExecutableRunner runner) {
                assertEquals(0, runner.stepCount());
                assertTrue(api.result(stage).isEmpty());
            } else {
                Stage.Result result = api.result(stage).orElseThrow();
                assertTrue(result.lastStep());
                assertNotNull(result.context());
            }
        }
    }

    @Test
    void utf8ConsoleEntryIsOptInAndLeavesDefaultCompilationAndLibraryBindingsUntouched() throws Exception {
        SourceFile source = new SourceFile("console.mc", "int main() { return 7; }");
        var defaultBindings = Map.copyOf(SystemLibraryCatalog.defaults().bindings());
        CompilerApi defaults = new CompilerApi(source, temporary.resolve("default-console"));
        CompilerApi enabled = new CompilerApi(source, temporary.resolve("utf8-console"), true);
        defaults.setResultRecording(Stage.class, false);
        enabled.setResultRecording(Stage.class, false);
        defaults.runThrough(linker(defaults));
        enabled.runThrough(linker(enabled));
        assertTrue(linker(defaults).succeeded(), () -> linker(defaults).errors().toString());
        assertTrue(linker(enabled).succeeded(), () -> linker(enabled).errors().toString());

        String defaultAssembly = assembly(defaults);
        String enabledAssembly = assembly(enabled);
        assertFalse(defaultAssembly.contains("SetConsoleCP"));
        assertFalse(defaultAssembly.contains("SetConsoleOutputCP"));
        assertTrue(enabledAssembly.contains("EXTERN SetConsoleCP:PROC"));
        assertTrue(enabledAssembly.contains("EXTERN SetConsoleOutputCP:PROC"));
        String setup = String.join(System.lineSeparator(),
                "    sub rsp, 40", "    mov ecx, 65001", "    call SetConsoleCP",
                "    mov ecx, 65001", "    call SetConsoleOutputCP", "    call main");
        assertTrue(enabledAssembly.contains(setup));
        assertEquals(defaultBindings, SystemLibraryCatalog.defaults().bindings());

        for (CompilerApi api : java.util.List.of(defaults, enabled)) {
            for (Stage stage : api.stages()) {
                assertFalse(stage.resultRecordingEnabled());
                assertTrue(api.results(stage).size() <= 1);
                if (stage instanceof ExecutableRunner runner) {
                    assertEquals(0, runner.stepCount(), "compilation must not execute its result");
                    assertTrue(api.result(stage).isEmpty());
                }
            }
        }
        String defaultImage = new String(Files.readAllBytes(linker(defaults).result()
                .executableArtifactOptional().orElseThrow().path()), StandardCharsets.ISO_8859_1);
        String enabledImage = new String(Files.readAllBytes(linker(enabled).result()
                .executableArtifactOptional().orElseThrow().path()), StandardCharsets.ISO_8859_1);
        assertFalse(defaultImage.contains("SetConsoleCP"));
        assertFalse(defaultImage.contains("SetConsoleOutputCP"));
        assertTrue(enabledImage.contains("SetConsoleCP"));
        assertTrue(enabledImage.contains("SetConsoleOutputCP"));
    }

    @Test
    void runCompilationEnablesUtf8InitializationAlongsideUserLibraryImports() throws Exception {
        var result = new RunCompilation().compile(new SourceFile("utf8.mc", """
                #include <stdio.h>
                int main() { puts("中文输出"); return 0; }
                """), temporary.resolve("run-console"));
        assertTrue(result.succeeded(), () -> result.failedStage() + ": " + result.diagnostics());
        String assembly = Files.readString(result.artifact().path().resolveSibling("utf8.asm"));
        assertTrue(assembly.contains("EXTERN puts:PROC"));
        assertEquals(1, assembly.lines().filter("EXTERN SetConsoleCP:PROC"::equals).count());
        assertEquals(1, assembly.lines().filter("EXTERN SetConsoleOutputCP:PROC"::equals).count());
        assertTrue(assembly.contains("    call SetConsoleCP"));
        assertTrue(assembly.contains("    call SetConsoleOutputCP"));
    }

    @Test
    void canCancelCompilationBetweenStepsWithoutStartingExecution() {
        CompilerApi api = new CompilerApi(new SourceFile("cancel.mc", "int main() { return 0; }"),
                temporary.resolve("cancel-output"));
        AtomicInteger cancellationChecks = new AtomicInteger();

        assertThrows(InterruptedException.class,
                () -> api.runThrough(linker(api), () -> cancellationChecks.incrementAndGet() > 1));

        assertEquals(1, api.stepCount());
        assertFalse(Files.exists(temporary.resolve("cancel-output")));
        assertTrue(api.stages().stream().filter(ExecutableRunner.class::isInstance)
                .map(ExecutableRunner.class::cast).allMatch(runner -> runner.stepCount() == 0));
    }

    @Test
    void honoursAnAlreadyInterruptedWorkerBeforeCreatingOutput() {
        Path output = temporary.resolve("cancelled-output");
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> new RunCompilation().compile(
                    new SourceFile("cancel.mc", "int main() { return 0; }"), output));
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(Files.exists(output));
        } finally {
            Thread.interrupted();
        }
    }

    private static Linker linker(CompilerApi api) {
        return api.stages().stream().filter(Linker.class::isInstance)
                .map(Linker.class::cast).findFirst().orElseThrow();
    }

    private static String assembly(CompilerApi api) {
        return api.stages().stream().filter(Assembler.class::isInstance)
                .map(Assembler.class::cast).findFirst().orElseThrow().result().text();
    }

    private static String cPath(Path path) {
        return path.toString().replace('\\', '/').replace("\"", "\\\"");
    }
}
