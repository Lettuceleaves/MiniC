package craken.visualization.adapter.pipeline.projector;

import craken.compiler.*;
import craken.compiler.preprocess.*;
import craken.visualization.adapter.pipeline.*;
import java.util.*;

public final class SourceProjector implements PipelineStageProjector<PreprocessResult> {
    @Override public Class<? extends Stage> stageType() { return Preprocessor.class; }
    @Override public Class<PreprocessResult> contextType() { return PreprocessResult.class; }
    @Override public StageProjection project(PipelineStepObservation observation, PreprocessResult context, PipelineProjectionPlan input) {
        PipelineProjectionPlan original = input != null ? input : source(Objects.requireNonNull(observation.source()));
        return new StageProjection(original.highlightRange(observation.result().sourceRange()), source(context.sourceFile()));
    }
    public static PipelineProjectionPlan source(SourceFile source) {
        var rows = new ArrayList<PipelinePlans.Row>();
        int offset = 0;
        for (String text : source.content().split("\\n", -1)) {
            rows.add(new PipelinePlans.Row(text, Map.of("text", text), source.range(offset, offset + text.length())));
            offset += text.length() + 1;
        }
        return PipelinePlans.sequence("源码 · " + source.path(), rows);
    }
}
