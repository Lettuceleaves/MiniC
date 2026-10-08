package craken.visualization.type;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.node.*;
import java.util.Set;

public final class BuiltinPageTypes {
    private BuiltinPageTypes() {}
    public static PageType point() { return new Basic("point", false, 0, PageType.Layout.POINT, Set.of(ViewNode.Kind.POINT)); }
    public static PageType array() { return array(0); }
    public static PageType array(int nesting) {
        return new Basic("array-" + nesting, false, nesting, PageType.Layout.ARRAY, Set.of(ViewNode.Kind.POINT, ViewNode.Kind.ARRAY));
    }
    public static PageType linked(boolean doubled) {
        return new Basic(doubled ? "doubly-linked" : "linked", true, 0, PageType.Layout.LINEAR,
                Set.of(ViewNode.Kind.LINKED, ViewNode.Kind.POINT));
    }
    public static PageType tree(boolean redBlack) {
        return new Basic(redBlack ? "red-black-tree" : "tree", true, 0, PageType.Layout.TREE,
                Set.of(ViewNode.Kind.TREE, ViewNode.Kind.POINT));
    }
    public static PageType graph(boolean directed) {
        return new Basic(directed ? "directed-graph" : "undirected-graph", true, 0, PageType.Layout.STRESS,
                Set.of(ViewNode.Kind.GRAPH, ViewNode.Kind.POINT));
    }
    /** Chained bucket array: a contiguous bucket list plus one chain of entry nodes per bucket. */
    public static PageType buckets() {
        return new Basic("buckets", true, 1, PageType.Layout.BUCKETS,
                Set.of(ViewNode.Kind.ARRAY, ViewNode.Kind.POINT, ViewNode.Kind.LINKED, ViewNode.Kind.TREE));
    }
    public static PageType composite(String key, PageType.Layout layout, boolean ready) {
        return new Basic(key, ready, 1, layout, Set.of(ViewNode.Kind.values()));
    }
    private record Basic(String key, boolean readyEnabled, int maximumNesting, Layout layout,
                         Set<ViewNode.Kind> nodeKinds) implements PageType {
        @Override public ViewNode create(ViewLocation location, ViewNode.Spec spec, ViewNode.Retention retention,
                                         ParentSelection parents) {
            return switch (spec.kind()) {
                case POINT -> new PointViewNode(location, spec, retention, parents);
                case ARRAY -> new ArrayViewNode(location, spec, retention, parents);
                case LINKED -> new LinkedViewNode(location, spec, retention, parents);
                case TREE -> new TreeViewNode(location, spec, retention, parents);
                case GRAPH -> new GraphViewNode(location, spec, retention, parents);
            };
        }
    }
}
