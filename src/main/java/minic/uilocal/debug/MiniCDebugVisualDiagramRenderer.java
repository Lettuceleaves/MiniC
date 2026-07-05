package minic.uilocal;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import minic.color.ThemeRegistry;
import minic.uiapi.UiDebugVisualElementDto;
import minic.uiapi.UiDebugVisualStructureDto;

import java.util.ArrayList;
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
    private static final double VISUAL_GRID_UNIT = 22;

    private final BiConsumer<Node, UiDebugVisualElementDto> tooltipInstaller;

    MiniCDebugVisualDiagramRenderer(BiConsumer<Node, UiDebugVisualElementDto> tooltipInstaller) {
        this.tooltipInstaller = tooltipInstaller;
    }

    Node visualDiagram(UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> arrayCells = visual.elements().stream()
                .filter(element -> element.kind().equals("ARRAY_CELL"))
                .toList();
        List<UiDebugVisualElementDto> graphNodes = visual.elements().stream()
                .filter(element -> element.kind().equals("GRAPH_NODE"))
                .toList();
        List<UiDebugVisualElementDto> graphEdges = visual.elements().stream()
                .filter(element -> element.kind().equals("GRAPH_EDGE"))
                .toList();
        if (visual.layoutHint().equals("grid")) {
            return gridVisualDiagram(arrayCells, graphNodes, graphEdges);
        }
        if (!arrayCells.isEmpty()) {
            return arrayDiagram(visual.layoutHint(), arrayCells);
        }
        if (!graphNodes.isEmpty()) {
            return graphDiagram(visual.kind(), visual.layoutHint(), graphNodes, graphEdges);
        }
        return null;
    }

    private Node arrayDiagram(String layoutHint, List<UiDebugVisualElementDto> cells) {
        Pane pane = new Pane();
        pane.getStyleClass().add("debug-visual-diagram");
        boolean matrixLayout = layoutHint.equals("grid") || layoutHint.equals("matrix") || cells.stream()
                .anyMatch(cell -> metadataInt(cell, "row", 0) > 0);
        int rows = matrixLayout
                ? cells.stream().mapToInt(cell -> metadataInt(cell, "row", 0)).max().orElse(0) + 1
                : 1;
        int columns = matrixLayout
                ? cells.stream().mapToInt(cell -> metadataInt(cell, "column", 0)).max().orElse(0) + 1
                : cells.size();
        double width = VISUAL_MARGIN * 2 + VISUAL_CELL_SIZE * Math.max(columns, 1);
        double height = VISUAL_MARGIN * 2 + VISUAL_CELL_SIZE * Math.max(rows, 1);
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        for (int index = 0; index < cells.size(); index++) {
            UiDebugVisualElementDto cell = cells.get(index);
            int row = matrixLayout ? metadataInt(cell, "row", 0) : 0;
            int column = matrixLayout ? metadataInt(cell, "column", index) : index;
            double x = VISUAL_MARGIN + column * VISUAL_CELL_SIZE;
            double y = VISUAL_MARGIN + row * VISUAL_CELL_SIZE;
            Rectangle rect = new Rectangle(x, y, VISUAL_CELL_SIZE, VISUAL_CELL_SIZE);
            rect.getStyleClass().add("debug-array-cell");
            Text text = visualText(shortArrayLabel(cell.label()), x, y + 4, VISUAL_CELL_SIZE);
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
        if (layoutHint.equals("grid")) {
            return gridGraphDiagram(nodes, edges);
        }
        Pane pane = new Pane();
        pane.getStyleClass().add("debug-visual-diagram");
        List<UiDebugVisualElementDto> visibleNodes = nodes;
        Map<String, UiDebugVisualElementDto> nodesById = visibleNodes.stream()
                .collect(Collectors.toMap(this::simpleVisualId, node -> node, (left, right) -> left, LinkedHashMap::new));
        Map<String, VisualPoint> positions = graphPositions(visibleNodes);
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
            applyVisualShapeStyle(circle, node);
            circle.setAccessibleText(simpleVisualId(node));
            Text text = visualText(shortLabel(node.label()), point.x() - VISUAL_NODE_RADIUS, point.y() + 4, VISUAL_CELL_SIZE);
            applyVisualTextStyle(text, node);
            tooltipInstaller.accept(circle, node);
            tooltipInstaller.accept(text, node);
            pane.getChildren().addAll(circle, text);
        }
        return pane;
    }

    private Node gridGraphDiagram(List<UiDebugVisualElementDto> nodes, List<UiDebugVisualElementDto> edges) {
        return gridVisualDiagram(List.of(), nodes, edges);
    }

    private Node gridVisualDiagram(
            List<UiDebugVisualElementDto> cells,
            List<UiDebugVisualElementDto> nodes,
            List<UiDebugVisualElementDto> edges
    ) {
        Pane pane = new Pane();
        pane.getStyleClass().add("debug-visual-diagram");
        LinkedHashMap<UiDebugVisualElementDto, GridRect> rectsByNode = new LinkedHashMap<>();
        ArrayList<GridRect> rects = new ArrayList<>();
        ArrayList<UiDebugVisualElementDto> drawableNodes = new ArrayList<>();
        drawableNodes.addAll(cells);
        drawableNodes.addAll(nodes);
        for (UiDebugVisualElementDto node : drawableNodes) {
            GridRect rect = gridRect(node);
            if (rect == null) {
                continue;
            }
            rectsByNode.put(node, rect);
            rects.add(rect);
        }
        LinkedHashMap<UiDebugVisualElementDto, VisualPoint> startsByEdge = new LinkedHashMap<>();
        LinkedHashMap<UiDebugVisualElementDto, VisualPoint> endsByEdge = new LinkedHashMap<>();
        ArrayList<VisualPoint> points = new ArrayList<>();
        for (UiDebugVisualElementDto edge : edges) {
            VisualPoint start = gridPoint(edge, "gridStartX", "gridStartY");
            VisualPoint end = gridPoint(edge, "gridEndX", "gridEndY");
            if (start == null || end == null) {
                continue;
            }
            startsByEdge.put(edge, start);
            endsByEdge.put(edge, end);
            points.add(start);
            points.add(end);
        }
        GridBounds bounds = gridBounds(rects, points);
        double width = Math.max(220, VISUAL_MARGIN * 2 + (bounds.maxX() - bounds.minX()) * VISUAL_GRID_UNIT);
        double height = Math.max(150, VISUAL_MARGIN * 2 + (bounds.maxY() - bounds.minY()) * VISUAL_GRID_UNIT);
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        for (Map.Entry<UiDebugVisualElementDto, VisualPoint> entry : startsByEdge.entrySet()) {
            UiDebugVisualElementDto edge = entry.getKey();
            VisualPoint start = entry.getValue();
            VisualPoint end = endsByEdge.get(edge);
            if (end == null) {
                continue;
            }
            VisualPoint svgStart = new VisualPoint(
                    VISUAL_MARGIN + (start.x() - bounds.minX()) * VISUAL_GRID_UNIT,
                    VISUAL_MARGIN + (start.y() - bounds.minY()) * VISUAL_GRID_UNIT
            );
            VisualPoint svgEnd = new VisualPoint(
                    VISUAL_MARGIN + (end.x() - bounds.minX()) * VISUAL_GRID_UNIT,
                    VISUAL_MARGIN + (end.y() - bounds.minY()) * VISUAL_GRID_UNIT
            );
            List<Node> arrow = gridArrow(svgStart, svgEnd, edge);
            arrow.forEach(node -> tooltipInstaller.accept(node, edge));
            pane.getChildren().addAll(arrow);
        }
        for (Map.Entry<UiDebugVisualElementDto, GridRect> entry : rectsByNode.entrySet()) {
            UiDebugVisualElementDto node = entry.getKey();
            GridRect rect = entry.getValue();
            double x = VISUAL_MARGIN + (rect.x() - bounds.minX()) * VISUAL_GRID_UNIT;
            double y = VISUAL_MARGIN + (rect.y() - bounds.minY()) * VISUAL_GRID_UNIT;
            double rectWidth = rect.width() * VISUAL_GRID_UNIT;
            double rectHeight = rect.height() * VISUAL_GRID_UNIT;
            Rectangle rectangle = new Rectangle(x, y, rectWidth, rectHeight);
            boolean square = node.kind().equals("ARRAY_CELL")
                    || node.metadata().getOrDefault("visual-shape", "").equals("SQUARE");
            if (square) {
                rectangle.getStyleClass().add("debug-grid-square");
            } else {
                rectangle.getStyleClass().add("debug-grid-node");
            }
            applyVisualShapeStyle(rectangle, node);
            rectangle.setAccessibleText(simpleVisualId(node));
            tooltipInstaller.accept(rectangle, node);
            pane.getChildren().add(rectangle);
            List<Node> content = gridNodeContent(node, square, x, y, rectWidth, rectHeight);
            content.forEach(child -> tooltipInstaller.accept(child, node));
            pane.getChildren().addAll(content);
        }
        return pane;
    }

    private List<Node> gridNodeContent(
            UiDebugVisualElementDto node,
            boolean square,
            double x,
            double y,
            double width,
            double height
    ) {
        if (!square && node.metadata().getOrDefault("visual-content", "").equals("STRUCT_TABLE")) {
            return gridStructTable(node, x, y, width, height);
        }
        Text text = visualText(
                square ? shortArrayLabel(node.label()) : shortLabel(node.label()),
                x,
                y + height / 2 - VISUAL_CELL_SIZE / 2 + 4,
                width
        );
        applyVisualTextStyle(text, node);
        return List.of(text);
    }

    private List<Node> gridStructTable(UiDebugVisualElementDto node, double x, double y, double width, double height) {
        int rowCount = Math.max(1, metadataInt(node, "visual-row-count", 1));
        double rowHeight = height / rowCount;
        ArrayList<Node> content = new ArrayList<>();
        for (int row = 1; row < rowCount; row++) {
            double lineY = y + row * rowHeight;
            Line line = new Line(x, lineY, x + width, lineY);
            line.getStyleClass().add("debug-grid-table-line");
            content.add(line);
        }
        for (int row = 0; row < rowCount; row++) {
            String value = node.metadata().getOrDefault("visual-row." + row, "");
            Text text = visualText(
                    fitText(value, metadataInt(node, "visual-row-capacity", 12)),
                    x,
                    y + row * rowHeight + rowHeight / 2 - VISUAL_CELL_SIZE / 2 + 4,
                    width
            );
            applyVisualTextStyle(text, node);
            content.add(text);
        }
        return content;
    }

    private void applyVisualShapeStyle(Node shape, UiDebugVisualElementDto element) {
        addVisualStyleClasses(shape, element);
        ArrayList<String> declarations = new ArrayList<>();
        String fill = element.metadata().getOrDefault("visual-fill", "");
        if (!fill.isBlank()) {
            declarations.add("-fx-fill: " + fill);
        }
        String stroke = element.metadata().getOrDefault("visual-stroke", "");
        if (!stroke.isBlank()) {
            declarations.add("-fx-stroke: " + stroke);
        }
        if (!declarations.isEmpty()) {
            shape.setStyle(String.join("; ", declarations) + ";");
        }
    }

    private void addVisualStyleClasses(Node node, UiDebugVisualElementDto element) {
        String styleClass = element.metadata().getOrDefault("visual-style-class", "");
        if (styleClass.isBlank()) {
            return;
        }
        for (String item : styleClass.split("[,\\s]+")) {
            if (!item.isBlank()) {
                node.getStyleClass().add(item);
            }
        }
    }

    private void applyVisualTextStyle(Text text, UiDebugVisualElementDto element) {
        String fill = element.metadata().getOrDefault("visual-text-fill", "");
        if (fill.isBlank()) {
            return;
        }
        try {
            text.setFill(Color.web(fill));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private GridRect gridRect(UiDebugVisualElementDto node) {
        Double x = metadataNumber(node, "gridX");
        Double y = metadataNumber(node, "gridY");
        Double width = metadataNumber(node, "gridWidth");
        Double height = metadataNumber(node, "gridHeight");
        if (x == null || y == null || width == null || height == null) {
            return null;
        }
        return new GridRect(x, y, width, height);
    }

    private VisualPoint gridPoint(UiDebugVisualElementDto edge, String xKey, String yKey) {
        Double x = metadataNumber(edge, xKey);
        Double y = metadataNumber(edge, yKey);
        return x == null || y == null ? null : new VisualPoint(x, y);
    }

    private GridBounds gridBounds(List<GridRect> rects, List<VisualPoint> points) {
        double minX = 0;
        double minY = 0;
        double maxX = 1;
        double maxY = 1;
        for (GridRect rect : rects) {
            minX = Math.min(minX, rect.x());
            minY = Math.min(minY, rect.y());
            maxX = Math.max(maxX, rect.x() + rect.width());
            maxY = Math.max(maxY, rect.y() + rect.height());
        }
        for (VisualPoint point : points) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        return new GridBounds(minX, minY, maxX, maxY);
    }

    private Double metadataNumber(UiDebugVisualElementDto element, String key) {
        String value = element.metadata().get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isNullNode(Map<String, UiDebugVisualElementDto> nodesById, String id) {
        UiDebugVisualElementDto node = nodesById.get(id);
        return node != null && isNullNode(node);
    }

    private boolean isNullNode(UiDebugVisualElementDto node) {
        return Boolean.parseBoolean(node.metadata().getOrDefault("visual-null", "false"));
    }

    private Map<String, VisualPoint> graphPositions(List<UiDebugVisualElementDto> nodes) {
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

    private int metadataInt(UiDebugVisualElementDto element, String key, int fallback) {
        try {
            return Integer.parseInt(element.metadata().getOrDefault(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<Node> gridArrow(VisualPoint from, VisualPoint to, UiDebugVisualElementDto edge) {
        double angle = Math.atan2(to.y() - from.y(), to.x() - from.x());
        Line line = new Line(from.x(), from.y(), to.x(), to.y());
        line.getStyleClass().addAll("debug-graph-edge", "debug-pointer-arrow");
        line.setAccessibleText(edge.label());
        double arrowSize = 8;
        Polygon head = new Polygon(
                to.x(), to.y(),
                to.x() - Math.cos(angle - Math.PI / 6) * arrowSize,
                to.y() - Math.sin(angle - Math.PI / 6) * arrowSize,
                to.x() - Math.cos(angle + Math.PI / 6) * arrowSize,
                to.y() - Math.sin(angle + Math.PI / 6) * arrowSize
        );
        head.getStyleClass().addAll("debug-graph-edge-head", "debug-pointer-arrow");
        head.setAccessibleText(edge.label());
        return List.of(line, head);
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

    private String shortArrayLabel(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        String compact = label.replace('\n', ' ').strip();
        int capacity = 4;
        if (compact.length() <= capacity) {
            return compact;
        }
        return compact.substring(0, capacity - 3) + "...";
    }

    private String fitText(String value, int capacity) {
        if (capacity < 3) {
            capacity = 3;
        }
        String compact = value == null ? "" : value.replace('\n', ' ').strip();
        if (compact.length() <= capacity) {
            return compact;
        }
        if (capacity == 3) {
            return "...";
        }
        return compact.substring(0, capacity - 3) + "...";
    }

    private record VisualPoint(double x, double y) {
    }

    private record GridRect(double x, double y, double width, double height) {
    }

    private record GridBounds(double minX, double minY, double maxX, double maxY) {
    }
}
