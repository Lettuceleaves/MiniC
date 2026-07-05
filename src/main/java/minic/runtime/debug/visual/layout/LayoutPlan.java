package minic.runtime.debug.visual.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Final geometry consumed by drawing infrastructure.
 *
 * @param roots ordered root memory references
 * @param nodes placed nodes
 * @param edges placed edges
 */
public record LayoutPlan(List<String> roots, List<PlacedNode> nodes, List<PlacedEdge> edges) {
    public LayoutPlan {
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        roots = List.copyOf(roots);
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public List<String> breadthFirstNodeIds() {
        LinkedHashMap<String, PlacedNode> placed = new LinkedHashMap<>();
        for (PlacedNode node : nodes) {
            placed.putIfAbsent(node.address(), node);
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String root : roots) {
            if (placed.containsKey(root)) {
                queue.add(root);
            }
        }
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (PlacedEdge edge : edges) {
                if (edge.fromAddress().equals(current)
                        && placed.containsKey(edge.toAddress())
                        && !visited.contains(edge.toAddress())) {
                    queue.add(edge.toAddress());
                }
            }
        }
        return new ArrayList<>(visited);
    }
}
