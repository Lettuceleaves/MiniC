package craken.visualization.adapter.pipeline;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.node.*;
import java.util.Set;

/** Explicit registration for pipeline arrays and AST pages. */
public final class PipelinePageTypes {
    private PipelinePageTypes() {}
    public static PageType array() { return new Type("pipeline-array", PageType.Layout.ARRAY, Set.of(ViewNode.Kind.ARRAY, ViewNode.Kind.POINT)); }
    public static PageType ast() { return new Type("pipeline-ast", PageType.Layout.TREE, Set.of(ViewNode.Kind.TREE, ViewNode.Kind.POINT)); }
    private record Type(String key, Layout layout, Set<ViewNode.Kind> nodeKinds) implements PageType {
        @Override public boolean readyEnabled() { return false; }
        @Override public int maximumNesting() { return 0; }
        @Override public ViewNode create(ViewLocation location, ViewNode.Spec spec, ViewNode.Retention retention, ParentSelection parents) {
            return switch (spec.kind()) {
                case ARRAY -> new PipelineArrayViewNode(location, spec, retention, parents);
                case TREE -> new TreeViewNode(location, spec, retention, parents);
                case POINT -> new PointViewNode(location, spec, retention, parents);
                default -> throw new IllegalArgumentException("Unsupported pipeline node kind");
            };
        }
    }
}
