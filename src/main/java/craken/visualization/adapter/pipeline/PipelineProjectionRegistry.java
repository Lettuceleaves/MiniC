package craken.visualization.adapter.pipeline;

import craken.compiler.Stage;
import craken.visualization.adapter.pipeline.projector.*;
import java.util.*;

/** Selects and type-checks captured contexts; never substitutes a later live stage. */
public final class PipelineProjectionRegistry {
    private final List<PipelineStageProjector<?>> projectors;
    public PipelineProjectionRegistry(List<PipelineStageProjector<?>> projectors) { this.projectors = List.copyOf(projectors); }
    public static PipelineProjectionRegistry standard() {
        return new PipelineProjectionRegistry(List.of(new SourceProjector(), new TokenProjector(), new ParserProjector(),
                new SemanticProjector(), new IrProjector()));
    }
    public StageProjection project(PipelineStepObservation observation, PipelineProjectionPlan input) {
        for (var projector : projectors) if (projector.stageType().isInstance(observation.executedStage()))
            return projectChecked(projector, observation, input);
        // Custom stages still display their actual operation and diagnostics.
        var result = observation.result();
        return new StageProjection(input == null ? PipelinePlans.empty("输入") : input,
                PipelinePlans.sequence(result.stageType().getSimpleName(), List.of(new PipelinePlans.Row(result.operation(),
                        Map.of("operation", result.operation(), "error", result.error()), result.sourceRange()))));
    }
    private static <C extends Stage.Context> StageProjection projectChecked(PipelineStageProjector<C> projector,
            PipelineStepObservation observation, PipelineProjectionPlan input) {
        if (!projector.contextType().isInstance(observation.context()))
            throw new IllegalArgumentException("Context does not match " + projector.stageType().getName());
        return projector.project(observation, projector.contextType().cast(observation.context()), input);
    }
}
