package craken.visualization.adapter.pipeline;

import craken.SourceRange;
import craken.compiler.parser.node.AstNode;
import craken.visualization.api.PageType;
import craken.visualization.model.ViewNode;
import craken.visualization.model.relation.TopologyEdge.Direction;
import java.util.*;

/** Immutable desired page content, independent of GUI and live AST slots. */
public record PipelineProjectionPlan(PageType pageType, List<Node> nodes, List<Edge> edges,
        List<Composition> composition, Set<ProjectionKey> highlights, ProjectionKey focus,
        Map<AstNode, ProjectionKey> aliases) {
    public PipelineProjectionPlan(PageType type, List<Node> nodes, List<Edge> edges,
            List<Composition> composition, Set<ProjectionKey> highlights, ProjectionKey focus) {
        this(type, nodes, edges, composition, highlights, focus, Map.of());
    }
    public PipelineProjectionPlan {
        Objects.requireNonNull(pageType);
        nodes = List.copyOf(nodes); edges = List.copyOf(edges); composition = List.copyOf(composition);
        highlights = Set.copyOf(highlights);
        var keys = new HashSet<ProjectionKey>();
        for (Node node : nodes) if (!keys.add(node.key())) throw new IllegalArgumentException("duplicate projection key");
        for (Edge edge : edges) if (!keys.contains(edge.parent()) || !keys.contains(edge.child()))
            throw new IllegalArgumentException("edge endpoint absent");
        for (Composition link : composition) if (!keys.contains(link.parent()) || !keys.contains(link.child()))
            throw new IllegalArgumentException("composition endpoint absent");
        if (!keys.containsAll(highlights) || focus != null && !keys.contains(focus))
            throw new IllegalArgumentException("highlight/focus absent");
        aliases = Collections.unmodifiableMap(new IdentityHashMap<>(aliases));
        if (!keys.containsAll(aliases.values())) throw new IllegalArgumentException("alias endpoint absent");
    }
    public record Node(ProjectionKey key, ViewNode.Spec content, SourceRange range) {
        public Node { Objects.requireNonNull(key); Objects.requireNonNull(content); }
    }
    public record Edge(ProjectionKey parent, ProjectionKey child, Direction direction) {
        public Edge { Objects.requireNonNull(parent); Objects.requireNonNull(child); Objects.requireNonNull(direction); }
    }
    public record Composition(ProjectionKey parent, ProjectionKey child, int slot) {
        public Composition { Objects.requireNonNull(parent); Objects.requireNonNull(child); if (slot < 0) throw new IllegalArgumentException("negative slot"); }
    }
    public PipelineProjectionPlan highlighted(Set<ProjectionKey> keys) {
        ProjectionKey selected = nodes.stream().map(Node::key).filter(keys::contains).findFirst().orElse(null);
        return new PipelineProjectionPlan(pageType, nodes, edges, composition, keys, selected, aliases);
    }
    public PipelineProjectionPlan highlightRange(SourceRange range) {
        if (range == null) return highlighted(Set.of());
        var keys = new LinkedHashSet<ProjectionKey>();
        for (Node node : nodes) if (node.range() != null && overlaps(node.range(), range)) keys.add(node.key());
        return highlighted(keys);
    }
    private static boolean overlaps(SourceRange a, SourceRange b) {
        boolean pointA = compare(a.startLine(), a.startByte(), a.endLine(), a.endByte()) == 0;
        boolean pointB = compare(b.startLine(), b.startByte(), b.endLine(), b.endByte()) == 0;
        if (pointA && pointB) return compare(a.startLine(), a.startByte(), b.startLine(), b.startByte()) == 0;
        if (pointA) return compare(b.startLine(), b.startByte(), a.startLine(), a.startByte()) <= 0
                && compare(a.startLine(), a.startByte(), b.endLine(), b.endByte()) < 0;
        if (pointB) return compare(a.startLine(), a.startByte(), b.startLine(), b.startByte()) <= 0
                && compare(b.startLine(), b.startByte(), a.endLine(), a.endByte()) < 0;
        return compare(a.startLine(), a.startByte(), b.endLine(), b.endByte()) < 0
                && compare(b.startLine(), b.startByte(), a.endLine(), a.endByte()) < 0;
    }
    private static int compare(int lineA, int byteA, int lineB, int byteB) {
        int lines = Integer.compare(lineA, lineB); return lines != 0 ? lines : Integer.compare(byteA, byteB);
    }
}
