package minic.uilocal;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import minic.color.ThemeRegistry;
import minic.uiapi.UiDebugVisualElementDto;
import minic.uiapi.UiDebugVisualStructureDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

final class MiniCDebugVisualDiagramRenderer {
    private static final double VISUAL_CELL_SIZE = 44;
    private static final double VISUAL_NODE_RADIUS = VISUAL_CELL_SIZE / 2;
    private static final double VISUAL_NULL_SIZE = 16;
    private static final double VISUAL_GRID_GAP = 34;
    private static final double VISUAL_MARGIN = 24;

    private final BiConsumer<Node, UiDebugVisualElementDto> tooltipInstaller;

    MiniCDebugVisualDiagramRenderer(BiConsumer<Node, UiDebugVisualElementDto> tooltipInstaller) {
        this.tooltipInstaller = tooltipInstaller;
    }

    Node visualDiagram(UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> arrayCells = visual.elements().stream()
                .filter(element -> element.kind().equals("ARRAY_CELL"))
                .toList();
        if (!arrayCells.isEmpty()) {
            return arrayDiagram(arrayCells);
        }
        List<UiDebugVisualElementDto> graphNodes = visual.elements().stream()
                .filter(element -> element.kind().equals("GRAPH_NODE"))
                .toList();
        if (!graphNodes.isEmpty()) {
            List<UiDebugVisualElementDto> graphEdges = visual.elements().stream()
                    .filter(element -> element.kind().equals("GRAPH_EDGE"))
                    .toList();
            return graphDiagram(visual.kind(), visual.layoutHint(), graphNodes, graphEdges);
        }
        return null;
    }

    private Node arrayDiagram(List<UiDebugVisualElementDto> cells) {
        Pane pane = new Pane();
        pane.getStyleClass().add("debug-visual-diagram");
        double width = VISUAL_MARGIN * 2 + VISUAL_CELL_SIZE * cells.size();
        double height = VISUAL_MARGIN * 2 + VISUAL_CELL_SIZE;
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        for (int index = 0; index < cells.size(); index++) {
            UiDebugVisualElementDto cell = cells.get(index);
            double x = VISUAL_MARGIN + index * VISUAL_CELL_SIZE;
            double y = VISUAL_MARGIN;
            Rectangle rect = new Rectangle(x, y, VISUAL_CELL_SIZE, VISUAL_CELL_SIZE);
            rect.getStyleClass().add("debug-array-cell");
            Text text = visualText(shortLabel(cell.label()), x, y + 4, VISUAL_CELL_SIZE);
            tooltipInstaller.accept(rect, cell);
            tooltipInstaller.accept(text, cell);
            pane.getChildren().addAll(rect, text);
        }
        return pane;
    }

    private Node graphDiagram(
            String kind,
            String layoutHint,
            List<UiDebugVisualElementDto> nodes,
            List<UiDebugVisualElementDto> edges
    ) {
        Pane pane = new Pane();
        pane.getStyleClass().add("debug-visual-diagram");
        List<UiDebugVisualElementDto> visibleNodes = visibleGraphNodes(kind, layoutHint, nodes, edges);
        Map<String, UiDebugVisualElementDto> nodesById = visibleNodes.stream()
                .collect(Collectors.toMap(this::simpleVisualId, node -> node, (left, right) -> left, LinkedHashMap::new));
        Map<String, VisualPoint> positions = graphPositions(kind, layoutHint, visibleNodes, edges);
        double width = Math.max(220, positions.values().stream().mapToDouble(VisualPoint::x).max().orElse(160) + VISUAL_MARGIN);
        double height = Math.max(150, positions.values().stream().mapToDouble(VisualPoint::y).max().orElse(100) + VISUAL_MARGIN);
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        for (UiDebugVisualElementDto edge : edges) {
            VisualPoint from = positions.get(edge.metadata().getOrDefault("from", ""));
            VisualPoint to = positions.get(edge.metadata().getOrDefault("to", ""));
            if (from != null && to != null) {
                List<Node> arrow = arrow(from, to, isNullNode(nodesById, edge.metadata().getOrDefault("to", "")));
                arrow.forEach(node -> tooltipInstaller.accept(node, edge));
                pane.getChildren().addAll(arrow);
            }
        }
        for (UiDebugVisualElementDto node : visibleNodes) {
            VisualPoint point = positions.get(simpleVisualId(node));
            if (point == null) {
                continue;
            }
            if (isNullNode(node)) {
                Rectangle nullRect = new Rectangle(
                        point.x() - VISUAL_NULL_SIZE / 2,
                        point.y() - VISUAL_NULL_SIZE / 2,
                        VISUAL_NULL_SIZE,
                        VISUAL_NULL_SIZE
                );
                nullRect.getStyleClass().add("debug-null-node");
                nullRect.setAccessibleText(simpleVisualId(node));
                tooltipInstaller.accept(nullRect, node);
                pane.getChildren().add(nullRect);
                continue;
            }
            Circle circle = new Circle(point.x(), point.y(), VISUAL_NODE_RADIUS);
            circle.getStyleClass().add("debug-graph-node");
            circle.setAccessibleText(simpleVisualId(node));
            Text text = visualText(shortLabel(node.label()), point.x() - VISUAL_NODE_RADIUS, point.y() + 4, VISUAL_CELL_SIZE);
            tooltipInstaller.accept(circle, node);
            tooltipInstaller.accept(text, node);
            pane.getChildren().addAll(circle, text);
        }
        return pane;
    }

    private boolean isNullNode(Map<String, UiDebugVisualElementDto> nodesById, String id) {
        UiDebugVisualElementDto node = nodesById.get(id);
        return node != null && isNullNode(node);
    }

    private boolean isNullNode(UiDebugVisualElementDto node) {
        return Boolean.parseBoolean(node.metadata().getOrDefault("visual-null", "false"));
    }

    private List<UiDebugVisualElementDto> visibleGraphNodes(
            String kind,
            String layoutHint,
            List<UiDebugVisualElementDto> nodes,
            List<UiDebugVisualElementDto> edges
    ) {
        if (!isTreeLayout(kind, layoutHint)) {
            return nodes;
        }
        HashSet<String> nodeIds = new HashSet<>();
        nodes.forEach(node -> nodeIds.add(simpleVisualId(node)));
        HashSet<String> edgeNodeIds = new HashSet<>();
        edges.forEach(edge -> {
            String from = edge.metadata().get("from");
            String to = edge.metadata().get("to");
            if (from != null) {
                edgeNodeIds.add(from);
            }
            if (to != null) {
                edgeNodeIds.add(to);
            }
        });
        return nodes.stream()
                .filter(node -> {
                    String id = simpleVisualId(node);
                    String summary = node.metadata().get("summary");
                    return edgeNodeIds.contains(id) || summary == null || !nodeIds.contains(summary);
                })
                .toList();
    }

    private Map<String, VisualPoint> graphPositions(
            String kind,
            String layoutHint,
            List<UiDebugVisualElementDto> nodes,
            List<UiDebugVisualElementDto> edges
    ) {
        if (isTreeLayout(kind, layoutHint)) {
            return treePositions(nodes, edges);
        }
        if (isBucketedLayout(kind, layoutHint)) {
            return bucketedPositions(nodes);
        }
        LinkedHashMap<String, VisualPoint> positions = new LinkedHashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            String id = simpleVisualId(nodes.get(index));
            positions.put(id, new VisualPoint(
                    VISUAL_MARGIN + VISUAL_NODE_RADIUS + index * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP),
                    VISUAL_MARGIN + VISUAL_NODE_RADIUS
            ));
        }
        return positions;
    }

    private boolean isTreeLayout(String kind, String layoutHint) {
        return kind.equals("tree")
                || kind.equals("binary_tree")
                || kind.equals("binary-tree")
                || layoutHint.equals("hierarchical");
    }

    private boolean isBucketedLayout(String kind, String layoutHint) {
        return kind.equals("hash-chain-table")
                || kind.equals("adjacency-list")
                || layoutHint.equals("bucketed")
                || layoutHint.equals("bucket_graph");
    }

    private Map<String, VisualPoint> bucketedPositions(List<UiDebugVisualElementDto> nodes) {
        LinkedHashMap<String, VisualPoint> positions = new LinkedHashMap<>();
        List<UiDebugVisualElementDto> buckets = nodes.stream()
                .filter(node -> node.metadata().getOrDefault("visual-role", "").equals("bucket"))
                .sorted(Comparator.comparingInt(node -> metadataInt(node, "bucketIndex", Integer.MAX_VALUE)))
                .toList();
        LinkedHashMap<String, Double> bucketXByIndex = new LinkedHashMap<>();
        double bucketY = VISUAL_MARGIN + VISUAL_NODE_RADIUS;
        for (int index = 0; index < buckets.size(); index++) {
            UiDebugVisualElementDto bucket = buckets.get(index);
            int bucketIndex = metadataInt(bucket, "bucketIndex", index);
            double x = VISUAL_MARGIN + VISUAL_NODE_RADIUS + index * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP);
            bucketXByIndex.put(Integer.toString(bucketIndex), x);
            positions.put(simpleVisualId(bucket), new VisualPoint(x, bucketY));
        }
        nodes.stream()
                .filter(node -> node.metadata().getOrDefault("visual-role", "").equals("chain-node"))
                .sorted(Comparator
                        .comparingInt((UiDebugVisualElementDto node) -> metadataInt(node, "bucketIndex", Integer.MAX_VALUE))
                        .thenComparingInt(node -> metadataInt(node, "chainDepth", Integer.MAX_VALUE)))
                .forEach(node -> {
                    String bucketIndex = node.metadata().getOrDefault("bucketIndex", "0");
                    int chainDepth = metadataInt(node, "chainDepth", 0);
                    double x = bucketXByIndex.getOrDefault(
                            bucketIndex,
                            VISUAL_MARGIN + VISUAL_NODE_RADIUS + bucketXByIndex.size() * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP)
                    );
                    double y = bucketY + (chainDepth + 1) * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP);
                    positions.put(simpleVisualId(node), new VisualPoint(x, y));
                });
        int fallbackIndex = 0;
        for (UiDebugVisualElementDto node : nodes) {
            String id = simpleVisualId(node);
            if (positions.containsKey(id)) {
                continue;
            }
            positions.put(id, new VisualPoint(
                    VISUAL_MARGIN + VISUAL_NODE_RADIUS + fallbackIndex * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP),
                    bucketY + (VISUAL_CELL_SIZE + VISUAL_GRID_GAP)
            ));
            fallbackIndex++;
        }
        return positions;
    }

    private int metadataInt(UiDebugVisualElementDto element, String key, int fallback) {
        try {
            return Integer.parseInt(element.metadata().getOrDefault(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Map<String, VisualPoint> treePositions(List<UiDebugVisualElementDto> nodes, List<UiDebugVisualElementDto> edges) {
        LinkedHashMap<String, UiDebugVisualElementDto> nodeById = new LinkedHashMap<>();
        nodes.forEach(node -> nodeById.put(simpleVisualId(node), node));
        LinkedHashMap<String, ArrayList<String>> childrenById = new LinkedHashMap<>();
        HashSet<String> childIds = new HashSet<>();
        nodeById.keySet().forEach(id -> childrenById.put(id, new ArrayList<>()));
        for (UiDebugVisualElementDto edge : orderedTreeEdges(edges)) {
            String from = edge.metadata().get("from");
            String to = edge.metadata().get("to");
            if (from != null && to != null && nodeById.containsKey(from) && nodeById.containsKey(to)) {
                childrenById.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
                childIds.add(to);
            }
        }
        ArrayList<String> roots = new ArrayList<>();
        nodeById.keySet().stream()
                .filter(id -> !childIds.contains(id))
                .forEach(roots::add);
        if (roots.isEmpty()) {
            roots.addAll(nodeById.keySet());
        }
        LinkedHashMap<String, VisualPoint> positions = new LinkedHashMap<>();
        TreeLayoutCursor cursor = new TreeLayoutCursor();
        for (String root : roots) {
            layoutTree(root, 0, childrenById, positions, new HashSet<>(), cursor);
            cursor.nextLeafX += VISUAL_CELL_SIZE + VISUAL_GRID_GAP;
        }
        return positions;
    }

    private List<UiDebugVisualElementDto> orderedTreeEdges(List<UiDebugVisualElementDto> edges) {
        return edges.stream()
                .sorted(Comparator
                        .comparing((UiDebugVisualElementDto edge) -> edge.metadata().getOrDefault("from", ""))
                        .thenComparingInt(edge -> treeEdgeOrder(edge.metadata().getOrDefault("key", edge.label()))))
                .toList();
    }

    private int treeEdgeOrder(String key) {
        return switch (key) {
            case "left" -> 0;
            case "right" -> 1;
            default -> 2;
        };
    }

    private double layoutTree(
            String nodeId,
            int depth,
            Map<String, ArrayList<String>> childrenById,
            Map<String, VisualPoint> positions,
            java.util.Set<String> visiting,
            TreeLayoutCursor cursor
    ) {
        if (!visiting.add(nodeId)) {
            double x = cursor.nextLeafX;
            cursor.nextLeafX += VISUAL_CELL_SIZE + VISUAL_GRID_GAP;
            positions.put(nodeId, new VisualPoint(x, treeY(depth)));
            return x;
        }
        List<String> children = childrenById.getOrDefault(nodeId, new ArrayList<>());
        if (children.isEmpty()) {
            double x = cursor.nextLeafX;
            cursor.nextLeafX += VISUAL_CELL_SIZE + VISUAL_GRID_GAP;
            positions.put(nodeId, new VisualPoint(x, treeY(depth)));
            visiting.remove(nodeId);
            return x;
        }
        ArrayList<Double> childXs = new ArrayList<>();
        for (String child : children) {
            childXs.add(layoutTree(child, depth + 1, childrenById, positions, visiting, cursor));
        }
        double x = (childXs.getFirst() + childXs.getLast()) / 2;
        positions.put(nodeId, new VisualPoint(x, treeY(depth)));
        visiting.remove(nodeId);
        return x;
    }

    private double treeY(int depth) {
        return VISUAL_MARGIN + VISUAL_NODE_RADIUS + depth * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP);
    }

    private List<Node> arrow(VisualPoint from, VisualPoint to, boolean nullTarget) {
        double angle = Math.atan2(to.y() - from.y(), to.x() - from.x());
        double startX = from.x() + Math.cos(angle) * VISUAL_NODE_RADIUS;
        double startY = from.y() + Math.sin(angle) * VISUAL_NODE_RADIUS;
        double targetRadius = nullTarget ? VISUAL_NULL_SIZE / 2 : VISUAL_NODE_RADIUS;
        double endX = to.x() - Math.cos(angle) * targetRadius;
        double endY = to.y() - Math.sin(angle) * targetRadius;
        Line line = new Line(startX, startY, endX, endY);
        line.getStyleClass().addAll("debug-graph-edge", "debug-pointer-arrow");
        double arrowSize = 8;
        Polygon head = new Polygon(
                endX, endY,
                endX - Math.cos(angle - Math.PI / 6) * arrowSize,
                endY - Math.sin(angle - Math.PI / 6) * arrowSize,
                endX - Math.cos(angle + Math.PI / 6) * arrowSize,
                endY - Math.sin(angle + Math.PI / 6) * arrowSize
        );
        head.getStyleClass().addAll("debug-graph-edge-head", "debug-pointer-arrow");
        return List.of(line, head);
    }

    private Text visualText(String label, double x, double y, double width) {
        Text text = new Text(label);
        text.getStyleClass().add("debug-visual-label");
        text.setX(x);
        text.setY(y + VISUAL_CELL_SIZE / 2);
        text.setWrappingWidth(width);
        text.setFill(ThemeRegistry.getColor("graph.label"));
        return text;
    }

    private String simpleVisualId(UiDebugVisualElementDto element) {
        String metadataId = element.metadata().get("id");
        if (metadataId != null && !metadataId.isBlank()) {
            return metadataId;
        }
        int index = element.id().lastIndexOf('-');
        return index < 0 ? element.id() : element.id().substring(index + 1);
    }

    private String shortLabel(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        String compact = label.replace('\n', ' ').strip();
        return compact.length() <= 10 ? compact : compact.substring(0, 9) + "...";
    }

    private record VisualPoint(double x, double y) {
    }

    private static final class TreeLayoutCursor {
        private double nextLeafX = VISUAL_MARGIN + VISUAL_NODE_RADIUS;
    }
}
