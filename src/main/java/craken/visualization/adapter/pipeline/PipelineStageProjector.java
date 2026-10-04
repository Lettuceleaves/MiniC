package craken.visualization.adapter.pipeline;

import craken.compiler.Stage;

/** Converts captured business values into a plan without executing compiler or UI work. */
public interface PipelineStageProjector<C extends Stage.Context> {
    Class<? extends Stage> stageType();
    Class<C> contextType();
    StageProjection project(PipelineStepObservation observation, C context, PipelineProjectionPlan input);
}
