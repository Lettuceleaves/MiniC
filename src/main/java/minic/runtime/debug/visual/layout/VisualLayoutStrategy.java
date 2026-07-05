package minic.runtime.debug.visual.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for layout strategies that compute final node and edge geometry.
 */
public abstract class VisualLayoutStrategy {
    public final LayoutPlan build(LayoutInput input) {
        Objects.requireNonNull(input, "input");
        LinkedHashMap<String, NodeMeasure> measures = new LinkedHashMap<>();
        for (VisualMemoryNode node : input.mirror().nodes()) {
            measures.put(node.address(), measureNode(input, node));
        }
        Map<String, GridRect> placements = placeNodes(input, Collections.unmodifiableMap(measures));
        LinkedHashMap<String, PlacedNode> placedNodes = new LinkedHashMap<>();
        for (Map.Entry<String, GridRect> entry : placements.entrySet()) {
            NodeMeasure measure = measures.get(entry.getKey());
            VisualMemoryNode node = input.mirror().node(entry.getKey()).orElse(null);
            if (measure != null && node != null) {
                placedNodes.put(entry.getKey(), new PlacedNode(entry.getKey(), entry.getValue(), measure, node.role()));
            }
        }
        ArrayList<PlacedEdge> placedEdges = new ArrayList<>();
        for (VisualMemoryEdge edge : input.mirror().edgesWithPlacedEndpoints(placedNodes)) {
            PlacedNode from = placedNodes.get(edge.fromAddress());
            PlacedNode to = placedNodes.get(edge.toAddress());
            placedEdges.add(placeEdge(input, edge, from, to));
        }
        return new LayoutPlan(input.roots(), placedNodes.values().stream().toList(), placedEdges);
    }

    protected abstract Map<String, GridRect> placeNodes(LayoutInput input, Map<String, NodeMeasure> measures);

    protected NodeMeasure measureNode(LayoutInput input, VisualMemoryNode node) {
        if (node.role() == VisualMemoryNodeRole.ARRAY_CELL) {
            return new NodeMeasure(2, 2);
        }
        int visualRows = metadataInt(node, "visual-row-count", 0);
        if (visualRows > 0) {
            return new NodeMeasure(4, Math.max(2, visualRows * 2));
        }
        int dataCells = Math.max(1, (node.byteCount() + 7) / 8);
        return new NodeMeasure(Math.max(2, dataCells * 2), 2);
    }

    protected PlacedEdge placeEdge(LayoutInput input, VisualMemoryEdge edge, PlacedNode from, PlacedNode to) {
        AnchorPair pair = closestAnchorPair(input, from, to);
        return new PlacedEdge(
                edge.fromAddress(),
                edge.toAddress(),
                pair.fromAnchor(),
                pair.toAnchor(),
                pair.fromAnchor().point(from.bounds()),
                pair.toAnchor().point(to.bounds())
        );
    }

    private AnchorPair closestAnchorPair(LayoutInput input, PlacedNode from, PlacedNode to) {
        List<NodeAnchor> fromAnchors = anchorsFor(input, from);
        List<NodeAnchor> toAnchors = anchorsFor(input, to);
        List<AnchorPair> candidates = new ArrayList<>();
        for (NodeAnchor fromAnchor : fromAnchors) {
            for (NodeAnchor toAnchor : toAnchors) {
                candidates.add(new AnchorPair(fromAnchor, toAnchor));
            }
        }
        return candidates.stream()
                .min(Comparator.comparingInt(pair -> distance(
                        pair.fromAnchor().point(from.bounds()),
                        pair.toAnchor().point(to.bounds())
                )))
                .orElseThrow();
    }

    private int distance(GridPoint start, GridPoint end) {
        return Math.abs(start.x() - end.x()) + Math.abs(start.y() - end.y());
    }

    private List<NodeAnchor> anchorsFor(LayoutInput input, PlacedNode node) {
        if (node.role() == VisualMemoryNodeRole.ARRAY_CELL) {
            return exposedArrayCellAnchors(input, node);
        }
        return List.of(NodeAnchor.TOP, NodeAnchor.RIGHT, NodeAnchor.BOTTOM, NodeAnchor.LEFT);
    }

    private List<NodeAnchor> exposedArrayCellAnchors(LayoutInput input, PlacedNode node) {
        VisualMemoryNode memoryNode = input.mirror().node(node.address()).orElse(null);
        if (memoryNode == null) {
            return List.of(NodeAnchor.TOP, NodeAnchor.RIGHT, NodeAnchor.BOTTOM, NodeAnchor.LEFT);
        }
        int row = metadataInt(memoryNode, "row", 0);
        int column = metadataInt(memoryNode, "column", 0);
        int rows = Math.max(1, metadataInt(memoryNode, "rows", 1));
        int columns = Math.max(1, metadataInt(memoryNode, "columns", 1));
        ArrayList<NodeAnchor> anchors = new ArrayList<>();
        if (row == 0) {
            anchors.add(NodeAnchor.TOP);
        }
        if (column + 1 >= columns) {
            anchors.add(NodeAnchor.RIGHT);
        }
        if (row + 1 >= rows) {
            anchors.add(NodeAnchor.BOTTOM);
        }
        if (column == 0) {
            anchors.add(NodeAnchor.LEFT);
        }
        if (anchors.isEmpty()) {
            return List.of(NodeAnchor.TOP, NodeAnchor.RIGHT, NodeAnchor.BOTTOM, NodeAnchor.LEFT);
        }
        return anchors;
    }

    private int metadataInt(VisualMemoryNode node, String key, int fallback) {
        try {
            return Integer.parseInt(node.metadata().getOrDefault(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record AnchorPair(NodeAnchor fromAnchor, NodeAnchor toAnchor) {
    }
}
