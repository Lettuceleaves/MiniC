package minic.uilocal;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.control.SplitPane;
import minic.settings.MiniCSettings;
import minic.uilocal.control.MiniCGraphViewportAdapter;
import minic.uilocal.control.MiniCScrollPaneViewportAdapter;
import minic.uilocal.control.MiniCViewportAdapter;
import minic.uilocal.control.MiniCWorkbenchControlHub;

import java.util.ArrayList;
import java.util.List;

final class MiniCDebugViewportController {
    private static final String AST_DRAG_START_X_KEY = "debugAstDragX";
    private static final String AST_DRAG_START_Y_KEY = "debugAstDragY";
    private static final String AST_DRAG_START_H_KEY = "debugAstDragH";
    private static final String AST_DRAG_START_V_KEY = "debugAstDragV";
    private static final String AST_GRAPH_ZOOM_CONTENT_KEY = "minic.uilocal.debug.astGraphZoomContent";
    private static final String SCROLL_VIEWPORT_FILTER_INSTALLED_KEY =
            "minic.uilocal.debug.scrollViewportFilterInstalled";
    private static final String SCROLL_DRAG_START_X_KEY = "debugScrollDragX";
    private static final String SCROLL_DRAG_START_Y_KEY = "debugScrollDragY";

    private final MiniCWorkbenchControlHub controlHub;
    private final Slider astZoom;

    MiniCDebugViewportController(MiniCWorkbenchControlHub controlHub, Slider astZoom) {
        this.controlHub = controlHub;
        this.astZoom = astZoom;
    }

    void configureAstGraphViewport(Pane graphViewport, Node zoomContent, double baseWidth, double baseHeight) {
        graphViewport.getProperties().put(AST_GRAPH_ZOOM_CONTENT_KEY, zoomContent);
        resizeGraphViewport(graphViewport, baseWidth, baseHeight, astZoom.getValue());
        configureAstWheelZoom(graphViewport);
        configureAstDrag(graphViewport);
        installGraphAdapterLater(graphViewport);
        astZoom.valueProperty().addListener((observable, oldValue, newValue) ->
                resizeGraphViewport(graphViewport, baseWidth, baseHeight, newValue.doubleValue()));
    }

    void installScrollViewportTarget(ScrollPane scrollPane) {
        MiniCScrollPaneViewportAdapter adapter = scrollPaneViewportAdapter(scrollPane);
        controlHub.installViewportTarget(scrollPane, adapter);
        if (Boolean.TRUE.equals(scrollPane.getProperties().get(SCROLL_VIEWPORT_FILTER_INSTALLED_KEY))) {
            return;
        }
        scrollPane.getProperties().put(SCROLL_VIEWPORT_FILTER_INSTALLED_KEY, true);
        scrollPane.addEventHandler(ScrollEvent.SCROLL, event -> {
            controlHub.viewportRegistry().businessActive(adapter);
            if (event.isShiftDown() && event.getDeltaY() != 0) {
                controlHub.handleScrollHorizontal(-event.getDeltaY());
                event.consume();
                return;
            }
            if (event.getDeltaY() != 0) {
                controlHub.handleScrollVertical(-event.getDeltaY());
                event.consume();
                return;
            }
            if (event.getDeltaX() != 0) {
                controlHub.handleScrollHorizontal(-event.getDeltaX());
                event.consume();
            }
        });
        scrollPane.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.SECONDARY) {
                return;
            }
            controlHub.viewportRegistry().businessActive(adapter);
            scrollPane.getProperties().put(SCROLL_DRAG_START_X_KEY, event.getScreenX());
            scrollPane.getProperties().put(SCROLL_DRAG_START_Y_KEY, event.getScreenY());
            event.consume();
        });
        scrollPane.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isSecondaryButtonDown()) {
                return;
            }
            Object startX = scrollPane.getProperties().get(SCROLL_DRAG_START_X_KEY);
            Object startY = scrollPane.getProperties().get(SCROLL_DRAG_START_Y_KEY);
            if (!(startX instanceof Number x) || !(startY instanceof Number y)) {
                return;
            }
            controlHub.viewportRegistry().businessActive(adapter);
            controlHub.handlePan(x.doubleValue() - event.getScreenX(), y.doubleValue() - event.getScreenY());
            scrollPane.getProperties().put(SCROLL_DRAG_START_X_KEY, event.getScreenX());
            scrollPane.getProperties().put(SCROLL_DRAG_START_Y_KEY, event.getScreenY());
            event.consume();
        });
    }

    List<MiniCViewportAdapter> activeViewportAdapters(Node root, MiniCViewportAdapter sourceAdapter) {
        ArrayList<MiniCViewportAdapter> adapters = new ArrayList<>();
        adapters.add(sourceAdapter);
        collectScrollViewportAdapters(root, adapters);
        collectGraphViewportAdapters(root, adapters);
        return adapters;
    }

    double graphZoomStep() {
        return MiniCSettings.graphZoomStep();
    }

    private void configureAstWheelZoom(Pane graphViewport) {
        graphViewport.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0) {
                return;
            }
            double delta = event.getDeltaY() > 0 ? graphZoomStep() : -graphZoomStep();
            MiniCGraphViewportAdapter adapter = graphViewportAdapter(graphViewport);
            if (adapter == null) {
                setAstZoom(astZoom.getValue() + delta);
            } else {
                controlHub.viewportRegistry().businessActive(adapter);
                adapter.zoomAt(graphZoomPoint(graphViewport, event.getX(), event.getY()), delta);
            }
            event.consume();
        });
    }

    private void configureAstDrag(Pane graphViewport) {
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
            if (adapter != null && startX instanceof Number x && startY instanceof Number y) {
                double deltaX = x.doubleValue() - event.getScreenX();
                double deltaY = y.doubleValue() - event.getScreenY();
                controlHub.viewportRegistry().businessActive(adapter);
                controlHub.handlePan(deltaX, deltaY);
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
        controlHub.installViewportTarget(graphViewport, adapter);
        return adapter;
    }

    private MiniCScrollPaneViewportAdapter scrollPaneViewportAdapter(ScrollPane scrollPane) {
        Object existing = scrollPane.getProperties().get(MiniCScrollPaneViewportAdapter.ADAPTER_PROPERTY);
        if (existing instanceof MiniCScrollPaneViewportAdapter adapter) {
            return adapter;
        }
        MiniCScrollPaneViewportAdapter adapter = new MiniCScrollPaneViewportAdapter(scrollPane);
        scrollPane.getProperties().put(MiniCScrollPaneViewportAdapter.ADAPTER_PROPERTY, adapter);
        return adapter;
    }

    private void collectScrollViewportAdapters(Node node, List<MiniCViewportAdapter> adapters) {
        Object adapter = node.getProperties().get(MiniCScrollPaneViewportAdapter.ADAPTER_PROPERTY);
        if (adapter instanceof MiniCViewportAdapter viewportAdapter) {
            adapters.add(viewportAdapter);
        }
        if (node instanceof SplitPane pane) {
            pane.getItems().forEach(child -> collectScrollViewportAdapters(child, adapters));
        }
        if (node instanceof ScrollPane pane && pane.getContent() != null) {
            collectScrollViewportAdapters(pane.getContent(), adapters);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectScrollViewportAdapters(child, adapters));
        }
    }

    private void collectGraphViewportAdapters(Node node, List<MiniCViewportAdapter> adapters) {
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
}
