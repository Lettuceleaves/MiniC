package minic.ui.control;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;

public final class MiniCGraphViewportAdapter implements MiniCViewportAdapter {
    public static final String ADAPTER_PROPERTY = "minic.ui.control.graphViewportAdapter";

    private static final double VISIBILITY_EPSILON = 0.5;

    private final ScrollPane scrollPane;
    private final Node graphContent;
    private final BiConsumer<Point2D, Double> zoomCallback;

    public MiniCGraphViewportAdapter(
            ScrollPane scrollPane,
            Node graphContent,
            DoubleConsumer zoomDeltaConsumer
    ) {
        this(
                scrollPane,
                graphContent,
                (point, delta) -> Objects.requireNonNull(zoomDeltaConsumer, "zoomDeltaConsumer").accept(delta)
        );
    }

    public MiniCGraphViewportAdapter(
            ScrollPane scrollPane,
            Node graphContent,
            BiConsumer<Point2D, Double> zoomCallback
    ) {
        this.scrollPane = Objects.requireNonNull(scrollPane, "scrollPane");
        this.graphContent = Objects.requireNonNull(graphContent, "graphContent");
        this.zoomCallback = Objects.requireNonNull(zoomCallback, "zoomCallback");
    }

    @Override
    public MiniCControlTargetType type() {
        return MiniCControlTargetType.GRAPH;
    }

    @Override
    public boolean canZoom() {
        return true;
    }

    @Override
    public void zoomAt(Point2D localPoint, double delta) {
        Point2D graphPoint = localPoint == null ? Point2D.ZERO : localPoint;
        Bounds viewport = scrollPane.getViewportBounds();
        Bounds before = scrollContentBounds();
        if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0) {
            zoomCallback.accept(graphPoint, delta);
            return;
        }
        Node content = scrollContent();
        Point2D beforeAnchor = toContentPoint(content, graphPoint);
        Point2D sceneAnchor = graphContent.localToScene(graphPoint);
        double visibleMinX = visibleMin(
                scrollPane.getHvalue(),
                scrollPane.getHmin(),
                scrollPane.getHmax(),
                before.getMinX(),
                before.getWidth(),
                viewport.getWidth()
        );
        double visibleMinY = visibleMin(
                scrollPane.getVvalue(),
                scrollPane.getVmin(),
                scrollPane.getVmax(),
                before.getMinY(),
                before.getHeight(),
                viewport.getHeight()
        );
        Point2D viewportPoint = new Point2D(
                beforeAnchor.getX() - visibleMinX,
                beforeAnchor.getY() - visibleMinY
        );

        zoomCallback.accept(graphPoint, delta);
        scrollPane.applyCss();
        scrollPane.layout();

        Bounds after = scrollContentBounds();
        Point2D afterAnchor = toContentPoint(scrollContent(), graphPoint);
        setAxisToVisibleMin(
                afterAnchor.getX() - viewportPoint.getX(),
                after.getMinX(),
                after.getWidth(),
                viewport.getWidth(),
                scrollPane.getHmin(),
                scrollPane.getHmax(),
                scrollPane::setHvalue
        );
        setAxisToVisibleMin(
                afterAnchor.getY() - viewportPoint.getY(),
                after.getMinY(),
                after.getHeight(),
                viewport.getHeight(),
                scrollPane.getVmin(),
                scrollPane.getVmax(),
                scrollPane::setVvalue
        );
        scrollPane.applyCss();
        scrollPane.layout();
        compensateResidualTranslation(graphPoint, sceneAnchor);
    }

    private Point2D toContentPoint(Node content, Point2D graphPoint) {
        if (content == graphContent) {
            return graphPoint;
        }
        return content.sceneToLocal(graphContent.localToScene(graphPoint));
    }

    private void compensateResidualTranslation(Point2D graphPoint, Point2D targetScenePoint) {
        if (targetScenePoint == null || graphContent.getScene() == null) {
            return;
        }
        Point2D afterScenePoint = graphContent.localToScene(graphPoint);
        double sceneDeltaX = targetScenePoint.getX() - afterScenePoint.getX();
        double sceneDeltaY = targetScenePoint.getY() - afterScenePoint.getY();
        if (Math.abs(sceneDeltaX) < 0.01 && Math.abs(sceneDeltaY) < 0.01) {
            return;
        }
        Node translationTarget = residualTranslationTarget();
        Parent parent = translationTarget.getParent();
        Point2D parentDelta;
        if (parent == null || parent.getScene() == null) {
            parentDelta = new Point2D(sceneDeltaX, sceneDeltaY);
        } else {
            Point2D targetParent = parent.sceneToLocal(targetScenePoint);
            Point2D afterParent = parent.sceneToLocal(afterScenePoint);
            parentDelta = targetParent.subtract(afterParent);
        }
        translationTarget.setTranslateX(translationTarget.getTranslateX() + parentDelta.getX());
        translationTarget.setTranslateY(translationTarget.getTranslateY() + parentDelta.getY());
        scrollPane.applyCss();
        scrollPane.layout();
    }

    private Node residualTranslationTarget() {
        Parent parent = graphContent.getParent();
        Node content = scrollContent();
        if (parent != null && parent != content) {
            return parent;
        }
        return graphContent;
    }

    @Override
    public boolean canScrollVertical() {
        return true;
    }

    @Override
    public void scrollVertical(double delta) {
        scrollBy(delta, Orientation.VERTICAL);
    }

    @Override
    public boolean canScrollHorizontal() {
        return true;
    }

    @Override
    public void scrollHorizontal(double delta) {
        scrollBy(delta, Orientation.HORIZONTAL);
    }

    @Override
    public boolean canPan() {
        return true;
    }

    @Override
    public void pan(double deltaX, double deltaY) {
        scrollHorizontal(deltaX);
        scrollVertical(deltaY);
    }

    @Override
    public boolean isActiveFullyVisible() {
        Optional<Bounds> activeBounds = activeBoundsInScrollContent();
        if (activeBounds.isEmpty()) {
            return true;
        }
        Bounds viewport = scrollPane.getViewportBounds();
        if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0) {
            return true;
        }
        Bounds contentBounds = scrollContentBounds();
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
        double visibleMaxX = visibleMinX + viewport.getWidth();
        double visibleMaxY = visibleMinY + viewport.getHeight();
        Bounds active = activeBounds.get();
        return active.getMinX() >= visibleMinX - VISIBILITY_EPSILON
                && active.getMaxX() <= visibleMaxX + VISIBILITY_EPSILON
                && active.getMinY() >= visibleMinY - VISIBILITY_EPSILON
                && active.getMaxY() <= visibleMaxY + VISIBILITY_EPSILON;
    }

    @Override
    public void centerActive() {
        activeBoundsInScrollContent().ifPresent(active -> {
            Bounds viewport = scrollPane.getViewportBounds();
            if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0) {
                return;
            }
            Bounds contentBounds = scrollContentBounds();
            centerAxis(
                    active.getCenterX(),
                    contentBounds.getMinX(),
                    contentBounds.getWidth(),
                    viewport.getWidth(),
                    scrollPane.getHmin(),
                    scrollPane.getHmax(),
                    scrollPane::setHvalue
            );
            centerAxis(
                    active.getCenterY(),
                    contentBounds.getMinY(),
                    contentBounds.getHeight(),
                    viewport.getHeight(),
                    scrollPane.getVmin(),
                    scrollPane.getVmax(),
                    scrollPane::setVvalue
            );
        });
    }

    private void scrollBy(double delta, Orientation orientation) {
        Bounds viewport = scrollPane.getViewportBounds();
        Bounds contentBounds = scrollContentBounds();
        if (orientation == Orientation.HORIZONTAL) {
            setAxisByDelta(
                    scrollPane.getHvalue(),
                    delta,
                    contentBounds.getWidth(),
                    viewport.getWidth(),
                    scrollPane.getHmin(),
                    scrollPane.getHmax(),
                    scrollPane::setHvalue
            );
        } else {
            setAxisByDelta(
                    scrollPane.getVvalue(),
                    delta,
                    contentBounds.getHeight(),
                    viewport.getHeight(),
                    scrollPane.getVmin(),
                    scrollPane.getVmax(),
                    scrollPane::setVvalue
            );
        }
    }

    private void setAxisByDelta(
            double value,
            double delta,
            double contentSize,
            double viewportSize,
            double min,
            double max,
            DoubleConsumer setter
    ) {
        double maxOffset = Math.max(0, contentSize - viewportSize);
        if (maxOffset <= 0 || max <= min) {
            setter.accept(min);
            return;
        }
        double currentOffset = normalized(value, min, max) * maxOffset;
        double target = clamp((currentOffset + delta) / maxOffset);
        setter.accept(min + target * (max - min));
    }

    private void centerAxis(
            double activeCenter,
            double contentMin,
            double contentSize,
            double viewportSize,
            double min,
            double max,
            DoubleConsumer setter
    ) {
        double maxOffset = Math.max(0, contentSize - viewportSize);
        if (maxOffset <= 0 || max <= min) {
            setter.accept(min);
            return;
        }
        double targetMin = activeCenter - viewportSize / 2.0;
        double offset = Math.max(0, Math.min(targetMin - contentMin, maxOffset));
        setter.accept(min + offset / maxOffset * (max - min));
    }

    private void setAxisToVisibleMin(
            double targetVisibleMin,
            double contentMin,
            double contentSize,
            double viewportSize,
            double min,
            double max,
            DoubleConsumer setter
    ) {
        double maxOffset = Math.max(0, contentSize - viewportSize);
        if (maxOffset <= 0 || max <= min) {
            setter.accept(min);
            return;
        }
        double offset = Math.max(0, Math.min(targetVisibleMin - contentMin, maxOffset));
        setter.accept(min + offset / maxOffset * (max - min));
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
        return contentMin + normalized(value, min, max) * maxOffset;
    }

    private Optional<Bounds> activeBoundsInScrollContent() {
        return activeShape(graphContent)
                .flatMap(active -> boundsInAncestor(active, scrollContent()));
    }

    private Optional<Node> activeShape(Node node) {
        if (isTrackedShape(node) && isActive(node)) {
            return Optional.of(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Optional<Node> active = activeShape(child);
                if (active.isPresent()) {
                    return active;
                }
            }
        }
        return Optional.empty();
    }

    private boolean isTrackedShape(Node node) {
        return node instanceof Circle || node instanceof Rectangle;
    }

    private boolean isActive(Node node) {
        return node.getStyleClass().contains("active")
                || node.getStyleClass().contains("active-scope-mask")
                || node.getStyleClass().contains("current")
                || node.getStyleClass().contains("debug-active")
                || Boolean.TRUE.equals(node.getProperties().get("active"));
    }

    private Optional<Bounds> boundsInAncestor(Node node, Node ancestor) {
        Bounds bounds = node.getBoundsInLocal();
        Node current = node;
        while (current != ancestor) {
            bounds = current.localToParent(bounds);
            Parent parent = current.getParent();
            if (parent == null) {
                return Optional.empty();
            }
            current = parent;
        }
        return Optional.of(bounds);
    }

    private Node scrollContent() {
        return scrollPane.getContent() == null ? graphContent : scrollPane.getContent();
    }

    private Bounds scrollContentBounds() {
        return scrollContent().getLayoutBounds();
    }

    private double normalized(double value, double min, double max) {
        if (max <= min) {
            return 0;
        }
        return clamp((value - min) / (max - min));
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private enum Orientation {
        HORIZONTAL,
        VERTICAL
    }
}
