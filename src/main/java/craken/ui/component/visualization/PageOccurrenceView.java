package craken.ui.component.visualization;

import craken.visualization.api.*;
import craken.visualization.layout.LayoutCoordinator;
import craken.visualization.model.PageModel;
import craken.visualization.navigation.NavigationFrame.PageOccurrence;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Label;
import javafx.scene.control.ChoiceBox;
import javafx.scene.Group;
import javafx.scene.transform.Scale;
import javafx.geometry.Insets;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Consumer;

public final class PageOccurrenceView extends BorderPane implements AutoCloseable {
    private final long id;
    private final LayoutCoordinator coordinator;
    private final Runnable changed;
    private final ScrollPane viewport = new ScrollPane();
    private final VBox stack = new VBox(16);
    private final Group world = new Group(stack);
    private final Label ready = new Label();
    private final Map<Long, PartView> parts = new LinkedHashMap<>();
    private PageOccurrence occurrence;
    private int readyCount;
    private double zoom = 1;
    private boolean closed;
    PageOccurrenceView(long id, LayoutCoordinator coordinator, Runnable changed) {
        this.id = id; this.coordinator = coordinator; this.changed = changed;
        setMinSize(0, 0); setPadding(new Insets(1));
        setBackground(new Background(new BackgroundFill(ViewNodeRenderer.color(VisualizationTheme.GROUP_BACKGROUND), CornerRadii.EMPTY, Insets.EMPTY)));
        setBorder(new Border(new BorderStroke(ViewNodeRenderer.color(VisualizationTheme.GROUP_BORDER), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));
        stack.setPadding(new Insets(8)); viewport.setContent(world); viewport.setFitToWidth(false); viewport.setPannable(true); viewport.setMinSize(0, 0);
        viewport.setStyle("-fx-background-color: #0D1117; -fx-background: #0D1117;");
        ready.setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT)); ready.setPadding(new Insets(6)); setCenter(viewport);
    }
    void update(PageModel page, PageOccurrence occurrence, double width, long epoch, Consumer<OperationPath> selection) {
        if (closed) return; this.occurrence = occurrence; setMinWidth(width); setPrefWidth(width); setMaxWidth(width);
        var title = new Label("页 #" + page.ref().pageId() + " · " + page.type().key()); title.setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT));
        var header = new HBox(8, title); header.setPadding(new Insets(8));
        if (occurrence.node() != null) {
            var node = page.nodes().get(occurrence.node().nodeId());
            if (node.parents().parents().size() > 1) {
                var parents = new ChoiceBox<ViewLocation>(); parents.getItems().setAll(node.parents().parents()); parents.setValue(node.parents().selected());
                parents.setOnAction(event -> { if (!Objects.equals(parents.getValue(), node.parents().selected())) selection.accept(new OperationPath(parents.getValue(), node.location())); });
                header.getChildren().add(parents);
            }
        }
        setTop(header); readyCount = page.ready().size(); ready.setText("待连接：" + readyCount);
        setBottom(page.type().readyEnabled() ? ready : null);
        parts.keySet().stream().filter(partId -> !page.parts().containsKey(partId)).toList().forEach(partId -> parts.remove(partId).close());
        var ordered = new LinkedHashMap<Long, PartView>();
        for (var part : page.parts().values().stream().sorted(Comparator.comparingLong(p -> p.id())).toList()) {
            var view = parts.computeIfAbsent(part.id(), partId -> new PartView(id, partId, coordinator, this::partChanged)); ordered.put(part.id(), view);
            view.update(page, part, occurrence.highlights(), Math.max(0, width - 2), epoch, selection);
        }
        parts.clear(); parts.putAll(ordered); stack.getChildren().setAll(parts.values());
        if (parts.isEmpty()) { var empty = new Label("空页"); empty.setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT)); stack.getChildren().add(empty); }
        stack.applyCss(); stack.autosize(); stack.layout(); setZoom(zoom); changed.run();
    }
    public PageOccurrence occurrence() { return occurrence; }
    public List<PartView> parts() { return List.copyOf(parts.values()); }
    public Map<ViewLocation, Pane> nodeViews() { var nodes = new LinkedHashMap<ViewLocation, Pane>(); parts.values().forEach(part -> nodes.putAll(part.nodeViews())); return Map.copyOf(nodes); }
    public ScrollPane scrollPane() { return viewport; }
    public int readyCount() { return readyCount; }
    public double getZoom() { return zoom; }
    private void partChanged() {
        if (closed) return; stack.applyCss(); stack.autosize(); stack.layout(); changed.run();
    }
    void setZoom(double zoom) {
        this.zoom = zoom; stack.getTransforms().setAll(new Scale(zoom, zoom, 0, 0)); stack.applyCss(); stack.autosize(); stack.layout();
    }
    @Override public void close() {
        ViewNodeRenderer.requireFxThread(); if (closed) return; closed = true; parts.values().forEach(PartView::close); parts.clear(); coordinator.cancelOccurrence(id);
        stack.getChildren().clear(); viewport.setContent(null); setCenter(null); setTop(null); setBottom(null);
    }
}
