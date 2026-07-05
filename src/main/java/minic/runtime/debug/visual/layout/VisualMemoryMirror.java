package minic.runtime.debug.visual.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Memory nodes and pointer relations available to a layout strategy.
 *
 * @param nodes byte-span nodes
 * @param edges directed pointer relations
 */
public record VisualMemoryMirror(List<VisualMemoryNode> nodes, List<VisualMemoryEdge> edges) {
    public VisualMemoryMirror {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public Optional<VisualMemoryNode> node(String address) {
        Objects.requireNonNull(address, "address");
        return nodes.stream()
                .filter(node -> node.address().equals(address))
                .findFirst();
    }

    public List<VisualMemoryEdge> outgoingEdges(String address) {
        Objects.requireNonNull(address, "address");
        return edges.stream()
                .filter(edge -> edge.fromAddress().equals(address))
                .toList();
    }

    Map<String, VisualMemoryNode> nodesByAddress() {
        LinkedHashMap<String, VisualMemoryNode> result = new LinkedHashMap<>();
        for (VisualMemoryNode node : nodes) {
            result.putIfAbsent(node.address(), node);
        }
        return result;
    }

    List<VisualMemoryEdge> edgesWithPlacedEndpoints(Map<String, ?> placements) {
        ArrayList<VisualMemoryEdge> result = new ArrayList<>();
        for (VisualMemoryEdge edge : edges) {
            if (placements.containsKey(edge.fromAddress()) && placements.containsKey(edge.toAddress())) {
                result.add(edge);
            }
        }
        return result;
    }
}
