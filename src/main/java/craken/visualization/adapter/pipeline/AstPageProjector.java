package craken.visualization.adapter.pipeline;

import craken.compiler.parser.node.*;
import craken.compiler.type.CrakenType;
import craken.visualization.model.ViewNode;
import craken.visualization.model.relation.TopologyEdge.Direction;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/** Enumerates AST business components without changing semantic AstChildren traversal. */
public final class AstPageProjector {
    private AstPageProjector() {}
    public static PipelineProjectionPlan project(AstNode root, AstNode current) {
        Objects.requireNonNull(root);
        var nodes = new ArrayList<PipelineProjectionPlan.Node>();
        var edges = new LinkedHashSet<PipelineProjectionPlan.Edge>();
        var aliases = new IdentityHashMap<AstNode, ProjectionKey>();
        var queue = new ArrayDeque<AstNode>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<AstNode, Boolean>());
        queue.add(root);
        while (!queue.isEmpty()) {
            AstNode node = queue.removeFirst();
            if (!visited.add(node)) continue;
            var key = new ProjectionKey.Ast(node);
            aliases.put(node, key);
            var fields = new LinkedHashMap<String, String>();
            var children = new ArrayList<AstNode>();
            for (var component : AstNodeComponents.describe(node.getClass()).components()) {
                Object value = read(component, node);
                if (value instanceof String || value instanceof Number || value instanceof Boolean
                        || value instanceof Character || value instanceof Enum<?> || value instanceof CrakenType)
                    fields.put(component.name(), String.valueOf(value));
                collectChildren(value, children, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
            String detail = fields.getOrDefault("name", fields.getOrDefault("operator", fields.getOrDefault("value", "")));
            String label = node.getClass().getSimpleName() + (detail.isEmpty() ? "" : " · " + detail);
            nodes.add(new PipelineProjectionPlan.Node(key, new ViewNode.Spec(ViewNode.Kind.TREE, label, fields), node.range()));
            for (AstNode child : children) {
                edges.add(new PipelineProjectionPlan.Edge(key, new ProjectionKey.Ast(child), Direction.FORWARD));
                queue.addLast(child);
            }
        }
        ProjectionKey selected = aliases.get(current);
        return new PipelineProjectionPlan(PipelinePageTypes.ast(), nodes, List.copyOf(edges), List.of(),
                selected == null ? Set.of() : Set.of(selected), selected == null ? aliases.get(root) : selected, aliases);
    }
    private static Object read(AstNodeComponents.Component component, Object target) {
        try { return component.accessor().invoke(target); }
        catch (IllegalAccessException | InvocationTargetException error) { throw new IllegalArgumentException("Cannot read AST business component", error); }
    }
    private static void collectChildren(Object value, List<AstNode> target, Set<Object> visited) {
        if (value == null || !visited.add(value)) return;
        if (value instanceof AstNode node) { target.add(node); return; }
        if (value instanceof Iterable<?> values) { for (Object item : values) collectChildren(item, target, visited); return; }
        if (value instanceof Optional<?> optional) { optional.ifPresent(item -> collectChildren(item, target, visited)); return; }
        String packageName = value.getClass().getPackageName();
        if (value.getClass().isRecord() && (packageName.equals("craken.compiler.parser.node") || packageName.equals("craken.compiler.type")))
            for (var component : AstNodeComponents.describe(value.getClass()).components()) collectChildren(read(component, value), target, visited);
    }
}
