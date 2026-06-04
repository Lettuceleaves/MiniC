package minic.ui;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import minic.ui.control.MiniCViewportPointMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MiniCViewportPointMapperTest {
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
    void mapsNestedGraphLocalPointToScrollViewportPoint() {
        startJavafx();
        runOnFxThread(() -> {
            Pane controls = fixedPane(300, 40);
            Pane graphViewport = fixedPane(800, 700);
            VBox content = new VBox(controls, graphViewport);
            content.setPrefSize(800, 740);
            ScrollPane scrollPane = laidOutScrollPane(content);
            scrollPane.setHvalue(0.25);
            scrollPane.setVvalue(0.20);
            scrollPane.layout();

            Point2D point = MiniCViewportPointMapper.toViewportPoint(
                    graphViewport,
                    120,
                    90,
                    scrollPane
            );

            double visibleMinX = visibleMin(
                    scrollPane.getHvalue(),
                    scrollPane.getHmin(),
                    scrollPane.getHmax(),
                    content.getLayoutBounds().getWidth(),
                    scrollPane.getViewportBounds().getWidth()
            );
            double visibleMinY = visibleMin(
                    scrollPane.getVvalue(),
                    scrollPane.getVmin(),
                    scrollPane.getVmax(),
                    content.getLayoutBounds().getHeight(),
                    scrollPane.getViewportBounds().getHeight()
            );
            assertThat(point.getX()).isCloseTo(120 - visibleMinX, within(1.0));
            assertThat(point.getY()).isCloseTo(40 + 90 - visibleMinY, within(1.0));
        });
    }

    private static Pane fixedPane(double width, double height) {
        Pane pane = new Pane();
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        return pane;
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

    private static double visibleMin(
            double value,
            double min,
            double max,
            double contentSize,
            double viewportSize
    ) {
        if (max <= min) {
            return 0;
        }
        double normalized = Math.max(0, Math.min(1, (value - min) / (max - min)));
        return normalized * Math.max(0, contentSize - viewportSize);
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
