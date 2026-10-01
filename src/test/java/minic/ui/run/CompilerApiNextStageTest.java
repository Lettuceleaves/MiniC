package minic.ui.run;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.Diagnostic;
import minic.compiler.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Stage-boundary contract tests without native compilation or UI startup. */
final class CompilerApiNextStageTest {
    @Test
    void nextStageCompletesOnlyTheCurrentStageAndRetainsOnlyItsFinalResult() {
        CountingStage first = new CountingStage(3, false);
        CountingStage second = new CountingStage(2, false);
        CompilerApi api = new CompilerApi(List.of(first, second));

        api.nextStage();

        assertEquals(3, first.steps);
        assertEquals(0, second.steps);
        assertSame(second, api.currentStage().orElseThrow());
        assertEquals(1, api.currentStageIndex());
        assertEquals(3, api.stepCount());
        assertTrue(api.canNext());
        assertFalse(api.completed());
        assertFalse(first.resultRecordingEnabled());
        assertEquals(1, first.stepResults().size());
        assertTrue(first.stageResult().orElseThrow().lastStep());
        assertTrue(second.stageResult().isEmpty());
    }

    @Test
    void nextStageCanResumeASingleSteppedStageAndOldCurrentStageApiStillWorks() {
        CountingStage first = new CountingStage(3, false);
        CountingStage second = new CountingStage(2, false);
        CompilerApi api = new CompilerApi(List.of(first, second));

        api.step();
        assertEquals(1, first.steps);
        api.nextStage();
        assertEquals(3, first.steps);
        assertEquals(0, second.steps);
        api.runCurrentStage();

        assertEquals(2, second.steps);
        assertEquals(5, api.stepCount());
        assertTrue(api.completed());
        assertFalse(api.canNext());
    }

    @Test
    void cancellableNextStageChecksEveryStepAndCanResumeWithoutTouchingTheNextStage() throws Exception {
        CountingStage first = new CountingStage(3, false);
        CountingStage second = new CountingStage(1, false);
        CompilerApi api = new CompilerApi(List.of(first, second));
        AtomicInteger checks = new AtomicInteger();

        assertThrows(InterruptedException.class, () -> api.nextStage(() -> checks.incrementAndGet() > 1));
        assertEquals(2, checks.get());
        assertEquals(1, first.steps);
        assertEquals(0, second.steps);
        assertSame(first, api.currentStage().orElseThrow());
        assertTrue(api.canNext());

        checks.set(0);
        api.nextStage(() -> checks.incrementAndGet() > 2);
        assertEquals(2, checks.get(), "the next stage must not be checked or executed by this call");
        assertEquals(3, first.steps);
        assertEquals(0, second.steps);
        assertSame(second, api.currentStage().orElseThrow());
    }

    @Test
    void cancellationBeforeTheFirstStepLeavesThePipelineUntouched() {
        CountingStage stage = new CountingStage(2, false);
        CompilerApi api = new CompilerApi(List.of(stage));

        assertThrows(InterruptedException.class, () -> api.nextStage(() -> true));

        assertEquals(0, stage.steps);
        assertEquals(0, api.stepCount());
        assertEquals(0, api.currentStageIndex());
        assertTrue(stage.stageResult().isEmpty());
        assertFalse(api.completed());
    }

    @Test
    void failedStageStopsThePipelineAndLaterNextStageCallsAreNoOps() throws Exception {
        CountingStage failed = new CountingStage(2, true);
        CountingStage unreachable = new CountingStage(1, false);
        CompilerApi api = new CompilerApi(List.of(failed, unreachable));

        api.nextStage();
        api.nextStage();
        api.nextStage(() -> { fail("a completed pipeline must not request another step"); return false; });

        assertEquals(2, failed.steps);
        assertEquals(0, unreachable.steps);
        assertEquals(2, api.stepCount());
        assertEquals(0, api.currentStageIndex());
        assertSame(failed, api.currentStage().orElseThrow());
        assertTrue(api.completed());
        assertFalse(failed.succeeded());
        assertEquals(1, failed.errors().size());
    }

    @Test
    void emptyAndSuccessfullyCompletedPipelinesAcceptNextStageWithoutAdditionalWork() throws Exception {
        CompilerApi empty = new CompilerApi(List.of());
        empty.nextStage();
        empty.nextStage(() -> true);
        assertTrue(empty.completed());
        assertEquals(0, empty.stepCount());
        assertThrows(NullPointerException.class, () -> empty.nextStage(null));

        CountingStage last = new CountingStage(1, false);
        CompilerApi successful = new CompilerApi(List.of(last));
        successful.nextStage(() -> false);
        successful.nextStage();
        successful.nextStage(() -> true);
        assertTrue(successful.completed());
        assertEquals(1, successful.stepCount());
        assertTrue(last.succeeded());
    }

    private static final class CountingStage extends Stage {
        private static final SourceRange RANGE = new SourceRange(1, 0, 1, 1);
        private final int total;
        private final boolean failAtEnd;
        private int steps;

        CountingStage(int total, boolean failAtEnd) {
            this.total = total;
            this.failAtEnd = failAtEnd;
        }

        @Override
        public SourceRange step() {
            if (!canNext()) throw new IllegalStateException("stage exhausted");
            steps++;
            List<Diagnostic> errors = failAtEnd && !canNext()
                    ? List.of(new Diagnostic("NEXT001", Diagnostic.Severity.ERROR,
                            "expected test failure", "use a successful test stage", RANGE))
                    : List.of();
            return finishStep(RANGE, "test step", errors, () -> new Snapshot(steps));
        }

        @Override public boolean canNext() { return steps < total; }
    }

    private record Snapshot(int completedSteps) implements Stage.Context { }
}
