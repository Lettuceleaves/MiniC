package minic.runtime.debug.visual.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Places typed memory nodes by natural reachability from root values.
 */
public final class NaturalLayoutStrategy extends VisualLayoutStrategy {
    private static final int NODE_GAP = 4;
    private static final int LAYER_GAP = 4;

    @Override
    protected Map<String, GridRect> placeNodes(LayoutInput input, Map<String, NodeMeasure> measures) {
        List<List<String>> layers = layers(input);
        LinkedHashMap<String, GridRect> placements = new LinkedHashMap<>();
        int y = 0;
        for (List<String> layer : layers) {
            int layerHeight = hasArrayCells(input, layer)
                    ? placeArrayLayer(input, measures, placements, layer, y)
                    : placeObjectLayer(measures, placements, layer, y);
            y += layerHeight + LAYER_GAP;
        }
        return placements;
    }

    @Override
    protected PlacedEdge placeEdge(LayoutInput input, VisualMemoryEdge edge, PlacedNode from, PlacedNode to) {
        if (from.role() == VisualMemoryNodeRole.OBJECT && to.bounds().y() > from.bounds().y()) {
            return new PlacedEdge(
                    edge.fromAddress(),
                    edge.toAddress(),
                    NodeAnchor.BOTTOM,
                    NodeAnchor.TOP,
                    NodeAnchor.BOTTOM.point(from.bounds()),
                    NodeAnchor.TOP.point(to.bounds())
            );
        }
        return super.placeEdge(input, edge, from, to);
    }

    private int placeObjectLayer(
            Map<String, NodeMeasure> measures,
            LinkedHashMap<String, GridRect> placements,
            List<String> layer,
            int y
    ) {
        int layerHeight = layer.stream()
                .map(measures::get)
                .mapToInt(NodeMeasure::height)
                .max()
                .orElse(0);
        int totalWidth = layerWidth(layer, measures);
        int x = -totalWidth / 2;
        for (String address : layer) {
            NodeMeasure measure = measures.get(address);
            placements.put(address, new GridRect(x, y, measure.width(), measure.height()));
            x += measure.width() + NODE_GAP;
        }
        return layerHeight;
    }

    private int placeArrayLayer(
            LayoutInput input,
            Map<String, NodeMeasure> measures,
            LinkedHashMap<String, GridRect> placements,
            List<String> layer,
            int y
    ) {
        List<String> arrayCells = layer.stream()
                .filter(address -> input.mirror().node(address)
                        .map(node -> node.role() == VisualMemoryNodeRole.ARRAY_CELL)
                        .orElse(false))
                .toList();
        int maxRow = arrayCells.stream()
                .map(input.mirror()::node)
                .flatMap(java.util.Optional::stream)
                .mapToInt(node -> metadataInt(node, "row", 0))
                .max()
                .orElse(0);
        int maxColumn = arrayCells.stream()
                .map(input.mirror()::node)
                .flatMap(java.util.Optional::stream)
                .mapToInt(node -> metadataInt(node, "column", 0))
                .max()
                .orElse(0);
        int cellWidth = arrayCells.stream().map(measures::get).mapToInt(NodeMeasure::width).max().orElse(2);
        int cellHeight = arrayCells.stream().map(measures::get).mapToInt(NodeMeasure::height).max().orElse(2);
        int totalWidth = (maxColumn + 1) * cellWidth;
        int x0 = -totalWidth / 2;
        for (String address : arrayCells) {
            VisualMemoryNode node = input.mirror().node(address).orElseThrow();
            int row = metadataInt(node, "row", 0);
            int column = metadataInt(node, "column", 0);
            placements.put(address, new GridRect(x0 + column * cellWidth, y + row * cellHeight, cellWidth, cellHeight));
        }
        return (maxRow + 1) * cellHeight;
    }

    private boolean hasArrayCells(LayoutInput input, List<String> layer) {
        return layer.stream()
                .map(input.mirror()::node)
                .flatMap(java.util.Optional::stream)
                .anyMatch(node -> node.role() == VisualMemoryNodeRole.ARRAY_CELL);
    }

    private List<List<String>> layers(LayoutInput input) {
        ArrayList<List<String>> layers = new ArrayList<>();
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> current = new ArrayDeque<>();
        for (String root : input.roots()) {
            if (input.mirror().node(root).isPresent()) {
                current.add(root);
            }
        }
        while (!current.isEmpty()) {
            ArrayList<String> layer = new ArrayList<>();
            ArrayDeque<String> next = new ArrayDeque<>();
            while (!current.isEmpty()) {
                String address = current.removeFirst();
                if (!visited.add(address)) {
                    continue;
                }
                layer.add(address);
                for (VisualMemoryEdge edge : input.mirror().outgoingEdges(address)) {
                    if (!visited.contains(edge.toAddress()) && input.mirror().node(edge.toAddress()).isPresent()) {
                        next.add(edge.toAddress());
                    }
                }
            }
            if (!layer.isEmpty()) {
                layers.add(List.copyOf(layer));
            }
            current = next;
        }
        return layers;
    }

    private int layerWidth(List<String> layer, Map<String, NodeMeasure> measures) {
        int width = 0;
        for (int i = 0; i < layer.size(); i++) {
            NodeMeasure measure = measures.get(layer.get(i));
            width += measure.width();
            if (i + 1 < layer.size()) {
                width += NODE_GAP;
            }
        }
        return width;
    }

    private int metadataInt(VisualMemoryNode node, String key, int fallback) {
        try {
            return Integer.parseInt(node.metadata().getOrDefault(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
