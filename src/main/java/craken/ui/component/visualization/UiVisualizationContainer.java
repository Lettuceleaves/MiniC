package craken.ui.component.visualization;

import craken.visualization.api.*;
import craken.visualization.layout.LayoutCoordinator;
import craken.visualization.layout.*;
import craken.visualization.layout.graphviz.*;
import craken.visualization.model.ContainerModel;
import craken.visualization.model.PageModel;
import craken.visualization.model.ViewNode;
import craken.visualization.model.InteractionState;
import craken.visualization.snapshot.VisualizationSnapshot;
import craken.visualization.snapshot.SnapshotCodec;
import craken.visualization.navigation.*;
import craken.visualization.mutation.DefaultVisualizationSession;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public final class UiVisualizationContainer extends BorderPane implements AutoCloseable {
    private record OccurrenceKey(PageRef page, ViewLocation node) {}
    private final VisualizationSession session;
    private final LayoutCoordinator coordinator;
    private final boolean ownSession;
    private final HBox pages = new HBox(8);
    private final Label path = new Label();
    private final MenuButton pathPicker = new MenuButton("页面路径");
    private final ListView<OperationPath> pathChoices = new ListView<>();
    private final LinkedHashMap<OccurrenceKey, PageOccurrenceView> visible = new LinkedHashMap<>();
    private final LinkedHashMap<OccurrenceKey, PageOccurrenceView.ViewportState> viewports = new LinkedHashMap<>(16, .75f, true);
    private final ReadOnlyBooleanWrapper pending = new ReadOnlyBooleanWrapper();
    private final ChangeListener<Number> widthListener = (property, old, value) -> renderFrame();
    private ContainerModel model;
    private Consumer<OperationPath> onEntryActivated = location -> {};
    private boolean snapshotMode, closed;
    private long occurrenceSequence, displayEpoch, modelEpoch = -1;
    private long lastFxPreparationNanos;
    private double zoom = 1;
    public UiVisualizationContainer() { this(new DefaultVisualizationSession(), defaultCoordinator(), true); }
    public UiVisualizationContainer(VisualizationSession session) { this(session, defaultCoordinator(), false); }
    public UiVisualizationContainer(VisualizationSession session, LayoutCoordinator coordinator) { this(session, coordinator, false); }
    private UiVisualizationContainer(VisualizationSession session, LayoutCoordinator coordinator, boolean ownSession) {
        ViewNodeRenderer.requireFxThread(); this.session = Objects.requireNonNull(session); this.coordinator = Objects.requireNonNull(coordinator); this.ownSession = ownSession;
        setMinSize(0, 0); setBackground(new Background(new BackgroundFill(ViewNodeRenderer.color(VisualizationTheme.CANVAS), CornerRadii.EMPTY, Insets.EMPTY)));
        path.setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT)); path.setPadding(new Insets(6));
        pathPicker.setId("visualization-path-picker"); pathPicker.setMinWidth(Region.USE_PREF_SIZE);
        pathPicker.setStyle("-fx-text-fill: #FFFFFF; -fx-text-base-color: #FFFFFF; -fx-mark-color: #FFFFFF; -fx-background-color: #161B22;");
        pathChoices.setPrefSize(360, 240); pathChoices.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(OperationPath item, boolean empty) {
                super.updateItem(item, empty); setText(empty || item == null ? null : "页 #" + item.nxt().pageId() + " · 节点 #" + item.nxt().nodeId());
                setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT));
                setBackground(new Background(new BackgroundFill(ViewNodeRenderer.color(VisualizationTheme.GROUP_BACKGROUND), CornerRadii.EMPTY, Insets.EMPTY)));
            }
        });
        pathChoices.setOnMouseClicked(event -> { if (event.getButton() == MouseButton.PRIMARY) choosePath(); });
        pathChoices.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER) { choosePath(); event.consume(); } });
        pathPicker.getItems().add(new CustomMenuItem(pathChoices, false));
        var header = new HBox(6, pathPicker, path);
        // 顶部条属于画布的一部分：显式使用画布底色，避免宿主主题的浅色背景透出。
        header.setBackground(new Background(new BackgroundFill(
                ViewNodeRenderer.color(VisualizationTheme.CANVAS), CornerRadii.EMPTY, Insets.EMPTY)));
        setTop(header); setCenter(pages);
        widthProperty().addListener(widthListener); refresh();
    }
    private static LayoutCoordinator defaultCoordinator() {
        var bridge = new GraphvizProcessBridge(GraphvizRuntimeLocator.resolve(UiVisualizationContainer.class), Duration.ofSeconds(10));
        var engines = new EnumMap<LayoutRequest.Kind, LayoutEngine>(LayoutRequest.Kind.class);
        engines.put(LayoutRequest.Kind.POINT, new ArrayLayout()); engines.put(LayoutRequest.Kind.ARRAY, new ArrayLayout());
        engines.put(LayoutRequest.Kind.LINEAR, new LinearLayout()); engines.put(LayoutRequest.Kind.TREE, new TreeLayout()); engines.put(LayoutRequest.Kind.GRAPH, new StressGraphLayout(bridge));
        engines.put(LayoutRequest.Kind.BUCKETS, new BucketChainLayout());
        return new LayoutCoordinator(engines, Platform::runLater, 2, bridge);
    }
    public VisualizationSession session() { return session; }
    /** 宿主注册"入口激活"扩展：点击任意节点时先询问宿主是否要把该位置展开成新的层。 */
    public void setOnEntryActivated(Consumer<OperationPath> action) {
        this.onEntryActivated = java.util.Objects.requireNonNull(action, "action");
    }
    public ContainerModel displayModel() { return model; }
    /** 调试工作台等单路径宿主隐藏页面路径入口；路径文本与选择状态保留。 */
    public void setPathPickerVisible(boolean visible) {
        requireOpen();
        pathPicker.setVisible(visible);
        pathPicker.setManaged(visible);
    }
    public void refresh() {
        requireOpen(); var next = session.model();
        if (snapshotMode || model == null || next.id() != model.id() || next.epoch() != modelEpoch) { clearOccurrences(); displayEpoch++; }
        if (model != null && next.id() != model.id()) viewports.clear();
        snapshotMode = false; model = next; modelEpoch = next.epoch(); renderFrame();
    }
    public void showSnapshot(VisualizationSnapshot snapshot) {
        requireOpen(); var next = SnapshotCodec.toDisplayModel(Objects.requireNonNull(snapshot));
        clearOccurrences(); if (model != null && next.id() != model.id()) viewports.clear();
        displayEpoch++; snapshotMode = true; model = next; modelEpoch = next.epoch(); renderFrame();
    }
    private void renderFrame() {
        if (closed || model == null) return;
        long started = System.nanoTime();
        var occurrences = NavigationResolver.resolve(model).occurrences();
        var widths = new PageWidthAllocator().allocate(occurrences.size(), Math.max(0, getWidth()), 2, 8, 160);
        var nextKeys = new LinkedHashMap<OccurrenceKey, NavigationFrame.PageOccurrence>();
        for (int index = widths.firstVisibleIndex(); index < occurrences.size(); index++) {
            var occurrence = occurrences.get(index); nextKeys.put(new OccurrenceKey(occurrence.page(), occurrence.node()), occurrence);
        }
        visible.keySet().stream().filter(key -> !nextKeys.containsKey(key)).toList().forEach(this::hideOccurrence);
        var ordered = new LinkedHashMap<OccurrenceKey, PageOccurrenceView>(); int widthIndex = 0;
        for (var entry : nextKeys.entrySet()) {
            var key = entry.getKey(); var occurrence = entry.getValue(); var page = model.pages().get(key.page().pageId());
            var view = visible.get(key);
            if (view == null) {
                view = new PageOccurrenceView(++occurrenceSequence, coordinator, this::updatePending, isSequencePage(page));
                var saved = viewports.get(key); if (saved == null) view.setZoom(zoom); else view.restoreViewport(saved);
            }
            view.update(page, occurrence, widths.widths().get(widthIndex++), displayEpoch, this::select);
            ordered.put(key, view);
        }
        visible.clear(); visible.putAll(ordered); pages.getChildren().setAll(visible.values());
        if (!snapshotMode) viewports.keySet().removeIf(key -> key.node() != null
                && (model.pages().get(key.page().pageId()) == null || !model.pages().get(key.page().pageId()).nodes().containsKey(key.node().nodeId())));
        path.setText(occurrences.isEmpty() ? "尚未初始化页面" : (widths.firstVisibleIndex() > 0 ? "隐藏 " + widths.firstVisibleIndex() + " 个上游页 · " : "")
                + "路径 " + occurrences.size() + " 页 · 聚焦页 #" + occurrences.getLast().page().pageId());
        pathChoices.getItems().setAll(occurrences.stream().filter(o -> o.node() != null)
                .map(o -> new OperationPath(model.node(o.node()).parents().selected(), o.node())).toList());
        pathPicker.setDisable(pathChoices.getItems().isEmpty());
        updatePending();
        lastFxPreparationNanos = System.nanoTime() - started;
    }
    /** Pipeline 把阶段输出投影成“数组根 + 顺序点单元”，这类页用带滚动条的列表组件呈现。 */
    private static boolean isSequencePage(PageModel page) {
        if (!"pipeline-array".equals(page.type().key()) || !page.topology().isEmpty()) return false;
        var arrays = page.nodes().values().stream()
                .filter(node -> node.content().kind() == ViewNode.Kind.ARRAY).toList();
        if (arrays.size() != 1) return false;
        return arrays.getFirst().children().stream().allMatch(child -> {
            ViewNode node = page.nodes().get(child.nodeId());
            return node != null && node.content().kind() == ViewNode.Kind.POINT;
        });
    }
    private void choosePath() {
        var chosen = pathChoices.getSelectionModel().getSelectedItem();
        if (chosen != null) { pathPicker.hide(); select(chosen); }
    }
    private void hideOccurrence(OccurrenceKey key) {
        var view = visible.remove(key); viewports.put(key, view.viewportState());
        view.close();
    }
    private void select(OperationPath location) {
        if (closed) return;
        onEntryActivated.accept(location);
        // 文档的入口语义（两种显示模式一致）：点击带下游页的入口，焦点落到下一层的节点。
        var child = ownedChild(location.nxt());
        if (child != null) location = new OperationPath(location.nxt(), child);
        if (snapshotMode) {
            var node = model.node(location.nxt()); var selected = node.parents();
            if (location.pre() != null) selected = selected.select(location.pre());
            var pages = new LinkedHashMap<>(model.pages()); var page = pages.get(location.nxt().pageId());
            pages.put(page.ref().pageId(), page.withNode(node.withState(node.content(), selected)));
            model = new ContainerModel(model.id(), model.root(), pages, model.version(), model.ownership(), model.pageRules(),
                    new InteractionState(location.nxt(), location.nxt(), AccessKind.READ, model.interaction().options()), model.epoch(), model.sourceStep(), model.sourceVersion());
            renderFrame();
        } else {
            var result = session.modify(MutationBatch.of(new VisualizationCommand.SetFocus(location)));
            if (result.succeeded()) {
                refresh();
            } else path.setText(result.error().message());
        }
    }
    /** 调用方显式注册的层入口（debug:slot:来源）在其它页面上的第一个节点；普通归属不跳页。 */
    private ViewLocation ownedChild(ViewLocation parent) {
        if (model == null) return null;
        return model.ownership().values().stream()
                .filter(binding -> binding.key().pre().equals(parent) && !binding.key().nxt().page().equals(parent.page())
                        && binding.sources().stream().anyMatch(source -> source.startsWith("debug:slot:")))
                .map(binding -> binding.key().nxt())
                .min(Comparator.comparingLong(ViewLocation::nodeId)).orElse(null);
    }
    private void updatePending() { pending.set(!closed && visible.values().stream().flatMap(view -> view.parts().stream()).anyMatch(PartView::isPending)); }
    private void clearOccurrences() { List.copyOf(visible.keySet()).forEach(this::hideOccurrence); pages.getChildren().clear(); pending.set(false); }
    public List<PageOccurrenceView> visibleOccurrences() { return List.copyOf(visible.values()); }
    public void setZoom(double zoom) {
        requireOpen(); if (!Double.isFinite(zoom) || zoom <= 0) throw new IllegalArgumentException("Zoom must be finite and positive");
        this.zoom = zoom; visible.values().forEach(view -> view.setZoom(zoom));
        viewports.replaceAll((key, state) -> new PageOccurrenceView.ViewportState(zoom, state.horizontal(), state.vertical()));
    }
    public double getZoom() { return zoom; }
    public boolean isLayoutPending() { return pending.get(); }
    public ReadOnlyBooleanProperty layoutPendingProperty() { return pending.getReadOnlyProperty(); }
    public boolean isClosed() { return closed; }
    public LayoutCoordinator.Diagnostics diagnostics() { return coordinator.diagnostics(); }
    /** Node construction, measured geometry and request preparation together; not a monitor presentation time. */
    public long lastFxPreparationNanos() { return lastFxPreparationNanos; }
    private void requireOpen() { ViewNodeRenderer.requireFxThread(); if (closed) throw new IllegalStateException("Visualization host closed"); }
    @Override public void close() {
        ViewNodeRenderer.requireFxThread(); if (closed) return; closed = true; widthProperty().removeListener(widthListener);
        Throwable failure = cleanup(this::clearOccurrences, null);
        failure = cleanup(coordinator::close, failure);
        if (ownSession) failure = cleanup(session::close, failure);
        failure = cleanup(() -> setCenter(null), failure); failure = cleanup(() -> setTop(null), failure);
        pathPicker.hide(); pathChoices.getItems().clear(); viewports.clear();
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }
    private static Throwable cleanup(Runnable action, Throwable failure) {
        try { action.run(); } catch (RuntimeException | Error error) {
            if (failure == null) return error;
            if (error != failure) failure.addSuppressed(error);
        }
        return failure;
    }
}
