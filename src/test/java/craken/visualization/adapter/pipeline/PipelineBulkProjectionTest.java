package craken.visualization.adapter.pipeline;

import craken.SourceRange;
import craken.compiler.*;
import craken.ui.pipeline.PipelineSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** A bulk stage run merges intermediate frames: capture and projection happen once at the terminal. */
@Tag("visualization-adapter")
final class PipelineBulkProjectionTest {
    @Test void nextStageProjectsOnlyTheTerminalFrameWhileEveryStepStillRuns() throws Exception {
        var projectedRows = new AtomicInteger();
        var stage = new CountingStage(200);
        var projector = new PipelineStageProjector<CountingStage.Progress>() {
            @Override public Class<? extends Stage> stageType() { return CountingStage.class; }
            @Override public Class<CountingStage.Progress> contextType() { return CountingStage.Progress.class; }
            @Override public StageProjection project(PipelineStepObservation observation, CountingStage.Progress context,
                                                    PipelineProjectionPlan input) {
                projectedRows.addAndGet(context.size());
                var rows = new ArrayList<PipelinePlans.Row>();
                for (int index = 0; index < context.size(); index++) rows.add(new PipelinePlans.Row("row" + index, Map.of(), null));
                return new StageProjection(input == null ? PipelinePlans.empty("输入") : input,
                        PipelinePlans.sequence("cells", rows));
            }
        };
        var visual = new PipelineVisualizationSession(new PipelineProjectionRegistry(List.of(projector)), PipelineCommitProbe.NONE);
        try (var session = new PipelineSession(new CompilerApi(List.of(stage)), visual)) {
            session.nextStage(() -> false);
            assertEquals(200, stage.steps(), "every compilation step still executes");
            assertEquals(1, stage.contextCalls(), "bulk mode must not copy growing contexts on every step");
            assertEquals(200, projectedRows.get(), "the terminal frame is projected once instead of once per step");
            assertTrue(session.snapshot().succeeded());
            assertFalse(session.snapshot().visualizationPending());
            assertTrue(session.snapshot().visualization().lastStep());
        }
    }

    @Test void singleStepModeStillCapturesAndProjectsEveryStep() throws Exception {
        var projectedRows = new AtomicInteger();
        var stage = new CountingStage(3);
        var projector = new PipelineStageProjector<CountingStage.Progress>() {
            @Override public Class<? extends Stage> stageType() { return CountingStage.class; }
            @Override public Class<CountingStage.Progress> contextType() { return CountingStage.Progress.class; }
            @Override public StageProjection project(PipelineStepObservation observation, CountingStage.Progress context,
                                                    PipelineProjectionPlan input) {
                projectedRows.addAndGet(1);
                return new StageProjection(input == null ? PipelinePlans.empty("输入") : input,
                        PipelinePlans.sequence("cells", List.of(new PipelinePlans.Row("row", Map.of(), null))));
            }
        };
        var visual = new PipelineVisualizationSession(new PipelineProjectionRegistry(List.of(projector)), PipelineCommitProbe.NONE);
        try (var session = new PipelineSession(new CompilerApi(List.of(stage)), visual)) {
            session.nextStep(() -> false);
            assertEquals(1, stage.contextCalls());
            assertEquals(1, projectedRows.get());
            session.nextStep(() -> false);
            assertEquals(2, stage.contextCalls());
            assertEquals(2, projectedRows.get());
            assertEquals(2, stage.steps());
        }
    }

    private static final class CountingStage extends Stage {
        private static final SourceRange RANGE = new SourceRange(1, 0, 1, 1);
        private final int total;
        private int steps;
        private int contextCalls;
        private CountingStage(int total) { this.total = total; }
        @Override public SourceRange step() {
            steps++;
            return finishStep(RANGE, "step", List.of(), () -> { contextCalls++; return new Progress(steps); });
        }
        @Override public boolean canNext() { return steps < total; }
        private int steps() { return steps; }
        private int contextCalls() { return contextCalls; }
        private record Progress(int size) implements Context { }
    }
}
