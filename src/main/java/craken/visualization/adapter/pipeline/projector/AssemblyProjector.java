package craken.visualization.adapter.pipeline.projector;

import craken.compiler.*;
import craken.compiler.asm.*;
import craken.visualization.adapter.pipeline.*;
import java.util.*;

public final class AssemblyProjector implements PipelineStageProjector<AsmResult> {
    @Override public Class<? extends Stage> stageType() { return Assembler.class; }
    @Override public Class<AsmResult> contextType() { return AsmResult.class; }
    @Override public StageProjection project(PipelineStepObservation observation, AsmResult context, PipelineProjectionPlan input) {
        var rows = Arrays.stream(context.text().split("\\n", -1)).map(line -> new PipelinePlans.Row(line, Map.of("assembly", line), null)).toList();
        var output = PipelinePlans.sequence("汇编", rows);
        if (observation.result().operation().startsWith("EMIT_") && !rows.isEmpty()) {
            int index = rows.size() - 1;
            if (rows.get(index).label().isEmpty() && index > 0) index--;
            output = output.highlighted(Set.of(new ProjectionKey.Indexed("汇编", index)));
        }
        return new StageProjection((input == null ? PipelinePlans.empty("IR") : input).highlightRange(observation.result().sourceRange()), output);
    }
}
