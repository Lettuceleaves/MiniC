package craken.visualization.adapter.pipeline.projector;

import craken.compiler.*;
import craken.compiler.parser.*;
import craken.visualization.adapter.pipeline.*;

public final class ParserProjector implements PipelineStageProjector<ParserResult> {
    @Override public Class<? extends Stage> stageType() { return Parser.class; }
    @Override public Class<ParserResult> contextType() { return ParserResult.class; }
    @Override public StageProjection project(PipelineStepObservation observation, ParserResult context, PipelineProjectionPlan input) {
        return new StageProjection((input == null ? PipelinePlans.empty("Token") : input).highlightRange(observation.result().sourceRange()),
                AstPageProjector.project(context.program(), observation.currentAstNode()));
    }
}
