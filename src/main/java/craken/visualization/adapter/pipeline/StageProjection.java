package craken.visualization.adapter.pipeline;

import java.util.Objects;

/** Both sides of a single compiler step are prepared together. */
public record StageProjection(PipelineProjectionPlan input, PipelineProjectionPlan output) {
    public StageProjection { Objects.requireNonNull(input); Objects.requireNonNull(output); }
}
