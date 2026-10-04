package craken.visualization.adapter.pipeline;

import craken.SourceRange;
import craken.visualization.model.ViewNode;
import java.util.*;

/** Shared immutable sequence construction for stage projectors. */
public final class PipelinePlans {
    private PipelinePlans() {}
    public record Row(String label, Map<String, String> fields, SourceRange range) {
        public Row { fields = Map.copyOf(fields); }
    }
    public static PipelineProjectionPlan sequence(String title, List<Row> rows) {
        var nodes = new ArrayList<PipelineProjectionPlan.Node>();
        var links = new ArrayList<PipelineProjectionPlan.Composition>();
        var array = new ProjectionKey.Named(title);
        nodes.add(new PipelineProjectionPlan.Node(array, new ViewNode.Spec(ViewNode.Kind.ARRAY, title), null));
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            var key = new ProjectionKey.Indexed(title, index);
            nodes.add(new PipelineProjectionPlan.Node(key, new ViewNode.Spec(ViewNode.Kind.POINT, row.label(), row.fields()), row.range()));
            links.add(new PipelineProjectionPlan.Composition(array, key, index));
        }
        return new PipelineProjectionPlan(PipelinePageTypes.array(), nodes, List.of(), links, Set.of(), null);
    }
    public static PipelineProjectionPlan empty(String title) { return sequence(title, List.of()); }
}
