package craken.ui.component.visualization;

import craken.visualization.api.ViewLocation;
import craken.visualization.layout.LayoutRequest;
import craken.visualization.model.ViewNode;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;

public final class ViewNodeRenderer {
    public record RenderedNode(Pane view, LayoutRequest.Unit unit, Map<ViewLocation, Pane> members) {
        public RenderedNode { members = Map.copyOf(members); }
    }
    private static final double PADDING = 10, GAP = 8, MIN_WIDTH = 64;
    private final Font font;
    public ViewNodeRenderer() { this(Font.font("System", 14)); }
    public ViewNodeRenderer(Font font) { this.font = Objects.requireNonNull(font); }
    public RenderedNode render(ViewNode owner, Map<Long, ViewNode> pageNodes, VisualizationTheme theme, Set<ViewLocation> highlights) {
        requireFxThread(); Objects.requireNonNull(theme);
        var themes = new HashMap<ViewLocation, VisualizationTheme>();
        pageNodes.values().forEach(node -> themes.put(node.location(), theme));
        return render(owner, pageNodes, themes, highlights);
    }
    /** A per-member theme map allows, for example, red and black tree nodes in a single measured macro. */
    public RenderedNode render(ViewNode owner, Map<Long, ViewNode> pageNodes,
                               Map<ViewLocation, VisualizationTheme> themes, Set<ViewLocation> highlights) {
        requireFxThread(); Objects.requireNonNull(owner);
        var nodes = Map.copyOf(pageNodes); var fills = Map.copyOf(themes); var selected = Set.copyOf(highlights);
        if (nodes.get(owner.location().nodeId()) != owner)
            throw new IllegalArgumentException("Owner must belong to the supplied immutable page snapshot");
        var root = build(owner, nodes, fills, selected);
        var members = new ArrayList<Member>(); var ports = new ArrayList<Port>(); var obstacles = new ArrayList<Rect>();
        var views = new LinkedHashMap<ViewLocation, Pane>();
        collect(root, 0, 0, members, ports, obstacles, views);
        root.view.applyCss(); root.view.layout();
        var unit = new Unit(owner.location(), new Size(root.width, root.height), members, ports, obstacles);
        return new RenderedNode(root.view, unit, views);
    }
    private Card build(ViewNode owner, Map<Long, ViewNode> nodes, Map<ViewLocation, VisualizationTheme> themes,
                       Set<ViewLocation> highlights) {
        var order = new ArrayList<ViewNode>(); var pending = new ArrayDeque<ViewNode>(); pending.push(owner);
        var visited = new HashSet<ViewLocation>();
        while (!pending.isEmpty()) {
            var node = pending.pop();
            if (!visited.add(node.location())) throw new IllegalArgumentException("Cyclic or repeated composition member");
            if (node.content().color() == null && !themes.containsKey(node.location())) throw new IllegalArgumentException("Missing validated member theme");
            order.add(node);
            for (int index = node.children().size() - 1; index >= 0; index--) {
                var location = node.children().get(index); var child = nodes.get(location.nodeId());
                if (!location.page().equals(node.location().page()) || child == null || !child.location().equals(location))
                    throw new IllegalArgumentException("Unknown/foreign composition member");
                pending.push(child);
            }
        }
        var cards = new HashMap<ViewLocation, Card>();
        for (int index = order.size() - 1; index >= 0; index--) {
            var node = order.get(index); var children = node.children().stream().map(cards::get).toList();
            var theme = node.content().color() == null ? themes.get(node.location()) : VisualizationTheme.of(node.content().color());
            cards.put(node.location(), new Card(node, children, theme, highlights.contains(node.location())));
        }
        return cards.get(owner.location());
    }
    private final class Card {
        final ViewNode node;
        final Pane view = new Pane();
        final List<Card> children;
        final List<Text> texts = new ArrayList<>();
        final Map<String, Double> fieldCenters = new LinkedHashMap<>();
        final Rectangle body = new Rectangle(), header = new Rectangle(), clip = new Rectangle();
        final double headerHeight, childrenTop, ownWidth;
        double width, height;
        Card(ViewNode node, List<Card> children, VisualizationTheme theme, boolean selected) {
            this.node = node; this.children = List.copyOf(children);
            view.setManaged(false); view.setUserData(node.location());
            body.setFill(color(theme.bodyFill())); body.setStroke(color(VisualizationTheme.NODE_BORDER));
            body.setStrokeType(StrokeType.INSIDE);
            body.setStrokeWidth(selected ? VisualizationTheme.SELECTED_BORDER_WIDTH : VisualizationTheme.BORDER_WIDTH);
            body.setArcWidth(VisualizationTheme.NODE_RADIUS * 2); body.setArcHeight(VisualizationTheme.NODE_RADIUS * 2);
            header.setFill(color(theme.headerFill()));
            clip.setArcWidth(VisualizationTheme.NODE_RADIUS * 2); clip.setArcHeight(VisualizationTheme.NODE_RADIUS * 2);
            view.setClip(clip);
            view.getChildren().addAll(body, header);
            var title = text(node.content().label());
            double lineHeight = Math.ceil(text("Ag").getLayoutBounds().getHeight());
            headerHeight = Math.max(lineHeight, Math.ceil(title.getLayoutBounds().getHeight())) + 2 * GAP;
            place(title, PADDING, GAP); view.getChildren().add(title); texts.add(title);
            double current = headerHeight + GAP, widest = Math.ceil(title.getLayoutBounds().getWidth());
            for (var field : node.content().fields().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                var label = text(field.getKey() + ": " + field.getValue());
                double fieldHeight = Math.max(lineHeight, Math.ceil(label.getLayoutBounds().getHeight()));
                place(label, PADDING, current); texts.add(label); view.getChildren().add(label);
                fieldCenters.put(field.getKey(), current + fieldHeight / 2);
                widest = Math.max(widest, Math.ceil(label.getLayoutBounds().getWidth())); current += fieldHeight + GAP;
            }
            childrenTop = current; ownWidth = Math.max(MIN_WIDTH, widest + 2 * PADDING);
            alignMatrixColumns(); reflow();
        }
        private boolean matrix() {
            return node.content().kind() == ViewNode.Kind.ARRAY && !children.isEmpty()
                    && children.stream().allMatch(c -> c.node.content().kind() == ViewNode.Kind.ARRAY);
        }
        private void alignMatrixColumns() {
            if (!matrix()) return;
            int count = children.stream().mapToInt(c -> c.children.size()).max().orElse(0);
            var columns = new double[count];
            for (var row : children) for (int i = 0; i < row.children.size(); i++) columns[i] = Math.max(columns[i], row.children.get(i).width);
            for (var row : children) {
                for (int i = 0; i < row.children.size(); i++) row.children.get(i).expandWidth(columns[i]);
                row.reflow();
            }
            double rowWidth = children.stream().mapToDouble(c -> c.width).max().orElse(0);
            children.forEach(c -> c.expandWidth(rowWidth));
        }
        private void reflow() {
            boolean vertical = matrix();
            double x = PADDING, y = childrenTop, childWidth = 0, childHeight = 0;
            for (var child : children) {
                child.view.relocate(x, y);
                childWidth = Math.max(childWidth, x + child.width - PADDING);
                childHeight = Math.max(childHeight, y + child.height - childrenTop);
                if (vertical) y += child.height + GAP; else x += child.width + GAP;
            }
            width = Math.max(ownWidth, childWidth + 2 * PADDING);
            height = childrenTop + Math.max(children.isEmpty() ? GAP : 0, childHeight) + PADDING;
            size();
        }
        private void expandWidth(double minimum) { width = Math.max(width, minimum); size(); }
        private void size() {
            view.setMinSize(width, height); view.setPrefSize(width, height); view.setMaxSize(width, height); view.resize(width, height);
            body.setWidth(width); body.setHeight(height); header.setWidth(width); header.setHeight(headerHeight);
            clip.setWidth(width); clip.setHeight(height);
        }
    }
    private Text text(String value) {
        var text = new Text(value); text.setFont(font); text.setFill(color(VisualizationTheme.TEXT)); text.setManaged(false);
        return text;
    }
    private static void place(Text text, double x, double y) {
        var bounds = text.getLayoutBounds(); text.setLayoutX(x - bounds.getMinX()); text.setLayoutY(y - bounds.getMinY());
    }
    private record Placed(Card card, double x, double y) {}
    private static void collect(Card root, double rootX, double rootY, List<Member> members, List<Port> ports,
                                List<Rect> obstacles, Map<ViewLocation, Pane> views) {
        var pending = new ArrayDeque<Placed>(); pending.push(new Placed(root, rootX, rootY));
        while (!pending.isEmpty()) {
        var placed = pending.pop(); var card = placed.card(); double x = placed.x(), y = placed.y();
        var location = card.node.location(); var rect = new Rect(x, y, card.width, card.height);
        members.add(new Member(location, rect)); views.put(location, card.view);
        ports.add(new Port(PortRef.node(location), rect.center(), Side.AUTO));
        ports.add(new Port(new PortRef(location, "north"), new Point(rect.center().x(), y), Side.NORTH));
        ports.add(new Port(new PortRef(location, "east"), new Point(rect.right(), rect.center().y()), Side.EAST));
        ports.add(new Port(new PortRef(location, "south"), new Point(rect.center().x(), rect.bottom()), Side.SOUTH));
        ports.add(new Port(new PortRef(location, "west"), new Point(x, rect.center().y()), Side.WEST));
        card.fieldCenters.forEach((key, center) -> ports.add(new Port(new PortRef(location, "field:" + key),
                new Point(rect.right(), y + center), Side.EAST)));
        for (var text : card.texts) {
            Bounds bounds = text.getBoundsInParent();
            if (bounds.getWidth() > 0 && bounds.getHeight() > 0)
                obstacles.add(new Rect(x + bounds.getMinX(), y + bounds.getMinY(), bounds.getWidth(), bounds.getHeight()));
        }
        for (int index = card.children.size() - 1; index >= 0; index--) {
            var child = card.children.get(index);
            pending.push(new Placed(child, x + child.view.getLayoutX(), y + child.view.getLayoutY()));
        }
        if (card != root) { card.view.relocate(x, y); root.view.getChildren().add(card.view); }
        }
    }
    static Color color(VisualizationTheme.Srgb value) { return Color.rgb(value.red(), value.green(), value.blue(), value.opacity()); }
    static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Visualization rendering and measurement require the JavaFX application thread");
    }
}
