package craken.visualization.adapter.pipeline;

import craken.SourceRange;
import craken.compiler.*;
import craken.ui.pipeline.PipelineSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class PipelineAtomicFrameTest {
    @Test
    void frameFailureKeepsBothPreviousSidesAndRetriesWithoutAnotherCompilerStep() throws Exception {
        CountingStage stage = new CountingStage(3);
        CompilerApi compiler = new CompilerApi(List.of(stage));
        AtomicBoolean fail = new AtomicBoolean(true);
        Object visualization = visualization(name -> { if (name.equals("OUTPUT") && fail.getAndSet(false)) throw new IllegalStateException("display failure"); });
        PipelineSession session = session(compiler, visualization);
        Object old = get(session.snapshot(), "visualization");
        session.nextStep(() -> false);
        assertEquals(1, compiler.stepCount());
        assertEquals(1, stage.steps);
        assertSame(old, get(session.snapshot(), "visualization"));
        assertEquals(true, get(session.snapshot(), "visualizationPending"));
        assertFalse(session.snapshot().failed(), "display failure does not change compilation success");
        session.nextStep(() -> false);
        assertEquals(1, compiler.stepCount(), "retry must use the pending captured observation");
        assertEquals(false, get(session.snapshot(), "visualizationPending"));
        assertNotSame(old, get(session.snapshot(), "visualization"));
        assertEquals(1, stage.contextCalls);
    }

    @Test
    void terminalFramesKeepExecutedIdentityAndRotateTheOutputOnlyForTheNextActualStageStep() throws Exception {
        CountingStage first = new CountingStage(2);
        CountingStage second = new CountingStage(1);
        CompilerApi compiler = new CompilerApi(List.of(first, second));
        Object visualization = visualization(name -> { });
        PipelineSession session = session(compiler, visualization);
        session.nextStage(() -> false);
        Object terminal = get(session.snapshot(), "visualization");
        assertEquals(0, get(terminal, "stageIndex"));
        assertEquals(true, get(terminal, "lastStep"));
        assertSame(second, compiler.currentStage().orElseThrow());
        long outputId = (long) get(get(terminal, "output"), "containerId");
        session.nextStep(() -> false);
        Object next = get(session.snapshot(), "visualization");
        assertEquals(outputId, get(get(next, "input"), "containerId"));
        assertNotEquals(outputId, get(get(next, "output"), "containerId"));
        assertEquals(2, get(visualization, "activeContainerCount"));
        assertTrue(session.selectStage(0));
        assertSame(terminal, get(session.snapshot(), "visualization"));
        assertEquals(3, compiler.stepCount());
    }

    @Test
    void nextStageProjectsTheTerminalAndKeepsItPendingForRetry() throws Exception {
        CountingStage stage = new CountingStage(3);
        CompilerApi compiler = new CompilerApi(List.of(stage));
        AtomicBoolean fail = new AtomicBoolean(true);
        PipelineSession session = session(compiler, visualization(name -> {
            if (name.equals("INPUT") && fail.getAndSet(false)) throw new IllegalStateException("fail once");
        }));
        session.nextStage(() -> false);
        assertEquals(3, compiler.stepCount(), "bulk stage runs project only the terminal frame");
        assertTrue(session.snapshot().visualizationPending());
        assertThrows(InterruptedException.class, () -> session.nextStage(() -> true));
        assertEquals(3, compiler.stepCount());
        session.nextStage(() -> false);
        assertEquals(3, compiler.stepCount());
        assertTrue(session.snapshot().succeeded());
    }

    @Test
    void publishedFrameKeepsEveryAffectedCellAndDoesNotHighlightUnrelatedCells() throws Exception {
        CountingStage stage = new CountingStage(1);
        var projector = new PipelineStageProjector<Progress>() {
            @Override public Class<? extends Stage> stageType() { return CountingStage.class; }
            @Override public Class<Progress> contextType() { return Progress.class; }
            @Override public StageProjection project(PipelineStepObservation observation, Progress context, PipelineProjectionPlan input) {
                var output = PipelinePlans.sequence("cells", List.of(new PipelinePlans.Row("a", Map.of(), null),
                        new PipelinePlans.Row("b", Map.of(), null), new PipelinePlans.Row("c", Map.of(), null)));
                return new StageProjection(input, output.highlighted(Set.of(new ProjectionKey.Indexed("cells", 0), new ProjectionKey.Indexed("cells", 1))));
            }
        };
        var visual = new PipelineVisualizationSession(new PipelineProjectionRegistry(List.of(projector)), PipelineCommitProbe.NONE);
        try (var session = new PipelineSession(new CompilerApi(List.of(stage)), visual)) {
            session.nextStep(() -> false);
            assertFalse(session.snapshot().visualizationPending());
            var snapshot = session.snapshot().visualization().output();
            var occurrence = craken.visualization.navigation.NavigationResolver.resolve(
                    craken.visualization.snapshot.SnapshotCodec.toDisplayModel(snapshot)).occurrences().getFirst();
            assertEquals(2, occurrence.highlights().size());
            var labels = occurrence.highlights().stream().map(location -> snapshot.pages().get(location.pageId()).nodes().get(location.nodeId()).content().label()).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("a", "b"), labels);
        }
    }

    @Test
    void closeStopsAllFurtherCompilerAdvancement() throws Exception {
        var stage = new CountingStage(3);
        var compiler = new CompilerApi(List.of(stage));
        var session = new PipelineSession(compiler);
        session.close(); session.close();
        session.nextStep(() -> false);
        session.nextStage(() -> false);
        assertEquals(0, compiler.stepCount(), "a disposed visualization session must not consume compiler steps");
        assertFalse(session.snapshot().canAdvance());
        assertFalse(session.selectStage(0));
    }

    @Test
    void closeClearsPendingProjectionInsteadOfOfferingRetries() throws Exception {
        var compiler = new CompilerApi(List.of(new CountingStage(3)));
        var visual = new PipelineVisualizationSession(PipelineProjectionRegistry.standard(), checkpoint -> {
            if (checkpoint.equals("OUTPUT")) throw new IllegalStateException("display failure");
        });
        var session = new PipelineSession(compiler, visual);
        session.nextStep(() -> false);
        assertTrue(session.snapshot().visualizationPending());
        assertEquals(1, compiler.stepCount());
        session.close();
        assertFalse(session.snapshot().visualizationPending(), "disposed contexts cannot remain pending");
        assertFalse(session.snapshot().canAdvance());
        session.nextStep(() -> false);
        assertEquals(1, compiler.stepCount());
        assertEquals(0, visual.activeContainerCount());
    }

    private interface Checkpoint { void reached(String name); }
    private static Object visualization(Checkpoint probe) throws Exception {
        Class<?> type = Class.forName("craken.visualization.adapter.pipeline.PipelineVisualizationSession");
        Class<?> hook = Class.forName("craken.visualization.adapter.pipeline.PipelineCommitProbe");
        Object callback = Proxy.newProxyInstance(hook.getClassLoader(), new Class<?>[]{hook}, (proxy, method, args) -> {
            if (method.getName().equals("checkpoint")) probe.reached((String) args[0]);
            return null;
        });
        return type.getConstructor(PipelineProjectionRegistry.class, hook).newInstance(PipelineProjectionRegistry.standard(), callback);
    }
    private static PipelineSession session(CompilerApi compiler, Object visualization) throws Exception {
        return PipelineSession.class.getConstructor(CompilerApi.class, visualization.getClass()).newInstance(compiler, visualization);
    }
    private static Object get(Object target, String accessor) throws Exception { return target.getClass().getMethod(accessor).invoke(target); }
    private static final class CountingStage extends Stage {
        private final int total; private int steps; private int contextCalls;
        CountingStage(int total) { this.total = total; }
        @Override public boolean canNext() { return steps < total; }
        @Override public SourceRange step() {
            steps++;
            return finishStep(new SourceRange(1,0,1,1), "step " + steps, List.of(), () -> {
                contextCalls++; return new Progress(steps);
            });
        }
    }
    private record Progress(int value) implements Stage.Context { }
}
