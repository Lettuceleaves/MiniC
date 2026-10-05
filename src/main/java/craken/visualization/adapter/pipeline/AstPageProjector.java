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
    /** Huge expanded headers stay usable: the focus path stays visible, the rest is bounded and summarized. */
    private static final int MAX_AST_NODES = 2000;
    public static PipelineProjectionPlan project(AstNode root, AstNode current) {
        Objects.requireNonNull(root);
        var order = new ArrayList<AstNode>();
        var children = new IdentityHashMap<AstNode, List<AstNode>>();
        var parents = new IdentityHashMap<AstNode, AstNode>();
        var labels = new IdentityHashMap<AstNode, String>();
        var fieldMaps = new IdentityHashMap<AstNode, Map<String, String>>();
        var queue = new ArrayDeque<AstNode>();
        // An AST is a tree: the first discovery of an object keeps its single parent edge. Later
        // references from other parents (shared objects, back links) must not add diamond edges,
        // which would degrade the page into a graph layout.
        var discovered = Collections.newSetFromMap(new IdentityHashMap<AstNode, Boolean>());
        discovered.add(root);
        queue.add(root);
        while (!queue.isEmpty()) {
            AstNode node = queue.removeFirst();
            order.add(node);
            var fields = new LinkedHashMap<String, String>();
            var direct = new ArrayList<AstNode>();
            for (var component : AstNodeComponents.describe(node.getClass()).components()) {
                Object value = read(component, node);
                if (value instanceof String || value instanceof Number || value instanceof Boolean
                        || value instanceof Character || value instanceof Enum<?> || value instanceof CrakenType)
                    fields.put(component.name(), String.valueOf(value));
                collectChildren(value, direct, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
            String detail = fields.getOrDefault("name", fields.getOrDefault("operator", fields.getOrDefault("value", "")));
            labels.put(node, node.getClass().getSimpleName() + (detail.isEmpty() ? "" : " · " + detail));
            fieldMaps.put(node, fields);
            var treeChildren = new ArrayList<AstNode>();
            for (AstNode child : direct) {
                if (!discovered.add(child)) continue;
                treeChildren.add(child);
                parents.put(child, node);
                queue.addLast(child);
            }
            children.put(node, List.copyOf(treeChildren));
        }
        var visible = visible(order, children, parents, current);
        var nodes = new ArrayList<PipelineProjectionPlan.Node>();
        var edges = new ArrayList<PipelineProjectionPlan.Edge>();
        var aliases = new IdentityHashMap<AstNode, ProjectionKey>();
        Map<AstNode, Integer> collapsed = order.size() <= MAX_AST_NODES ? Map.of()
                : collapsedCounts(order, children, visible);
        for (AstNode node : order) {
            if (!visible.contains(node)) continue;
            var key = new ProjectionKey.Ast(node);
            aliases.put(node, key);
            var fields = new LinkedHashMap<>(fieldMaps.get(node));
            Integer hidden = collapsed.get(node);
            if (hidden != null && hidden > 0) fields.put("collapsed", String.valueOf(hidden));
            nodes.add(new PipelineProjectionPlan.Node(key, PipelinePlans.card(ViewNode.Kind.TREE, labels.get(node), fields), node.range()));
            for (AstNode child : children.get(node)) if (visible.contains(child))
                edges.add(new PipelineProjectionPlan.Edge(key, new ProjectionKey.Ast(child), Direction.FORWARD));
        }
        ProjectionKey selected = aliases.get(current);
        return new PipelineProjectionPlan(PipelinePageTypes.ast(), nodes, List.copyOf(edges), List.of(),
                selected == null ? Set.of() : Set.of(selected), selected == null ? aliases.get(root) : selected, aliases);
    }

    /** Focus ancestors first, then the breadth-first prefix, up to the display budget. */
    private static Set<AstNode> visible(List<AstNode> order, IdentityHashMap<AstNode, List<AstNode>> children,
                                        IdentityHashMap<AstNode, AstNode> parents, AstNode current) {
        var visible = Collections.newSetFromMap(new IdentityHashMap<AstNode, Boolean>());
        if (order.size() <= MAX_AST_NODES) { visible.addAll(order); return visible; }
        for (AstNode node = current; node != null && !visible.contains(node); node = parents.get(node)) visible.add(node);
        for (AstNode node : order) if (visible.size() < MAX_AST_NODES) visible.add(node); else break;
        return visible;
    }

    /** Hidden descendant counts for the summarized "collapsed" annotation. */
    private static Map<AstNode, Integer> collapsedCounts(List<AstNode> order, IdentityHashMap<AstNode, List<AstNode>> children,
                                                         Set<AstNode> visible) {
        var subtree = new IdentityHashMap<AstNode, Integer>();
        var visibleSubtree = new IdentityHashMap<AstNode, Integer>();
        for (int index = order.size() - 1; index >= 0; index--) {
            AstNode node = order.get(index);
            int total = 1, shown = visible.contains(node) ? 1 : 0;
            for (AstNode child : children.get(node)) {
                total += subtree.getOrDefault(child, 0);
                shown += visibleSubtree.getOrDefault(child, 0);
            }
            subtree.put(node, total);
            visibleSubtree.put(node, shown);
        }
        var collapsed = new IdentityHashMap<AstNode, Integer>();
        for (AstNode node : order) if (visible.contains(node)) {
            int hidden = subtree.get(node) - visibleSubtree.get(node);
            if (hidden > 0) collapsed.put(node, hidden);
        }
        return collapsed;
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
