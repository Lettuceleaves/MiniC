package minic.uilocal;

import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import minic.color.ThemeRegistry;
import minic.settings.MiniCSettings;
import minic.uilocal.control.MiniCGraphViewportAdapter;
import minic.uilocal.control.MiniCViewportAdapter;
import minic.uilocal.control.MiniCWorkbenchControlHub;
import minic.uiapi.UiAstNodeVisualDto;
import minic.uiapi.UiSemanticScopeVisualDto;
import minic.uiapi.UiSourceSpanDto;
import minic.uiapi.UiStageVisualDto;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class MiniCVisualAstGraphRenderer {
    private static final String ACTIVE_CENTER_Y_KEY = "activeCenterY";
    private static final String AST_DRAG_START_X_KEY = "astDragStartX";
    private static final String AST_DRAG_START_Y_KEY = "astDragStartY";
    private static final String AST_DRAG_START_H_KEY = "astDragStartH";
    private static final String AST_DRAG_START_V_KEY = "astDragStartV";
    private static final String AST_GRAPH_ZOOM_CONTENT_KEY = "minic.uilocal.visual.astGraphZoomContent";

    private final MiniCAstGraphModelFactory astGraphModelFactory = new MiniCAstGraphModelFactory();
    private final Slider astZoom;
    private final Supplier<MiniCWorkbenchControlHub> controlHubSupplier;
    private final Supplier<String> selectedSemanticScopeId;
    private final Consumer<String> semanticScopeSelector;
    private final Runnable refreshAction;
    private final BiFunction<UiAstNodeVisualDto, UiStageVisualDto, MiniCHoverInspectorContent> astContentFactory;
    private final SemanticScopeContentFactory semanticScopeContentFactory;
    private final BiConsumer<Node, MiniCHoverInspectorContent> inspectorAttacher;

    MiniCVisualAstGraphRenderer(
            Slider astZoom,
            Supplier<MiniCWorkbenchControlHub> controlHubSupplier,
            Supplier<String> selectedSemanticScopeId,
            Consumer<String> semanticScopeSelector,
            Runnable refreshAction,
            BiFunction<UiAstNodeVisualDto, UiStageVisualDto, MiniCHoverInspectorContent> astContentFactory,
            SemanticScopeContentFactory semanticScopeContentFactory,
            BiConsumer<Node, MiniCHoverInspectorContent> inspectorAttacher
    ) {
        this.astZoom = astZoom;
        this.controlHubSupplier = controlHubSupplier;
        this.selectedSemanticScopeId = selectedSemanticScopeId;
        this.semanticScopeSelector = semanticScopeSelector;
        this.refreshAction = refreshAction;
        this.astContentFactory = astContentFactory;
        this.semanticScopeContentFactory = semanticScopeContentFactory;
        this.inspectorAttacher = inspectorAttacher;
    }

    VBox zoomableAstGraph(UiStageVisualDto visual) {
        return zoomableAstGraph(visual, false);
    }

    VBox zoomableSemanticAstGraph(UiStageVisualDto visual) {
        return zoomableAstGraph(visual, true);
    }

    void collectGraphViewportAdapters(Node node, List<MiniCViewportAdapter> adapters) {
        Object adapter = node.getProperties().get(MiniCGraphViewportAdapter.ADAPTER_PROPERTY);
        if (adapter instanceof MiniCViewportAdapter viewportAdapter) {
            adapters.add(viewportAdapter);
        }
        if (node instanceof SplitPane pane) {
            pane.getItems().forEach(child -> collectGraphViewportAdapters(child, adapters));
        }
        if (node instanceof ScrollPane pane && pane.getContent() != null) {
            collectGraphViewportAdapters(pane.getContent(), adapters);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectGraphViewportAdapters(child, adapters));
        }
    }

    private Pane astGraph(UiStageVisualDto visual) {
        if (visual == null || visual.astRoot() == null) {
            return emptyPane("AST 尚未就绪");
        }
        MiniCAstGraphModel graph = astGraphModelFactory.create(visual);
        Pane pane = new Pane();
        pane.getStyleClass().add("ast-graph");
        pane.setMinSize(graph.width(), graph.height());
        pane.setPrefSize(graph.width(), graph.height());
        graph.edges().forEach(edge -> {
            Line line = new Line(edge.fromX(), edge.fromY(), edge.toX(), edge.toY());
            line.getStyleClass().add("ast-edge");
            if (edge.hot()) {
                line.getStyleClass().add("hot");
            }
            pane.getChildren().add(line);
        });
        graph.nodes().forEach(node -> addAstNode(pane, visual, node));
        return pane;
    }

    private Pane semanticAstGraph(UiStageVisualDto visual) {
        if (visual == null || visual.astRoot() == null) {
            return emptyPane("AST 尚未就绪");
        }
        MiniCAstGraphModel graph = astGraphModelFactory.create(visual);
        Pane pane = new Pane();
        pane.getStyleClass().add("ast-graph");
        pane.setMinSize(graph.width(), graph.height());
        pane.setPrefSize(graph.width(), graph.height());
        addSemanticScopeMasks(pane, graph, visual);
        graph.edges().forEach(edge -> {
            Line line = new Line(edge.fromX(), edge.fromY(), edge.toX(), edge.toY());
            line.getStyleClass().add("ast-edge");
            if (edge.hot()) {
                line.getStyleClass().add("hot");
            }
            pane.getChildren().add(line);
        });
        graph.nodes().forEach(node -> addAstNode(pane, visual, node));
        return pane;
    }

    private void addAstNode(Pane pane, UiStageVisualDto visual, MiniCAstGraphNode node) {
        Circle circle = new Circle(node.x(), node.y(), node.root() ? 30 : node.leaf() ? 22 : 26);
        circle.getStyleClass().add("ast-graph-node");
        if (node.root()) {
            circle.getStyleClass().add("root");
        }
        if (node.active()) {
            circle.getStyleClass().add("active");
            pane.getProperties().put(ACTIVE_CENTER_Y_KEY, node.y());
        }
        if (node.leaf()) {
            circle.getStyleClass().add("leaf");
        }
        Text text = new Text(shortLabel(node.label()));
        text.getStyleClass().add("ast-graph-label");
        text.setX(node.x() - 32);
        text.setY(node.y() + 4);
        text.setWrappingWidth(64);
        text.setFill(ThemeRegistry.getColor("graph.label"));
        UiAstNodeVisualDto astNode = astNodeById(visual.astRoot(), node.id());
        MiniCHoverInspectorContent content = astContentFactory.apply(astNode, visual);
        inspectorAttacher.accept(circle, content);
        inspectorAttacher.accept(text, content);
        pane.getChildren().addAll(circle, text);
    }

    private void addSemanticScopeMasks(Pane pane, MiniCAstGraphModel graph, UiStageVisualDto visual) {
        List<ScopeEntry> scopes = flattenScopes(visual.semanticRoot());
        for (ScopeEntry entry : scopes) {
            if (entry.scope().range() == null) {
                continue;
            }
            BoundsBox bounds = scopeBounds(entry.scope().range(), graph, visual.astRoot());
            if (bounds == null) {
                continue;
            }
            Rectangle mask = new Rectangle(bounds.x() - 34, bounds.y() - 34, bounds.width() + 68, bounds.height() + 68);
            mask.getStyleClass().add("semantic-graph-scope-mask-" + (entry.depth() % 4));
            mask.setOnMouseClicked(event -> {
                semanticScopeSelector.accept(entry.scope().id());
                refreshAction.run();
                event.consume();
            });
            inspectorAttacher.accept(mask, semanticScopeContentFactory.create(entry.scope(), entry.depth(), visual));
            if (entry.scope().active()) {
                mask.getStyleClass().add("active-scope-mask");
            }
            if (entry.scope().id().equals(selectedSemanticScopeId.get())) {
                mask.getStyleClass().add("selected-scope-mask");
            }
            pane.getChildren().add(mask);
        }
    }

    private BoundsBox scopeBounds(UiSourceSpanDto scopeRange, MiniCAstGraphModel graph, UiAstNodeVisualDto root) {
        ArrayList<MiniCAstGraphNode> covered = new ArrayList<>();
        collectCoveredGraphNodes(scopeRange, root, graph, covered);
        if (covered.isEmpty()) {
            return null;
        }
        double minX = covered.stream().mapToDouble(MiniCAstGraphNode::x).min().orElse(0);
        double maxX = covered.stream().mapToDouble(MiniCAstGraphNode::x).max().orElse(0);
        double minY = covered.stream().mapToDouble(MiniCAstGraphNode::y).min().orElse(0);
        double maxY = covered.stream().mapToDouble(MiniCAstGraphNode::y).max().orElse(0);
        return new BoundsBox(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    private void collectCoveredGraphNodes(
            UiSourceSpanDto scopeRange,
            UiAstNodeVisualDto astNode,
            MiniCAstGraphModel graph,
            ArrayList<MiniCAstGraphNode> covered
    ) {
        if (astNode.range() != null && contains(scopeRange, astNode.range())) {
            graph.nodes().stream()
                    .filter(node -> node.id().equals(astNode.id()))
                    .findFirst()
                    .ifPresent(covered::add);
        }
        astNode.children().forEach(child -> collectCoveredGraphNodes(scopeRange, child, graph, covered));
    }

    private VBox zoomableAstGraph(UiStageVisualDto visual, boolean semanticMasks) {
        VBox box = new VBox(6);
        box.getStyleClass().add("ast-zoom-box");
        HBox controls = new HBox(8);
        controls.getStyleClass().add("ast-zoom-controls");
        Label title = new Label("缩放");
        title.getStyleClass().add("ast-zoom-label");
        Label value = new Label();
        value.getStyleClass().add("ast-zoom-value");
        value.textProperty().bind(astZoom.valueProperty().multiply(100).asString("%.1f%%"));
        controls.getChildren().addAll(title, astZoom, value);
        Pane graph = semanticMasks ? semanticAstGraph(visual) : astGraph(visual);
        Group graphGroup = new Group(graph);
        double baseWidth = graph.getPrefWidth();
        double baseHeight = graph.getPrefHeight();
        graph.scaleXProperty().bind(astZoom.valueProperty());
        graph.scaleYProperty().bind(astZoom.valueProperty());
        graph.setManaged(false);
        graphGroup.setManaged(false);
        Pane graphViewport = new Pane(graphGroup);
        graphViewport.getStyleClass().add("ast-graph-viewport");
        graphViewport.getProperties().put(AST_GRAPH_ZOOM_CONTENT_KEY, graph);
        resizeGraphViewport(graphViewport, baseWidth, baseHeight, astZoom.getValue());
        configureAstGraphWheelZoom(graphViewport);
        configureAstGraphDrag(graphViewport);
        installGraphAdapterLater(graphViewport);
        updateZoomedActiveMarker(box, graph, astZoom.getValue());
        astZoom.valueProperty().addListener((observable, oldValue, newValue) -> {
            resizeGraphViewport(graphViewport, baseWidth, baseHeight, newValue.doubleValue());
            updateZoomedActiveMarker(box, graph, newValue.doubleValue());
        });
        box.getChildren().addAll(controls, graphViewport);
        box.setMinWidth(0);
        return box;
    }

    private void configureAstGraphWheelZoom(Pane graphViewport) {
        graphViewport.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0) {
                return;
            }
            double delta = event.getDeltaY() > 0 ? graphZoomStep() : -graphZoomStep();
            MiniCGraphViewportAdapter adapter = graphViewportAdapter(graphViewport);
            MiniCWorkbenchControlHub controlHub = controlHubSupplier.get();
            if (adapter == null) {
                setAstZoom(astZoom.getValue() + delta);
            } else if (controlHub != null) {
                controlHub.viewportRegistry().businessActive(adapter);
                adapter.zoomAt(graphZoomPoint(graphViewport, event.getX(), event.getY()), delta);
            } else {
                adapter.zoomAt(graphZoomPoint(graphViewport, event.getX(), event.getY()), delta);
            }
            event.consume();
        });
    }

    private void configureAstGraphDrag(Pane graphViewport) {
        graphViewport.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.SECONDARY) {
                return;
            }
            ScrollPane scrollPane = nearestScrollPane(graphViewport);
            MiniCGraphViewportAdapter adapter = graphViewportAdapter(graphViewport);
            if (scrollPane == null && adapter == null) {
                return;
            }
            graphViewport.getProperties().put(AST_DRAG_START_X_KEY, event.getScreenX());
            graphViewport.getProperties().put(AST_DRAG_START_Y_KEY, event.getScreenY());
            if (scrollPane != null) {
                graphViewport.getProperties().put(AST_DRAG_START_H_KEY, scrollPane.getHvalue());
                graphViewport.getProperties().put(AST_DRAG_START_V_KEY, scrollPane.getVvalue());
            }
            event.consume();
        });
        graphViewport.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isSecondaryButtonDown()) {
                return;
            }
            Object startX = graphViewport.getProperties().get(AST_DRAG_START_X_KEY);
            Object startY = graphViewport.getProperties().get(AST_DRAG_START_Y_KEY);
            MiniCGraphViewportAdapter adapter = graphViewportAdapter(graphViewport);
            MiniCWorkbenchControlHub controlHub = controlHubSupplier.get();
            if (adapter != null && startX instanceof Number x && startY instanceof Number y) {
                double deltaX = x.doubleValue() - event.getScreenX();
                double deltaY = y.doubleValue() - event.getScreenY();
                if (controlHub != null) {
                    controlHub.viewportRegistry().businessActive(adapter);
                    controlHub.handlePan(deltaX, deltaY);
                } else {
                    adapter.pan(deltaX, deltaY);
                }
                graphViewport.getProperties().put(AST_DRAG_START_X_KEY, event.getScreenX());
                graphViewport.getProperties().put(AST_DRAG_START_Y_KEY, event.getScreenY());
                event.consume();
                return;
            }
            ScrollPane scrollPane = nearestScrollPane(graphViewport);
            if (scrollPane == null) {
                return;
            }
            Object startH = graphViewport.getProperties().get(AST_DRAG_START_H_KEY);
            Object startV = graphViewport.getProperties().get(AST_DRAG_START_V_KEY);
            if (!(startX instanceof Number x)
                    || !(startY instanceof Number y)
                    || !(startH instanceof Number h)
                    || !(startV instanceof Number v)) {
                return;
            }
            double contentWidth = scrollPane.getContent().getBoundsInLocal().getWidth();
            double contentHeight = scrollPane.getContent().getBoundsInLocal().getHeight();
            double viewportWidth = scrollPane.getViewportBounds().getWidth();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double maxX = Math.max(1, contentWidth - viewportWidth);
            double maxY = Math.max(1, contentHeight - viewportHeight);
            double deltaX = x.doubleValue() - event.getScreenX();
            double deltaY = y.doubleValue() - event.getScreenY();
            scrollPane.setHvalue(clamp(h.doubleValue() + deltaX / maxX));
            scrollPane.setVvalue(clamp(v.doubleValue() + deltaY / maxY));
            event.consume();
        });
    }

    private void installGraphAdapterLater(Pane graphViewport) {
        Platform.runLater(() -> graphViewportAdapter(graphViewport));
    }

    private MiniCGraphViewportAdapter graphViewportAdapter(Pane graphViewport) {
        Object existing = graphViewport.getProperties().get(MiniCGraphViewportAdapter.ADAPTER_PROPERTY);
        if (existing instanceof MiniCGraphViewportAdapter adapter) {
            return adapter;
        }
        ScrollPane scrollPane = nearestScrollPane(graphViewport);
        if (scrollPane == null) {
            return null;
        }
        MiniCGraphViewportAdapter adapter = new MiniCGraphViewportAdapter(
                scrollPane,
                graphZoomContent(graphViewport),
                (point, delta) -> setAstZoom(astZoom.getValue() + delta)
        );
        graphViewport.getProperties().put(MiniCGraphViewportAdapter.ADAPTER_PROPERTY, adapter);
        MiniCWorkbenchControlHub controlHub = controlHubSupplier.get();
        if (controlHub != null) {
            controlHub.installViewportTarget(graphViewport, adapter);
        }
        return adapter;
    }

    private ScrollPane nearestScrollPane(Node node) {
        Parent parent = node.getParent();
        while (parent != null) {
            if (parent instanceof ScrollPane scrollPane) {
                return scrollPane;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private Point2D graphZoomPoint(Pane graphViewport, double localX, double localY) {
        Node zoomContent = graphZoomContent(graphViewport);
        if (MiniCSettings.graphZoomAnchoredAtMouse()) {
            return zoomContent.sceneToLocal(graphViewport.localToScene(localX, localY));
        }
        ScrollPane scrollPane = nearestScrollPane(graphViewport);
        if (scrollPane == null || scrollPane.getContent() == null) {
            return zoomContent.sceneToLocal(graphViewport.localToScene(localX, localY));
        }
        Point2D viewportCenter = new Point2D(
                scrollPane.getViewportBounds().getWidth() / 2.0,
                scrollPane.getViewportBounds().getHeight() / 2.0
        );
        return graphLocalPointFromViewportPoint(zoomContent, scrollPane, viewportCenter);
    }

    private Point2D graphLocalPointFromViewportPoint(
            Node zoomContent,
            ScrollPane scrollPane,
            Point2D viewportPoint
    ) {
        Node content = scrollPane.getContent();
        Bounds contentBounds = content.getLayoutBounds();
        Bounds viewport = scrollPane.getViewportBounds();
        double visibleMinX = visibleMin(
                scrollPane.getHvalue(),
                scrollPane.getHmin(),
                scrollPane.getHmax(),
                contentBounds.getMinX(),
                contentBounds.getWidth(),
                viewport.getWidth()
        );
        double visibleMinY = visibleMin(
                scrollPane.getVvalue(),
                scrollPane.getVmin(),
                scrollPane.getVmax(),
                contentBounds.getMinY(),
                contentBounds.getHeight(),
                viewport.getHeight()
        );
        Point2D contentPoint = new Point2D(
                visibleMinX + viewportPoint.getX(),
                visibleMinY + viewportPoint.getY()
        );
        return zoomContent.sceneToLocal(content.localToScene(contentPoint));
    }

    private Node graphZoomContent(Pane graphViewport) {
        Object content = graphViewport.getProperties().get(AST_GRAPH_ZOOM_CONTENT_KEY);
        return content instanceof Node node ? node : graphViewport;
    }

    private void resizeGraphViewport(Pane graphViewport, double baseWidth, double baseHeight, double zoom) {
        double width = Math.max(1, baseWidth * zoom);
        double height = Math.max(1, baseHeight * zoom);
        graphViewport.setMinSize(width, height);
        graphViewport.setPrefSize(width, height);
    }

    double graphZoomStep() {
        return MiniCSettings.graphZoomStep();
    }

    private void setAstZoom(double value) {
        astZoom.setValue(Math.max(astZoom.getMin(), Math.min(astZoom.getMax(), value)));
    }

    private double visibleMin(
            double value,
            double min,
            double max,
            double contentMin,
            double contentSize,
            double viewportSize
    ) {
        double maxOffset = Math.max(0, contentSize - viewportSize);
        if (max <= min) {
            return contentMin;
        }
        return contentMin + clamp((value - min) / (max - min)) * maxOffset;
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private void updateZoomedActiveMarker(VBox box, Pane graph, double zoom) {
        Object marker = graph.getProperties().get(ACTIVE_CENTER_Y_KEY);
        if (marker instanceof Number number) {
            box.getProperties().put(ACTIVE_CENTER_Y_KEY, 38 + number.doubleValue() * zoom);
        }
    }

    private Pane emptyPane(String message) {
        Pane pane = new Pane();
        pane.getStyleClass().add("ast-graph");
        Label label = new Label(message);
        label.getStyleClass().add("body-text");
        pane.getChildren().add(label);
        pane.setMinSize(360, 180);
        pane.setPrefSize(360, 180);
        return pane;
    }

    private String shortLabel(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        String compact = label.replace('\n', ' ').strip();
        return compact.length() <= 10 ? compact : compact.substring(0, 9) + "...";
    }

    private List<ScopeEntry> flattenScopes(UiSemanticScopeVisualDto root) {
        if (root == null) {
            return List.of();
        }
        ArrayList<ScopeEntry> scopes = new ArrayList<>();
        flattenScopes(root, 0, scopes);
        return scopes;
    }

    private void flattenScopes(UiSemanticScopeVisualDto scope, int depth, ArrayList<ScopeEntry> scopes) {
        scopes.add(new ScopeEntry(scope, depth));
        scope.children().forEach(child -> flattenScopes(child, depth + 1, scopes));
    }

    private boolean contains(UiSourceSpanDto outer, UiSourceSpanDto inner) {
        return outer.sourceName().equals(inner.sourceName())
                && outer.startOffset() <= inner.startOffset()
                && outer.endOffset() >= inner.endOffset();
    }

    private UiAstNodeVisualDto astNodeById(UiAstNodeVisualDto node, String id) {
        if (node == null) {
            return null;
        }
        if (node.id().equals(id)) {
            return node;
        }
        for (UiAstNodeVisualDto child : node.children()) {
            UiAstNodeVisualDto found = astNodeById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @FunctionalInterface
    interface SemanticScopeContentFactory {
        MiniCHoverInspectorContent create(UiSemanticScopeVisualDto scope, int depth, UiStageVisualDto visual);
    }

    private record ScopeEntry(UiSemanticScopeVisualDto scope, int depth) {
    }

    private record BoundsBox(double x, double y, double width, double height) {
    }
}
