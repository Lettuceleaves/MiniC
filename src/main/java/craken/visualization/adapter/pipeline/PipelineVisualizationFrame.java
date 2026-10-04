package craken.visualization.adapter.pipeline;

import craken.SourceRange;
import craken.visualization.snapshot.VisualizationSnapshot;
import java.util.Objects;

/** Published values from one step; history never consults live AST metadata. */
public record PipelineVisualizationFrame(int stageIndex, long stepIndex, String stageName, String operation,
        String error, SourceRange sourceRange, boolean lastStep, boolean succeeded,
        VisualizationSnapshot input, VisualizationSnapshot output) {
    public PipelineVisualizationFrame { Objects.requireNonNull(input); Objects.requireNonNull(output); }
}
