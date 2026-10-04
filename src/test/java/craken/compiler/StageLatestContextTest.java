package craken.compiler;

import craken.SourceRange;
import craken.compiler.lexer.Lexer;
import craken.compiler.lexer.LexerResult;
import craken.compiler.parser.Parser;
import craken.compiler.parser.ParserResult;
import craken.compiler.preprocess.PreprocessResult;
import craken.compiler.preprocess.Preprocessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class StageLatestContextTest {
    @Test
    void defaultDoesNotCreateIntermediateContextsAndStillRetainsTheFinalResult() {
        CountingStage stage = new CountingStage(3);
        CompilerApi api = new CompilerApi(List.of(stage));

        assertFalse(captureEnabled(stage));
        assertNull(api.stepResult().context());
        assertNull(api.stepResult().context());
        assertEquals(0, stage.contextCalls);
        assertTrue(stage.stepResults().isEmpty());
        Stage.Result end = api.stepResult();
        assertEquals(new Progress(3), end.context());
        assertEquals(1, stage.contextCalls);
        assertEquals(List.of(end), stage.stepResults());
        assertSame(end, api.result(stage).orElseThrow());
    }

    @Test
    void latestCaptureRunsEverySupplierWithoutAccumulatingHistory() {
        CountingStage stage = new CountingStage(4);
        CompilerApi api = new CompilerApi(List.of(stage));
        setCapture(api, Stage.class, true);

        for (int step = 1; step <= 4; step++) {
            Stage.Result result = api.stepResult();
            assertEquals(new Progress(step), result.context());
            assertEquals(step, stage.contextCalls);
            assertSame(result, api.lastStepResult().orElseThrow());
            assertEquals(step == 4 ? 1 : 0, stage.stepResults().size());
            assertEquals(step == 4, stage.stageResult().isPresent());
        }
        assertFalse(stage.resultRecordingEnabled());
        assertEquals(new Progress(4), api.results(stage).getFirst().context());
    }

    @Test
    void turningCaptureOnAndOffAffectsOnlyFollowingSteps() {
        CountingStage stage = new CountingStage(4);
        CompilerApi api = new CompilerApi(List.of(stage));
        assertNull(api.stepResult().context());

        setCapture(api, stage, true);
        Stage.Result captured = api.stepResult();
        assertEquals(new Progress(2), captured.context());
        setCapture(api, 0, false);
        assertFalse(captureEnabled(stage));
        assertSame(captured, api.lastStepResult().orElseThrow());
        assertNull(api.stepResult().context());
        assertTrue(stage.stepResults().isEmpty());
        assertEquals(new Progress(4), api.stepResult().context());
        assertEquals(2, stage.contextCalls);
        assertEquals(new Progress(2), captured.context(), "already captured results stay immutable");
    }

    @Test
    void recordingAndLatestCaptureAreIndependentAndDoNotDoubleEvaluateTheSupplier() {
        CountingStage stage = new CountingStage(4);
        CompilerApi api = new CompilerApi(List.of(stage));
        api.setResultRecording(stage, true);
        setCapture(api, stage, true);
        Stage.Result first = api.stepResult();
        setCapture(api, stage, false);
        Stage.Result second = api.stepResult();
        assertEquals(List.of(first, second), stage.stepResults());
        assertEquals(2, stage.contextCalls);
        assertSame(second, stage.stageResult().orElseThrow());

        setCapture(api, stage, true);
        api.setResultRecording(stage, false);
        Stage.Result third = api.stepResult();
        assertEquals(new Progress(3), third.context());
        assertTrue(stage.stepResults().isEmpty());
        assertTrue(stage.stageResult().isEmpty());
        Stage.Result last = api.stepResult();
        assertEquals(List.of(last), stage.stepResults());
        assertEquals(4, stage.contextCalls, "all enabled conditions share one capture per step");
    }

    @Test
    void configurationOverloadsValidateMembershipAndSelectAllMatchingStages() {
        CountingStage first = new CountingStage(2);
        CountingStage second = new CountingStage(1);
        CompilerApi api = new CompilerApi(List.of(first, second));
        setCapture(api, CountingStage.class, true);
        assertTrue(captureEnabled(first));
        assertTrue(captureEnabled(second));
        setCapture(api, 1, false);
        assertTrue(captureEnabled(first));
        assertFalse(captureEnabled(second));
        assertThrows(IllegalArgumentException.class, () -> setCapture(api, new CountingStage(1), true));
        assertThrows(IllegalArgumentException.class, () -> setCapture(api, Parser.class, true));
        assertThrows(IndexOutOfBoundsException.class, () -> setCapture(api, -1, true));
        assertThrows(IndexOutOfBoundsException.class, () -> setCapture(api, 2, true));
        assertThrows(NullPointerException.class, () -> setCapture(api, (Stage) null, true));
        assertThrows(NullPointerException.class, () -> setCapture(api, (Class<?>) null, true));
    }

    @Test
    void realFrontendEmitsCurrentTypedContextAndKeepsOnlyEachStageTerminalResult() {
        CompilerApi api = new CompilerApi(new SourceFile("capture.mc", "int value = 1 + 2; int main() { return value; }"));
        setCapture(api, Stage.class, true);
        Parser parser = api.stage(Parser.class);
        List<Class<? extends Stage.Context>> types = List.of(PreprocessResult.class, LexerResult.class, ParserResult.class);
        while (api.currentStageIndex() <= 2 && api.canNext()) {
            Stage executed = api.currentStage().orElseThrow();
            int executedIndex = api.currentStageIndex();
            Stage.Result result = api.stepResult();
            assertInstanceOf(types.get(executedIndex), result.context());
            assertEquals(result.lastStep() ? 1 : 0, executed.stepResults().size());
        }
        assertTrue(parser.succeeded());
        assertEquals(1, api.stage(Preprocessor.class).stepResults().size());
        assertEquals(1, api.stage(Lexer.class).stepResults().size());
        assertEquals(1, parser.stepResults().size());
        assertSame(parser.stageResult().orElseThrow(), api.lastStepResult().orElseThrow());
        assertNotSame(parser, api.currentStage().orElseThrow(), "latest result keeps the executed stage after auto advance");
        assertEquals(parser.getClass(), api.lastStepResult().orElseThrow().stageType());
    }

    // Reflection lets this behavioral suite execute against the pre-feature API for the Red run.
    private static boolean captureEnabled(Stage stage) {
        return (boolean) invoke(Stage.class, stage, "captureLatestContext", new Class<?>[0]);
    }

    private static void setCapture(CompilerApi api, Stage stage, boolean enabled) {
        invoke(CompilerApi.class, api, "setCaptureLatestContext", new Class<?>[] {Stage.class, boolean.class}, stage, enabled);
    }

    private static void setCapture(CompilerApi api, int index, boolean enabled) {
        invoke(CompilerApi.class, api, "setCaptureLatestContext", new Class<?>[] {int.class, boolean.class}, index, enabled);
    }

    private static void setCapture(CompilerApi api, Class<?> type, boolean enabled) {
        invoke(CompilerApi.class, api, "setCaptureLatestContext", new Class<?>[] {Class.class, boolean.class}, type, enabled);
    }

    private static Object invoke(Class<?> owner, Object target, String name, Class<?>[] types, Object... args) {
        try {
            return owner.getMethod(name, types).invoke(target, args);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("missing current-context capture API", failure);
        }
    }

    private static final class CountingStage extends Stage {
        private static final SourceRange RANGE = new SourceRange(1, 0, 1, 1);
        private final int total;
        private int steps;
        private int contextCalls;

        private CountingStage(int total) { this.total = total; }

        @Override public SourceRange step() {
            if (!canNext()) throw new IllegalStateException("stage exhausted");
            steps++;
            return finishStep(RANGE, "capture test", List.of(), () -> {
                contextCalls++;
                return new Progress(steps);
            });
        }

        @Override public boolean canNext() { return steps < total; }
    }

    private record Progress(int steps) implements Stage.Context { }
}
