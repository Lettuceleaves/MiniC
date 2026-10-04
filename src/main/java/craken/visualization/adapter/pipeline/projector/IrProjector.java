package craken.visualization.adapter.pipeline.projector;

import craken.compiler.*;
import craken.compiler.ir.*;
import craken.visualization.adapter.pipeline.*;
import java.util.*;

public final class IrProjector implements PipelineStageProjector<IrResult> {
    @Override public Class<? extends Stage> stageType() { return IrLowerer.class; }
    @Override public Class<IrResult> contextType() { return IrResult.class; }
    @Override public StageProjection project(PipelineStepObservation observation, IrResult context, PipelineProjectionPlan input) {
        var rows = new ArrayList<PipelinePlans.Row>();
        for (var function : context.functions()) {
            rows.add(new PipelinePlans.Row(context.displayName(function.name()), Map.of("function", context.displayName(function.name()),
                    "returnType", function.returnType().toString()), function.range()));
            for (var block : function.blocks()) {
                rows.add(new PipelinePlans.Row(block.label(), Map.of("function", context.displayName(function.name()), "block", block.label()), function.range()));
                for (var instruction : block.instructions()) rows.add(new PipelinePlans.Row(instruction.toString(),
                        Map.of("function", context.displayName(function.name()), "block", block.label(), "instruction", instruction.toString()), instruction.range()));
            }
        }
        for (var data : context.globalData()) rows.add(new PipelinePlans.Row(context.displayName(data.label()),
                Map.of("global", context.displayName(data.label()), "type", data.declaredType().toString(), "bytes", HexFormat.of().formatHex(data.bytes())), data.range()));
        for (var data : context.stringData()) rows.add(new PipelinePlans.Row(data.label(), Map.of("string", data.label(), "value", data.value()), null));
        context.externalFunctionNames().stream().sorted().forEach(name -> rows.add(new PipelinePlans.Row(context.displayName(name), Map.of("externalFunction", context.displayName(name)), null)));
        context.externalObjectNames().stream().sorted().forEach(name -> rows.add(new PipelinePlans.Row(context.displayName(name), Map.of("externalObject", context.displayName(name)), null)));
        var source = input == null ? PipelinePlans.empty("AST") : AstPresentationMapper.highlight(input, context.currentAstNode());
        return new StageProjection(source, PipelinePlans.sequence("IR", rows).highlightRange(observation.result().sourceRange()));
    }
}
