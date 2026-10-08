package craken.ui.component.visualization;

import craken.visualization.api.*;
import craken.visualization.layout.*;
import craken.visualization.model.*;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.Node;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Consumer;
import static craken.visualization.layout.LayoutRequest.*;

public final class PartView extends VBox implements AutoCloseable {
    private record Shape(Kind kind, List<Unit> units, List<Link> links, Hints hints, long epoch, double width) {}
    /** The design presentation draws chain arrows with a heavier light stroke and a larger head. */
    private static final craken.visualization.style.EdgeStyle DESIGN_EDGE =
            new craken.visualization.style.EdgeStyle("#C9D1D9", null, "");
    private static final double DESIGN_EDGE_WIDTH = 2, DESIGN_ARROW_SCALE = 1.35;
    private final long id;
    private final LayoutCoordinator coordinator;
    private final LayoutCoordinator.Key key;
    private final Runnable changed;
    private final Label status = new Label();
    private final Pane canvas = new Pane();
    private final Map<ViewLocation, Pane> displayed = new LinkedHashMap<>();
    private LayoutRequest request;
    private LayoutResult geometry;
    private Shape shape;
    private long geometryVersion;
    private boolean pending, closed;
    private String error = "";
    PartView(long occurrenceId, long id, LayoutCoordinator coordinator, Runnable changed) {
        this.id = id; this.coordinator = coordinator; this.changed = changed; key = new LayoutCoordinator.Key(occurrenceId, id);
        status.setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT)); canvas.setMinSize(0, 0);
        getChildren().addAll(status, canvas); setSpacing(4);
    }
    void update(PageModel page, Part part, Set<ViewLocation> highlights, double width, long epoch,
                Consumer<OperationPath> selection) {
        ViewNodeRenderer.requireFxThread(); if (closed) return;
        var includedChildren = new HashSet<Long>(); page.composition().values().forEach(c -> includedChildren.add(c.child().nodeId()));
        var memberNodes = part.members().stream().sorted().map(page.nodes()::get)
                .filter(node -> node != null && !includedChildren.contains(node.location().nodeId())).toList();
        var links = page.topology().values().stream().filter(e -> part.members().contains(e.a().nodeId()) && part.members().contains(e.b().nodeId()))
                .sorted(Comparator.comparingLong(e -> e.id())).map(e -> new Link(e.id(), new PortRef(e.a(), e.aPort()), new PortRef(e.b(), e.bPort()), Direction.valueOf(e.direction().name()))).toList();
        Kind kind = switch (page.type().layout()) { case POINT -> Kind.POINT; case ARRAY -> Kind.ARRAY; case LINEAR -> Kind.LINEAR; case TREE -> Kind.TREE; case STRESS -> Kind.GRAPH; case BUCKETS -> Kind.BUCKETS; };
        highlights = kind == Kind.BUCKETS ? bucketHighlights(page, links, highlights, page.layoutHints().treeRoot()) : highlights;
        // Debug workbench pages use the sectioned design cards; Pipeline pages keep their own look.
        boolean sectioned = page.type().layout() == PageType.Layout.BUCKETS || page.type().key().startsWith("debug-");
        var configuredRoot = page.layoutHints().treeRoot();
        var anchor = configuredRoot != null && part.members().contains(configuredRoot.nodeId()) ? configuredRoot
                : memberNodes.isEmpty() ? null
                : kind == Kind.BUCKETS ? memberNodes.stream().filter(node -> node.content().kind() == ViewNode.Kind.ARRAY)
                        .map(ViewNode::location).findFirst().orElse(memberNodes.getFirst().location())
                : kind == Kind.TREE ? treeAnchor(memberNodes, links)
                : memberNodes.getFirst().location();
        var renderer = new ViewNodeRenderer(); var prepared = new LinkedHashMap<ViewLocation, ViewNodeRenderer.RenderedNode>();
        var units = new ArrayList<Unit>();
        var theme = VisualizationTheme.of(VisualizationTheme.Preset.BLUE);
        var sectionedStyle = ViewNodeRenderer.ArrayStyle.sectionedCards();
        for (var node : memberNodes) {
            if (kind == Kind.BUCKETS && node.location().equals(anchor)) continue;
            // 调试页里的最内层数组按连续列表绘制：共享分隔线、左侧下标，和文档的数组原语一致。
            boolean slotArray = sectioned && node.content().kind() == ViewNode.Kind.ARRAY && !node.children().isEmpty()
                    && node.children().stream().allMatch(child -> page.nodes().get(child.nodeId()).content().kind() == ViewNode.Kind.POINT);
            var rendered = slotArray ? renderer.render(node, page.nodes(), theme, highlights, ViewNodeRenderer.ArrayStyle.slots(48))
                    : sectioned
                    ? renderer.render(node, page.nodes(), theme, highlights, sectionedStyle)
                    : renderer.render(node, page.nodes(), theme, highlights);
            prepared.put(node.location(), rendered); units.add(rendered.unit());
        }
        if (kind == Kind.BUCKETS && anchor != null) {
            // Bucket cells grow to the tallest chain card so every chain row lines up with its cell.
            double tallest = units.stream().mapToDouble(unit -> unit.size().height()).max().orElse(0);
            var node = page.nodes().get(anchor.nodeId());
            var rendered = renderer.render(node, page.nodes(), theme, highlights, ViewNodeRenderer.ArrayStyle.slots(tallest + 24));
            prepared.put(node.location(), rendered); units.add(rendered.unit());
        }
        // A measured isolated macro is an exact point, even when its page otherwise uses Stress.
        if (units.size() == 1 && links.isEmpty()) kind = Kind.POINT;
        // A TREE page may contain shared AST identities. Dispatch its measured macro graph before layout.
        if (kind == Kind.TREE && !isMacroTree(units, links)) kind = Kind.GRAPH;
        var configuredOrder = page.layoutHints().order().stream().filter(n -> part.members().contains(n.nodeId())).toList();
        List<ViewLocation> order = configuredOrder.isEmpty() ? units.stream().map(Unit::node).toList() : configuredOrder;
        if (kind == Kind.LINEAR) {
            var chain = LinearLayout.topologyOrder(units, links);
            if (chain.isPresent()) { if (configuredOrder.isEmpty()) order = chain.get(); } else kind = Kind.GRAPH;
        }
        double widest = units.stream().mapToDouble(u -> u.size().width()).max().orElse(1);
        int columns = Math.max(1, (int)Math.floor((Math.max(0, width - 16) + 24) / (widest + 24)));
        var treeRoot = kind == Kind.TREE || kind == Kind.BUCKETS ? anchor : null;
        var hints = new Hints(24, 40, 8, columns, Orientation.HORIZONTAL, treeRoot, order);
        var nextShape = new Shape(kind, List.copyOf(units), links, hints, epoch, width);
        if (!nextShape.equals(shape)) { shape = nextShape; geometryVersion++; }
        request = new LayoutRequest(new Stamp(page.ref(), id, geometryVersion, 0, epoch, width), kind, units, links, hints, Map.of());
        var current = request; pending = true; error = ""; text("正在布局…");
        if (geometry != null) paint(geometry, prepared, links, page, selection, true);
        coordinator.submit(key, current, result -> {
            if (closed || request != current) return;
            geometry = result; pending = false; error = ""; paint(result, prepared, links, page, selection, false); text(""); changed.run();
        }, failure -> {
            if (closed || request != current) return;
            pending = false; error = failure.code() + ": " + failure.getMessage();
            text("布局失败：" + error); changed.run();
        });
        changed.run();
    }
    /** A highlighted chain entry marks its bucket cell so the whole bucket row reads as one unit. */
    private static Set<ViewLocation> bucketHighlights(PageModel page, List<Link> links,
                                                      Set<ViewLocation> highlights, ViewLocation anchor) {
        if (highlights.isEmpty() || anchor == null || page.nodes().get(anchor.nodeId()) == null) return highlights;
        var next = new LinkedHashMap<ViewLocation, ViewLocation>();
        for (var link : links) next.putIfAbsent(link.tail().node(), link.head().node());
        var result = new LinkedHashSet<>(highlights);
        for (var slot : page.nodes().get(anchor.nodeId()).children()) {
            var visited = new HashSet<ViewLocation>(); ViewLocation current = slot;
            while (current != null && visited.add(current)) {
                if (highlights.contains(current)) { result.add(slot); break; }
                current = next.get(current);
            }
        }
        return Set.copyOf(result);
    }
    /** A tree page without an explicit display root hangs from the unit no drawn edge points to. */
    private static ViewLocation treeAnchor(List<ViewNode> units, List<Link> links) {
        var members = new HashSet<ViewLocation>();
        for (var unit : units) members.add(unit.location());
        var incoming = new HashMap<ViewLocation, Integer>();
        for (var link : links) if (members.contains(link.head().node()))
            incoming.merge(link.head().node(), 1, Integer::sum);
        return units.stream().map(ViewNode::location)
                .filter(location -> incoming.getOrDefault(location, 0) == 0)
                .findFirst().orElse(units.getFirst().location());
    }
    private static boolean isMacroTree(List<Unit> units, List<Link> links) {
        if (units.isEmpty()) return true;
        var owners = new HashMap<ViewLocation, ViewLocation>();
        var adjacent = new LinkedHashMap<ViewLocation, Set<ViewLocation>>();
        for (var unit : units) {
            adjacent.put(unit.node(), new HashSet<>());
            for (var member : unit.members()) owners.put(member.node(), unit.node());
        }
        for (var link : links) {
            if (link.tail().node().equals(link.head().node())) return false;
            var a = owners.get(link.tail().node()); var b = owners.get(link.head().node());
            if (a == null || b == null) return false;
            if (!a.equals(b)) { adjacent.get(a).add(b); adjacent.get(b).add(a); }
        }
        if (adjacent.values().stream().mapToLong(Set::size).sum() / 2 != units.size() - 1) return false;
        var visited = new HashSet<ViewLocation>(); var pending = new ArrayDeque<ViewLocation>();
        pending.add(units.getFirst().node());
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (visited.add(current)) for (var next : adjacent.get(current)) if (!visited.contains(next)) pending.add(next);
        }
        return visited.size() == units.size();
    }
    private void paint(LayoutResult result, Map<ViewLocation, ViewNodeRenderer.RenderedNode> prepared,
                       List<Link> links, PageModel page, Consumer<OperationPath> selection, boolean onlyCompatible) {
        displayed.values().forEach(v -> v.setOnMouseClicked(null)); displayed.clear(); canvas.getChildren().clear();
        var extent = result.contentBounds(); var visible = new HashSet<ViewLocation>();
        for (var rendered : prepared.values()) {
            boolean compatible = rendered.unit().members().stream().allMatch(member -> {
                var old = result.nodeBounds().get(member.node()); return old != null && (!onlyCompatible
                        || Math.abs(old.width() - member.bounds().width()) < 1e-7 && Math.abs(old.height() - member.bounds().height()) < 1e-7);
            });
            if (compatible) visible.addAll(rendered.members().keySet());
        }
        var edgeRenderer = new EdgeRenderer();
        boolean designEdges = request.kind() == Kind.BUCKETS;
        var graphics = new ArrayList<Node>();
        for (var edge : links) if (visible.contains(edge.tail().node()) && visible.contains(edge.head().node())) {
            var path = result.edgePaths().get(edge.id());
            if (path != null) {
                var style = page.topology().get(edge.id()).style();
                if (designEdges && style.equals(craken.visualization.style.EdgeStyle.DEFAULT)) style = DESIGN_EDGE;
                var graphic = designEdges
                        ? edgeRenderer.render(path, edge.direction(), style, DESIGN_EDGE_WIDTH, DESIGN_ARROW_SCALE)
                        : edgeRenderer.render(path, edge.direction(), style);
                graphic.setTranslateX(-extent.x()); graphic.setTranslateY(-extent.y());
                // Composition frames have opaque backgrounds. Routed edges must remain visible in their internal channels.
                graphic.setViewOrder(-1); graphics.add(graphic);
            }
        }
        var views = new ArrayList<Pane>();
        for (var entry : prepared.entrySet()) if (visible.contains(entry.getKey())) {
            var rendered = entry.getValue(); var outer = result.nodeBounds().get(entry.getKey());
            rendered.view().relocate(outer.x() - extent.x(), outer.y() - extent.y());
            views.add(rendered.view());
            rendered.members().forEach((location, view) -> {
                displayed.put(location, view);
                view.setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        selection.accept(new OperationPath(page.nodes().get(location.nodeId()).parents().selected(), location)); event.consume();
                    }
                });
            });
        }
        canvas.getChildren().addAll(graphics);
        canvas.getChildren().addAll(views);
        canvas.setPrefSize(extent.width(), extent.height()); canvas.resize(extent.width(), extent.height());
        if (onlyCompatible && visible.size() < request.units().stream().mapToInt(u -> u.members().size()).sum()) text("新节点等待布局…");
    }
    private void text(String value) { status.setText(value); status.setVisible(!value.isEmpty()); status.setManaged(!value.isEmpty()); }
    public long partId() { return id; }
    public LayoutResult geometry() { return geometry; }
    public boolean isPending() { return pending; }
    public String errorText() { return error; }
    Map<ViewLocation, Pane> nodeViews() { return Map.copyOf(displayed); }
    @Override public void close() {
        ViewNodeRenderer.requireFxThread(); if (closed) return; closed = true; pending = false; coordinator.cancel(key);
        displayed.values().forEach(v -> v.setOnMouseClicked(null)); displayed.clear(); canvas.getChildren().clear(); getChildren().clear();
    }
}
