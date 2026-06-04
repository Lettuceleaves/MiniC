package minic.ui;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import minic.ui.control.MiniCControlTargetType;
import minic.ui.control.MiniCGraphViewportAdapter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MiniCGraphViewportAdapterTest {
    private static boolean javafxStarted;

    private static void startJavafx() {
        if (javafxStarted) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        javafxStarted = true;
    }

    @Test
    void exposesGraphViewportCapabilitiesAndMouseCenteredZoomCallback() {
        startJavafx();
        runOnFxThread(() -> {
            Pane content = fixedContent(800, 700);
            ScrollPane scrollPane = laidOutScrollPane(content);
            AtomicReference<Point2D> zoomPoint = new AtomicReference<>();
            AtomicReference<Double> zoomDelta = new AtomicReference<>();
            MiniCGraphViewportAdapter adapter = new MiniCGraphViewportAdapter(
                    scrollPane,
                    content,
                    (point, delta) -> {
                        zoomPoint.set(point);
                        zoomDelta.set(delta);
                    }
            );

            adapter.zoomAt(new Point2D(48, 64), 0.25);

            assertThat(adapter.type()).isEqualTo(MiniCControlTargetType.GRAPH);
            assertThat(adapter.canZoom()).isTrue();
            assertThat(adapter.canScrollVertical()).isTrue();
            assertThat(adapter.canScrollHorizontal()).isTrue();
            assertThat(adapter.canPan()).isTrue();
            assertThat(zoomPoint.get()).isEqualTo(new Point2D(48, 64));
            assertThat(zoomDelta.get()).isEqualTo(0.25);
        });
    }

    @Test
    void mouseCenteredZoomKeepsGraphPointUnderMouseStableWhenContentScales() {
        startJavafx();
        runOnFxThread(() -> {
            Pane content = fixedContent(800, 800);
            ScrollPane scrollPane = laidOutScrollPane(content);
            scrollPane.setHvalue(0.35);
            scrollPane.setVvalue(0.40);
            scrollPane.layout();
            Point2D mousePoint = new Point2D(70, 90);
            double anchorX = visibleMinX(scrollPane, content) + mousePoint.getX();
            double anchorY = visibleMinY(scrollPane, content) + mousePoint.getY();
            double scale = 1000.0 / 800.0;
            MiniCGraphViewportAdapter adapter = new MiniCGraphViewportAdapter(scrollPane, content, (point, delta) -> {
                content.setPrefSize(1000, 1000);
                content.setMinSize(1000, 1000);
                content.setMaxSize(1000, 1000);
                content.resize(1000, 1000);
                content.autosize();
                content.layout();
                scrollPane.layout();
            });

            adapter.zoomAt(mousePoint, 0.25);
            scrollPane.layout();

            assertThat(content.getLayoutBounds().getWidth()).isGreaterThan(900);
            assertThat(visibleMinX(scrollPane, content) + mousePoint.getX()).isCloseTo(anchorX * scale, within(2.0));
            assertThat(visibleMinY(scrollPane, content) + mousePoint.getY()).isCloseTo(anchorY * scale, within(2.0));
        });
    }

    @Test
    void scrollAndPanMoveGraphViewportWithinScrollBounds() {
        startJavafx();
        runOnFxThread(() -> {
            Pane content = fixedContent(900, 800);
            ScrollPane scrollPane = laidOutScrollPane(content);
            MiniCGraphViewportAdapter adapter = new MiniCGraphViewportAdapter(scrollPane, content, delta -> {
            });

            adapter.scrollVertical(120);
            adapter.scrollHorizontal(90);

            assertThat(scrollPane.getVvalue()).isGreaterThan(0);
            assertThat(scrollPane.getHvalue()).isGreaterThan(0);

            double afterScrollV = scrollPane.getVvalue();
            double afterScrollH = scrollPane.getHvalue();
            adapter.pan(-45, -60);

            assertThat(scrollPane.getVvalue()).isLessThan(afterScrollV);
            assertThat(scrollPane.getHvalue()).isLessThan(afterScrollH);
            assertThat(scrollPane.getVvalue()).isBetween(scrollPane.getVmin(), scrollPane.getVmax());
            assertThat(scrollPane.getHvalue()).isBetween(scrollPane.getHmin(), scrollPane.getHmax());
        });
    }

    @Test
    void activeCircleAlreadyFullyVisibleDoesNotMoveViewport() {
        startJavafx();
        runOnFxThread(() -> {
            Pane content = fixedContent(700, 700);
            Circle active = new Circle(80, 90, 18);
            active.getStyleClass().add("active");
            content.getChildren().add(active);
            ScrollPane scrollPane = laidOutScrollPane(content);
            MiniCGraphViewportAdapter adapter = new MiniCGraphViewportAdapter(scrollPane, content, delta -> {
            });

            assertThat(adapter.isActiveFullyVisible()).isTrue();
            adapter.centerActiveIfNeeded();

            assertThat(scrollPane.getHvalue()).isEqualTo(0);
            assertThat(scrollPane.getVvalue()).isEqualTo(0);
        });
    }

    @Test
    void partiallyHiddenActiveCircleCentersInViewport() {
        startJavafx();
        runOnFxThread(() -> {
            Pane content = fixedContent(700, 700);
            Circle active = new Circle(500, 420, 20);
            active.getStyleClass().addAll("ast-graph-node", "active");
            content.getChildren().add(active);
            ScrollPane scrollPane = laidOutScrollPane(content);
            MiniCGraphViewportAdapter adapter = new MiniCGraphViewportAdapter(scrollPane, content, delta -> {
            });

            assertThat(adapter.isActiveFullyVisible()).isFalse();
            adapter.centerActive();

            assertThat(visibleCenterX(scrollPane, content)).isCloseTo(active.getCenterX(), within(2.0));
            assertThat(visibleCenterY(scrollPane, content)).isCloseTo(active.getCenterY(), within(2.0));
            assertThat(adapter.isActiveFullyVisible()).isTrue();
        });
    }

    @Test
    void activeScopeRectangleCentersInViewport() {
        startJavafx();
        runOnFxThread(() -> {
            Pane content = fixedContent(720, 720);
            Rectangle active = new Rectangle(450, 320, 48, 64);
            active.getStyleClass().add("active-scope-mask");
            content.getChildren().add(active);
            ScrollPane scrollPane = laidOutScrollPane(content);
            MiniCGraphViewportAdapter adapter = new MiniCGraphViewportAdapter(scrollPane, content, delta -> {
            });

            assertThat(adapter.isActiveFullyVisible()).isFalse();
            adapter.centerActive();

            assertThat(visibleCenterX(scrollPane, content)).isCloseTo(active.getX() + active.getWidth() / 2.0, within(2.0));
            assertThat(visibleCenterY(scrollPane, content)).isCloseTo(active.getY() + active.getHeight() / 2.0, within(2.0));
        });
    }

    @Test
    void activeNonShapeNodesAreIgnoredForGraphTracking() {
        startJavafx();
        runOnFxThread(() -> {
            Pane content = fixedContent(700, 700);
            Text activeText = new Text(500, 500, "active label");
            activeText.getStyleClass().add("active");
            content.getChildren().add(activeText);
            ScrollPane scrollPane = laidOutScrollPane(content);
            MiniCGraphViewportAdapter adapter = new MiniCGraphViewportAdapter(scrollPane, content, delta -> {
            });

            assertThat(adapter.isActiveFullyVisible()).isTrue();
            adapter.centerActive();

            assertThat(scrollPane.getHvalue()).isEqualTo(0);
            assertThat(scrollPane.getVvalue()).isEqualTo(0);
        });
    }

    private static Pane fixedContent(double width, double height) {
        Pane content = new Pane();
        content.setMinSize(width, height);
        content.setPrefSize(width, height);
        content.setMaxSize(width, height);
        return content;
    }

    private static ScrollPane laidOutScrollPane(Pane content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setPrefViewportWidth(200);
        scrollPane.setPrefViewportHeight(200);
        new Scene(scrollPane, 220, 220);
        scrollPane.applyCss();
        scrollPane.resize(220, 220);
        scrollPane.layout();
        return scrollPane;
    }

    private static double visibleCenterX(ScrollPane scrollPane, Pane content) {
        double visibleWidth = scrollPane.getViewportBounds().getWidth();
        double contentWidth = content.getLayoutBounds().getWidth();
        return visibleMinX(scrollPane, content) + visibleWidth / 2.0;
    }

    private static double visibleCenterY(ScrollPane scrollPane, Pane content) {
        double visibleHeight = scrollPane.getViewportBounds().getHeight();
        double contentHeight = content.getLayoutBounds().getHeight();
        return visibleMinY(scrollPane, content) + visibleHeight / 2.0;
    }

    private static double visibleMinX(ScrollPane scrollPane, Pane content) {
        double visibleWidth = scrollPane.getViewportBounds().getWidth();
        double contentWidth = content.getLayoutBounds().getWidth();
        return normalized(scrollPane.getHvalue(), scrollPane.getHmin(), scrollPane.getHmax())
                * Math.max(0, contentWidth - visibleWidth);
    }

    private static double visibleMinY(ScrollPane scrollPane, Pane content) {
        double visibleHeight = scrollPane.getViewportBounds().getHeight();
        double contentHeight = content.getLayoutBounds().getHeight();
        return normalized(scrollPane.getVvalue(), scrollPane.getVmin(), scrollPane.getVmax())
                * Math.max(0, contentHeight - visibleHeight);
    }

    private static double normalized(double value, double min, double max) {
        if (max <= min) {
            return 0;
        }
        return (value - min) / (max - min);
    }

    private static void runOnFxThread(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
