package craken.ui.component.visualization;

import craken.visualization.api.*;
import craken.visualization.layout.LayoutCoordinator;
import craken.visualization.model.PageModel;
import craken.visualization.model.ViewNode;
import craken.visualization.navigation.NavigationFrame.PageOccurrence;
import craken.ui.component.UiStyles;
import craken.ui.component.data.UiCollection;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Label;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollBar;
import javafx.scene.Group;
import javafx.scene.input.MouseButton;
import javafx.scene.transform.Scale;
import javafx.geometry.Insets;
import javafx.scene.layout.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.event.EventHandler;
import java.util.*;
import java.util.function.Consumer;

public final class PageOccurrenceView extends BorderPane implements AutoCloseable {
    record ViewportState(double zoom, double horizontal, double vertical) {}
    /** 一个序列行：既保留被投影节点的身份，也携带列表要显示的主文本、元信息与高亮状态。 */
    public record SequenceRow(ViewLocation location, String label, String detail, boolean highlighted) {}
    private final long id;
    private final LayoutCoordinator coordinator;
    private final Runnable changed;
    private final boolean listMode;
    private final ScrollPane viewport = new ScrollPane();
    private final VBox stack = new VBox(16);
    private final Group world = new Group(stack);
    private final Label ready = new Label();
    private final Map<Long, PartView> parts = new LinkedHashMap<>();
    private UiCollection<SequenceRow> sequence;
    private Consumer<SequenceRow> sequenceClick = row -> { };
    private PageOccurrence occurrence;
    private int readyCount;
    private double zoom = 1;
    private boolean closed;
    private boolean updating;
    private ViewportState pendingViewport;
    private final EventHandler<ScrollEvent> zoomGesture = event -> {
        if (event.isControlDown()) { setZoom(Math.max(.05, Math.min(8, zoom * Math.exp(event.getDeltaY() * .002)))); event.consume(); }
    };
    PageOccurrenceView(long id, LayoutCoordinator coordinator, Runnable changed) {
        this(id, coordinator, changed, false);
    }
    PageOccurrenceView(long id, LayoutCoordinator coordinator, Runnable changed, boolean listMode) {
        this.id = id; this.coordinator = coordinator; this.changed = changed; this.listMode = listMode;
        setMinSize(0, 0); setPadding(new Insets(1));
        setBackground(new Background(new BackgroundFill(ViewNodeRenderer.color(VisualizationTheme.GROUP_BACKGROUND), CornerRadii.EMPTY, Insets.EMPTY)));
        setBorder(new Border(new BorderStroke(ViewNodeRenderer.color(VisualizationTheme.GROUP_BORDER), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));
        if (listMode) {
            sequence = new UiCollection<>();
            sequence.getStyleClass().addAll("interaction-list", "pipeline-sequence-list");
            sequence.setStyle("-fx-background-color: #0D1117; -fx-background: #0D1117;"
                    + " -fx-control-inner-background: #0D1117; -fx-control-inner-background-alt: #0D1117;");
            sequence.setPlaceholder(sequencePlaceholder());
            Font font = UiStyles.listFont();
            Text sample = new Text("Ag"); sample.setFont(font);
            sequence.setFixedCellSize(Math.max(24, Math.ceil(sample.getLayoutBounds().getHeight()) + 8));
            sequence.setCellFactory(list -> new SequenceCell());
            setCenter(sequence);
            return;
        }
        stack.setPadding(new Insets(8)); viewport.setContent(world); viewport.setFitToWidth(false); viewport.setPannable(true); viewport.setMinSize(0, 0);
        viewport.setStyle("-fx-background-color: #0D1117; -fx-background: #0D1117;");
        viewport.addEventFilter(ScrollEvent.SCROLL, zoomGesture);
        ready.setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT)); ready.setPadding(new Insets(6)); setCenter(viewport);
    }
    void update(PageModel page, PageOccurrence occurrence, double width, long epoch, Consumer<OperationPath> selection) {
        if (closed) return; updating = true; this.occurrence = occurrence; setMinWidth(width); setPrefWidth(width); setMaxWidth(width);
        if (listMode) {
            sequenceClick = row -> selection.accept(new OperationPath(
                    page.nodes().get(row.location().nodeId()).parents().selected(), row.location()));
            renderSequence(page, occurrence);
            var title = new Label(sequenceTitle(page, sequence.getItems().size()));
            title.setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT));
            var header = new HBox(8, title); header.setPadding(new Insets(8));
            header.setBackground(new Background(new BackgroundFill(
                    ViewNodeRenderer.color(VisualizationTheme.GROUP_BACKGROUND), CornerRadii.EMPTY, Insets.EMPTY)));
            setTop(header); setBottom(null);
            updating = false; restoreScroll(); changed.run();
            return;
        }
        var title = new Label("页 #" + page.ref().pageId() + " · " + page.type().key()); title.setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT));
        var header = new HBox(8, title); header.setPadding(new Insets(8));
        header.setBackground(new Background(new BackgroundFill(
                ViewNodeRenderer.color(VisualizationTheme.GROUP_BACKGROUND), CornerRadii.EMPTY, Insets.EMPTY)));
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
        stack.applyCss(); stack.autosize(); stack.layout(); setZoom(zoom); updating = false; restoreScroll(); changed.run();
    }
    public PageOccurrence occurrence() { return occurrence; }
    public List<PartView> parts() { return List.copyOf(parts.values()); }
    public Map<ViewLocation, Pane> nodeViews() { var nodes = new LinkedHashMap<ViewLocation, Pane>(); parts.values().forEach(part -> nodes.putAll(part.nodeViews())); return Map.copyOf(nodes); }
    public ScrollPane scrollPane() {
        if (!listMode) return viewport;
        var internal = sequence.lookup(".scroll-pane");
        return internal instanceof ScrollPane pane ? pane : null;
    }
    public int readyCount() { return readyCount; }
    public double getZoom() { return zoom; }
    /** 列表呈现页不再用画布 part；调用方可用此标记区分两种页面表达。 */
    public boolean isSequenceList() { return listMode; }
    public int sequenceRowCount() { return sequence == null ? 0 : sequence.getItems().size(); }
    public List<SequenceRow> sequenceRows() { return sequence == null ? List.of() : List.copyOf(sequence.getItems()); }
    private void partChanged() {
        if (closed) return; stack.applyCss(); stack.autosize(); stack.layout(); restoreScroll(); changed.run();
    }
    ViewportState viewportState() {
        return listMode ? new ViewportState(zoom, 0, sequenceVerticalValue())
                : new ViewportState(zoom, viewport.getHvalue(), viewport.getVvalue());
    }
    void restoreViewport(ViewportState state) {
        pendingViewport = state; zoom = state.zoom();
        if (!listMode) setZoom(state.zoom());
        restoreScroll();
    }
    private void restoreScroll() {
        if (pendingViewport == null) return;
        if (listMode) {
            setSequenceVerticalValue(pendingViewport.vertical());
            pendingViewport = null;
            return;
        }
        viewport.setHvalue(pendingViewport.horizontal()); viewport.setVvalue(pendingViewport.vertical());
        if (!updating && !parts.isEmpty() && parts.values().stream().noneMatch(PartView::isPending)) pendingViewport = null;
    }
    void setZoom(double zoom) {
        this.zoom = zoom;
        if (listMode) return;
        stack.getTransforms().setAll(new Scale(zoom, zoom, 0, 0)); stack.applyCss(); stack.autosize(); stack.layout();
    }
    @Override public void close() {
        ViewNodeRenderer.requireFxThread(); if (closed) return; closed = true; parts.values().forEach(PartView::close); parts.clear(); coordinator.cancelOccurrence(id);
        if (listMode) {
            sequence.getItems().clear(); sequenceClick = row -> { };
            setCenter(null); setTop(null); setBottom(null);
            return;
        }
        viewport.removeEventFilter(ScrollEvent.SCROLL, zoomGesture);
        stack.getChildren().clear(); viewport.setContent(null); setCenter(null); setTop(null); setBottom(null);
    }

    /** Pipeline 数组页只画成顺序列表：数组根的槽序即行序，不参与画布布局。 */
    private void renderSequence(PageModel page, PageOccurrence occurrence) {
        var array = page.nodes().values().stream()
                .filter(node -> node.content().kind() == ViewNode.Kind.ARRAY)
                .findFirst().orElse(null);
        var rows = new ArrayList<SequenceRow>();
        if (array != null) {
            var highlights = occurrence.highlights();
            for (var child : array.children()) {
                var node = page.nodes().get(child.nodeId());
                if (node == null || node.content().kind() != ViewNode.Kind.POINT) continue;
                rows.add(new SequenceRow(node.location(), rowLabel(node.content()), rowDetail(node.content()),
                        highlights.contains(node.location())));
            }
        }
        sequence.getItems().setAll(rows);
        if (!rows.isEmpty()) {
            int target = rowIndex(rows, occurrence.node());
            if (target < 0) for (int index = 0; index < rows.size(); index++)
                if (rows.get(index).highlighted()) { target = index; break; }
            if (target >= 0) sequence.scrollTo(target);
        }
    }
    private static int rowIndex(List<SequenceRow> rows, ViewLocation node) {
        if (node == null) return -1;
        for (int index = 0; index < rows.size(); index++) if (rows.get(index).location().equals(node)) return index;
        return -1;
    }
    private static String sequenceTitle(PageModel page, int count) {
        String name = page.nodes().values().stream()
                .filter(node -> node.content().kind() == ViewNode.Kind.ARRAY)
                .map(node -> node.content().label()).filter(label -> !label.isBlank()).findFirst()
                .orElse(page.type().key());
        return name + " · " + count + " 项";
    }
    private static String rowLabel(ViewNode.Spec content) {
        String label = content.label();
        if (!label.isBlank()) return label;
        return content.fields().values().stream().filter(value -> !value.isBlank()).findFirst().orElse("");
    }
    private static String rowDetail(ViewNode.Spec content) {
        String label = content.label();
        return content.fields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !entry.getValue().isBlank() && !entry.getValue().equals(label))
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(" · "));
    }
    private double sequenceVerticalValue() {
        for (var node : sequence.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) return bar.getValue();
        }
        return 0;
    }
    private void setSequenceVerticalValue(double value) {
        for (var node : sequence.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) { bar.setValue(value); return; }
        }
    }
    private static Label sequencePlaceholder() {
        var label = new Label("暂无内容");
        label.getStyleClass().add("interaction-sidebar-title");
        return label;
    }

    private final class SequenceCell extends ListCell<SequenceRow> {
        private static final PseudoClass HIGHLIGHTED = PseudoClass.getPseudoClass("highlighted");
        private final Label index = new Label();
        private final Label label = new Label();
        private final Label detail = new Label();
        private final HBox box = new HBox(10, index, label, detail);

        SequenceCell() {
            setFont(UiStyles.listFont());
            setAlignment(Pos.CENTER_LEFT);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setMinWidth(0);
            setPrefWidth(0);
            index.getStyleClass().add("pipeline-sequence-index");
            label.getStyleClass().add("pipeline-sequence-label");
            detail.getStyleClass().add("pipeline-sequence-detail");
            index.setTextFill(ViewNodeRenderer.color(VisualizationTheme.IDE_MUTED_TEXT));
            label.setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT));
            detail.setTextFill(ViewNodeRenderer.color(VisualizationTheme.IDE_MUTED_TEXT));
            index.setMinWidth(Region.USE_PREF_SIZE);
            label.setMinWidth(0);
            label.setMaxWidth(Double.MAX_VALUE);
            label.setTextOverrun(OverrunStyle.ELLIPSIS);
            detail.setMinWidth(0);
            detail.setMaxWidth(320);
            detail.setTextOverrun(OverrunStyle.ELLIPSIS);
            HBox.setHgrow(label, Priority.ALWAYS);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(0, 8, 0, 2));
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && getItem() != null) {
                    sequenceClick.accept(getItem());
                    event.consume();
                }
            });
        }

        @Override
        protected void updateItem(SequenceRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null); setGraphic(null); setAccessibleText(null);
                pseudoClassStateChanged(HIGHLIGHTED, false);
                return;
            }
            String main = row.label().isBlank() ? row.detail() : row.label();
            String secondary = row.label().isBlank() ? "" : row.detail();
            index.setText("[" + getIndex() + "]");
            label.setText(main);
            detail.setText(secondary);
            detail.setVisible(!secondary.isEmpty());
            detail.setManaged(!secondary.isEmpty());
            setText(null); setGraphic(box);
            pseudoClassStateChanged(HIGHLIGHTED, row.highlighted());
            setAccessibleText((main + " " + secondary).trim());
        }
    }
}
