package craken.visualization.adapter.pipeline;

import craken.SourceRange;
import craken.visualization.model.ViewNode;
import craken.visualization.style.ColorSpec;
import java.util.*;

/** Shared immutable sequence construction for stage projectors. */
public final class PipelinePlans {
    private PipelinePlans() {}
    /** Large stage outputs stay usable: keep head and tail rows plus one explicit summary row. */
    private static final int MAX_SEQUENCE_ROWS = 1500;
    /** Pipeline cards use the approved IDE card surface: neutral fill with the demo outline and selection. */
    public static ViewNode.Spec card(ViewNode.Kind kind, String label, Map<String, String> fields) {
        return new ViewNode.Spec(kind, label, fields, ColorSpec.Preset.NEUTRAL);
    }
    public record Row(String label, Map<String, String> fields, SourceRange range) {
        public Row { fields = Map.copyOf(fields); }
    }
    public static PipelineProjectionPlan sequence(String title, List<Row> rows) {
        if (rows.size() > MAX_SEQUENCE_ROWS) {
            int half = MAX_SEQUENCE_ROWS / 2;
            var shown = new ArrayList<Row>(MAX_SEQUENCE_ROWS + 1);
            shown.addAll(rows.subList(0, half));
            shown.add(new Row("… 共 " + rows.size() + " 项 · 省略中间 " + (rows.size() - MAX_SEQUENCE_ROWS) + " 项",
                    Map.of("omitted", String.valueOf(rows.size() - MAX_SEQUENCE_ROWS)), null));
            shown.addAll(rows.subList(rows.size() - half, rows.size()));
            rows = shown;
        }
        var nodes = new ArrayList<PipelineProjectionPlan.Node>();
        var links = new ArrayList<PipelineProjectionPlan.Composition>();
        var array = new ProjectionKey.Named(title);
        nodes.add(new PipelineProjectionPlan.Node(array, card(ViewNode.Kind.ARRAY, title, Map.of()), null));
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            var key = new ProjectionKey.Indexed(title, index);
            nodes.add(new PipelineProjectionPlan.Node(key, card(ViewNode.Kind.POINT, row.label(), row.fields()), row.range()));
            links.add(new PipelineProjectionPlan.Composition(array, key, index));
        }
        return new PipelineProjectionPlan(PipelinePageTypes.array(), nodes, List.of(), links, Set.of(), null);
    }
    public static PipelineProjectionPlan empty(String title) { return sequence(title, List.of()); }
}
