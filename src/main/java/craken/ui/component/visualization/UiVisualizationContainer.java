package craken.ui.component.visualization;

import craken.visualization.api.*;
import craken.visualization.layout.LayoutCoordinator;
import craken.visualization.layout.*;
import craken.visualization.layout.graphviz.*;
import craken.visualization.model.ContainerModel;
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
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import java.time.Duration;
import java.util.*;

public final class UiVisualizationContainer extends BorderPane implements AutoCloseable {
    private record OccurrenceKey(int index, PageRef page) {}
    private final VisualizationSession session;
    private final LayoutCoordinator coordinator;
    private final boolean ownSession;
    private final HBox pages = new HBox(8);
    private final Label path = new Label();
    private final LinkedHashMap<OccurrenceKey, PageOccurrenceView> visible = new LinkedHashMap<>();
    private final ReadOnlyBooleanWrapper pending = new ReadOnlyBooleanWrapper();
    private final ChangeListener<Number> widthListener = (property, old, value) -> renderFrame();
    private ContainerModel model;
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
        path.setTextFill(ViewNodeRenderer.color(VisualizationTheme.TEXT)); path.setPadding(new Insets(6)); setTop(path); setCenter(pages);
        widthProperty().addListener(widthListener); refresh();
    }
    private static LayoutCoordinator defaultCoordinator() {
        var bridge = new GraphvizProcessBridge(GraphvizRuntimeLocator.resolve(UiVisualizationContainer.class), Duration.ofSeconds(10));
        var engines = new EnumMap<LayoutRequest.Kind, LayoutEngine>(LayoutRequest.Kind.class);
        engines.put(LayoutRequest.Kind.POINT, new ArrayLayout()); engines.put(LayoutRequest.Kind.ARRAY, new ArrayLayout());
        engines.put(LayoutRequest.Kind.LINEAR, new LinearLayout()); engines.put(LayoutRequest.Kind.TREE, new TreeLayout()); engines.put(LayoutRequest.Kind.GRAPH, new StressGraphLayout(bridge));
        return new LayoutCoordinator(engines, Platform::runLater, 2, bridge);
    }
    public VisualizationSession session() { return session; }
    public ContainerModel displayModel() { return model; }
    public void refresh() {
        requireOpen(); var next = session.model();
        if (snapshotMode || model == null || next.id() != model.id() || next.epoch() != modelEpoch) { clearOccurrences(); displayEpoch++; }
        snapshotMode = false; model = next; modelEpoch = next.epoch(); renderFrame();
    }
    public void showSnapshot(VisualizationSnapshot snapshot) {
        requireOpen(); var next = SnapshotCodec.toDisplayModel(Objects.requireNonNull(snapshot));
        clearOccurrences(); displayEpoch++; snapshotMode = true; model = next; modelEpoch = next.epoch(); renderFrame();
    }
    private void renderFrame() {
        if (closed || model == null) return;
        long started = System.nanoTime();
        var occurrences = NavigationResolver.resolve(model).occurrences();
        var widths = new PageWidthAllocator().allocate(occurrences.size(), Math.max(0, getWidth()), 2, 8, 160);
        var nextKeys = new LinkedHashSet<OccurrenceKey>();
        for (int index = widths.firstVisibleIndex(); index < occurrences.size(); index++) {
            var occurrence = occurrences.get(index); nextKeys.add(new OccurrenceKey(index, occurrence.page()));
        }
        visible.keySet().stream().filter(key -> !nextKeys.contains(key)).toList().forEach(key -> visible.remove(key).close());
        var ordered = new LinkedHashMap<OccurrenceKey, PageOccurrenceView>(); int widthIndex = 0;
        for (var key : nextKeys) {
            var occurrence = occurrences.get(key.index()); var page = model.pages().get(key.page().pageId());
            var view = visible.computeIfAbsent(key, unused -> new PageOccurrenceView(++occurrenceSequence, coordinator, this::updatePending));
            view.setZoom(zoom); view.update(page, occurrence, widths.widths().get(widthIndex++), displayEpoch, this::select);
            ordered.put(key, view);
        }
        visible.clear(); visible.putAll(ordered); pages.getChildren().setAll(visible.values());
        path.setText(occurrences.isEmpty() ? "尚未初始化页面" : (widths.firstVisibleIndex() > 0 ? "隐藏 " + widths.firstVisibleIndex() + " 个上游页 · " : "")
                + "路径 " + occurrences.size() + " 页 · 聚焦页 #" + occurrences.getLast().page().pageId());
        updatePending();
        lastFxPreparationNanos = System.nanoTime() - started;
    }
    private void select(OperationPath location) {
        if (closed) return;
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
            if (result.succeeded()) refresh(); else path.setText(result.error().message());
        }
    }
    private void updatePending() { pending.set(!closed && visible.values().stream().flatMap(view -> view.parts().stream()).anyMatch(PartView::isPending)); }
    private void clearOccurrences() { visible.values().forEach(PageOccurrenceView::close); visible.clear(); pages.getChildren().clear(); pending.set(false); }
    public List<PageOccurrenceView> visibleOccurrences() { return List.copyOf(visible.values()); }
    public void setZoom(double zoom) {
        requireOpen(); if (!Double.isFinite(zoom) || zoom <= 0) throw new IllegalArgumentException("Zoom must be finite and positive");
        this.zoom = zoom; visible.values().forEach(view -> view.setZoom(zoom));
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
