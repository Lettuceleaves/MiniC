package craken.visualization.adapter.pipeline;

import craken.compiler.parser.node.AstNode;
import java.util.*;

/** Candidate source/core aliases point to the same core display identity without copying slots. */
public final class AstPresentationMapper {
    private AstPresentationMapper() {}
    public static PipelineProjectionPlan withSourceAliases(PipelineProjectionPlan core, Map<AstNode, AstNode> sourceToCore) {
        var aliases = new IdentityHashMap<>(core.aliases());
        sourceToCore.forEach((source, node) -> {
            ProjectionKey key = core.aliases().get(node);
            if (key != null) aliases.put(source, key);
        });
        return new PipelineProjectionPlan(core.pageType(), core.nodes(), core.edges(), core.composition(), core.highlights(), core.focus(), aliases);
    }
    public static PipelineProjectionPlan highlight(PipelineProjectionPlan plan, AstNode node) {
        ProjectionKey key = plan.aliases().get(node);
        return plan.highlighted(key == null ? Set.of() : Set.of(key));
    }
}
