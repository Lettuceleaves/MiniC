package minic.ui.pipeline;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.Diagnostic;
import minic.compiler.SourceFile;
import minic.compiler.Stage;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static minic.ui.pipeline.PipelineSession.Status.*;
import static org.junit.jupiter.api.Assertions.*;

final class PipelineSessionTest {
    @TempDir Path temporary;

    @Test
    void oneStepAndStageAdvanceFollowRealCompilerWorkAndKeepFutureStagesLocked() throws Exception {
        CountingStage first = new CountingStage(3, false);
        CountingStage second = new CountingStage(2, false);
        PipelineSession session = new PipelineSession(new CompilerApi(List.of(first, second)));

        assertEquals(List.of(CURRENT, PENDING), statuses(session));
        assertFalse(session.selectStage(1));
        session.nextStep(() -> false);
        assertEquals(1, first.steps);
        assertEquals(1, session.snapshot().stepCount());
        assertEquals(0, session.snapshot().currentStageIndex());
        session.nextStage(() -> false);

        assertEquals(3, first.steps);
        assertEquals(0, second.steps);
        assertEquals(List.of(COMPLETED, CURRENT), statuses(session));
        assertEquals(1, session.snapshot().selectedStageIndex());
        assertTrue(session.selectStage(0));
        assertEquals(0, session.snapshot().selectedStageIndex());
        assertEquals(1, session.snapshot().currentStageIndex());
        session.nextStep(() -> false);
        assertEquals(3, first.steps, "reviewing history must not rerun a completed stage");
        assertEquals(1, second.steps);
        assertEquals(1, session.snapshot().selectedStageIndex());
    }

    @Test
    void failuresStopProgressAndLeaveEarlierResultsSelectable() throws Exception {
        CountingStage first = new CountingStage(1, false);
        CountingStage failure = new CountingStage(1, true);
        CountingStage future = new CountingStage(1, false);
        PipelineSession session = new PipelineSession(new CompilerApi(List.of(first, failure, future)));
        session.nextStage(() -> false);
        session.nextStage(() -> false);
        session.nextStep(() -> { fail("failed compilation must not ask to advance"); return false; });

        assertEquals(List.of(COMPLETED, FAILED, PENDING), statuses(session));
        assertTrue(session.snapshot().failed());
        assertFalse(session.snapshot().canAdvance());
        assertFalse(session.snapshot().succeeded());
        assertTrue(session.snapshot().stages().get(1).error().contains("expected test failure"));
        assertTrue(session.selectStage(0));
        assertTrue(session.selectStage(1));
        assertFalse(session.selectStage(2));
        assertEquals(0, future.steps);
    }

    @Test
    void cancellationPublishesPartialProgressAndCanResume() throws Exception {
        CountingStage first = new CountingStage(3, false);
        CountingStage second = new CountingStage(1, false);
        PipelineSession session = new PipelineSession(new CompilerApi(List.of(first, second)));
        AtomicInteger checks = new AtomicInteger();

        assertThrows(InterruptedException.class, () -> session.nextStep(() -> true));
        assertEquals(0, session.snapshot().stepCount());
        assertThrows(InterruptedException.class, () -> session.nextStage(() -> checks.incrementAndGet() > 1));
        assertEquals(1, session.snapshot().stepCount());
        assertEquals(List.of(CURRENT, PENDING), statuses(session));
        assertTrue(session.snapshot().canAdvance());
        assertFalse(session.snapshot().failed());
        session.nextStage(() -> false);
        assertEquals(List.of(COMPLETED, CURRENT), statuses(session));
        assertEquals(0, second.steps);
    }

    @Test
    void completedPipelinesAreReadOnlyAndSnapshotsRemainImmutable() throws Exception {
        PipelineSession session = new PipelineSession(new CompilerApi(List.of(new CountingStage(1, false))));
        PipelineSession.Snapshot initial = session.snapshot();
        session.nextStep(() -> false);
        session.nextStage(() -> { fail("completed compilation must not check cancellation"); return false; });

        assertEquals(CURRENT, initial.stages().getFirst().status());
        assertEquals(List.of(COMPLETED), statuses(session));
        assertTrue(session.snapshot().succeeded());
        assertFalse(session.snapshot().failed());
        assertFalse(session.snapshot().canAdvance());
        assertTrue(session.selectStage(0));
        assertFalse(session.selectStage(-1));
        assertFalse(session.selectStage(1));
        assertThrows(UnsupportedOperationException.class, () -> initial.stages().clear());
    }

    @Test
    void unexpectedCompilerExceptionsPublishAFailedStageEvenWithoutAMessage() {
        Stage broken = new Stage() {
            @Override public SourceRange step() { throw new IllegalStateException(""); }
            @Override public boolean canNext() { return true; }
        };
        PipelineSession session = new PipelineSession(new CompilerApi(List.of(broken)));

        assertThrows(IllegalStateException.class, () -> session.nextStage(() -> false));

        assertTrue(session.snapshot().failed());
        assertFalse(session.snapshot().canAdvance());
        assertEquals(FAILED, session.snapshot().stages().getFirst().status());
        assertEquals("IllegalStateException", session.snapshot().stages().getFirst().error());
    }

    @Test
    void compilesAllEightStagesWithoutExecutingTheProgram() throws Exception {
        CompilerApi compiler = new CompilerApi(new SourceFile("pipeline.mc", "int main() { return 17; }"),
                temporary.resolve("native-output"));
        PipelineSession session = new PipelineSession(compiler);
        assertEquals(PipelineSession.stageLabels(), session.snapshot().stages().stream()
                .map(PipelineSession.StageView::label).toList());
        for (int index = 0; index < 8; index++) {
            assertEquals(index, session.snapshot().currentStageIndex());
            session.nextStage(() -> false);
            assertFalse(session.snapshot().failed(), () -> session.snapshot().stages().toString());
        }
        session.nextStep(() -> false);

        assertTrue(session.snapshot().succeeded());
        assertEquals(7, session.snapshot().selectedStageIndex());
        assertTrue(session.snapshot().stages().stream().allMatch(stage -> stage.status() == COMPLETED));
        assertTrue(Files.isRegularFile(temporary.resolve("native-output/pipeline.exe")));
        ExecutableRunner execution = (ExecutableRunner) compiler.stages().getLast();
        assertEquals(0, execution.stepCount());
        assertTrue(execution.stageResult().isEmpty());
    }

    @Test
    void sessionCreationPreservesUnsavedSourcesAndUsesSeparateArtifactDirectories() throws Exception {
        Path sourcePath = temporary.resolve("saved.mc");
        Files.writeString(sourcePath, "invalid original content");
        SourceFile unsaved = new SourceFile(sourcePath.toString(), "int main() { return 0; }");
        Path output = temporary.resolve("sessions");
        PipelineSession first = PipelineSession.create(unsaved, output);
        PipelineSession second = PipelineSession.create(unsaved, output);
        for (int i = 0; i < 4; i++) first.nextStage(() -> false);

        assertFalse(first.snapshot().failed(), () -> first.snapshot().stages().toString());
        assertEquals(0, second.snapshot().stepCount());
        assertEquals("invalid original content", Files.readString(sourcePath));
        try (var directories = Files.list(output)) {
            assertEquals(2, directories.count());
        }
    }

    private static List<PipelineSession.Status> statuses(PipelineSession session) {
        return session.snapshot().stages().stream().map(PipelineSession.StageView::status).toList();
    }

    private static final class CountingStage extends Stage {
        private static final SourceRange RANGE = new SourceRange(1, 0, 1, 1);
        private final int total;
        private final boolean fails;
        private int steps;

        private CountingStage(int total, boolean fails) {
            this.total = total;
            this.fails = fails;
        }

        @Override public SourceRange step() {
            steps++;
            List<Diagnostic> errors = fails && !canNext()
                    ? List.of(new Diagnostic("PIPE001", Diagnostic.Severity.ERROR,
                    "expected test failure", "use a successful test stage", RANGE)) : List.of();
            return finishStep(RANGE, "test step", errors, () -> new Context() { });
        }

        @Override public boolean canNext() { return steps < total; }
    }
}
