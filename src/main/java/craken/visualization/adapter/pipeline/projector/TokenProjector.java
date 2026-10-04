package craken.visualization.adapter.pipeline.projector;

import craken.compiler.*;
import craken.compiler.lexer.*;
import craken.visualization.adapter.pipeline.*;
import java.util.*;

public final class TokenProjector implements PipelineStageProjector<LexerResult> {
    @Override public Class<? extends Stage> stageType() { return Lexer.class; }
    @Override public Class<LexerResult> contextType() { return LexerResult.class; }
    @Override public StageProjection project(PipelineStepObservation observation, LexerResult context, PipelineProjectionPlan input) {
        var rows = context.tokens().stream().map(token -> {
            var fields = new LinkedHashMap<String, String>();
            fields.put("type", token.type().name()); fields.put("lexeme", token.lexeme());
            token.literalValueOptional().ifPresent(value -> fields.put("literal", String.valueOf(value)));
            return new PipelinePlans.Row(token.type().name() + " " + token.lexeme(), fields, token.range());
        }).toList();
        var output = PipelinePlans.sequence("Token", rows).highlightRange(observation.result().sourceRange());
        return new StageProjection((input == null ? PipelinePlans.empty("输入") : input).highlightRange(observation.result().sourceRange()), output);
    }
}
