package minic.runtime.debug.visual.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Places reachable nodes in one downward direction.
 *
 * <p>Each BFS layer is horizontally centered on the y-axis. Adjacent nodes in
 * a layer keep a four-grid gap, and each layer starts four grid units below
 * the previous layer's bottom.</p>
 */
public final class UnidirectionalLayoutStrategy extends VisualLayoutStrategy {
    private static final int NODE_GAP = 4;
    private static final int LAYER_GAP = 4;

    @Override
    protected Map<String, GridRect> placeNodes(LayoutInput input, Map<String, NodeMeasure> measures) {
        List<List<String>> layers = layers(input);
        LinkedHashMap<String, GridRect> placements = new LinkedHashMap<>();
        int y = 0;
        for (List<String> layer : layers) {
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
            y += layerHeight + LAYER_GAP;
        }
        return placements;
    }

    @Override
    protected PlacedEdge placeEdge(LayoutInput input, VisualMemoryEdge edge, PlacedNode from, PlacedNode to) {
        if (to.bounds().y() > from.bounds().y()) {
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
}
