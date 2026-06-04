package minic.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import minic.ui.control.MiniCControlTargetType;
import minic.ui.control.MiniCScrollPaneViewportAdapter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCScrollPaneViewportAdapterTest {
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
    void scrollAndPanMoveGenericScrollPaneViewport() {
        startJavafx();
        runOnFxThread(() -> {
            Pane content = new Pane();
            content.setMinSize(900, 800);
            content.setPrefSize(900, 800);
            ScrollPane scrollPane = laidOutScrollPane(content);
            MiniCScrollPaneViewportAdapter adapter = new MiniCScrollPaneViewportAdapter(scrollPane);

            adapter.scrollVertical(120);
            adapter.scrollHorizontal(90);

            assertThat(adapter.type()).isEqualTo(MiniCControlTargetType.SCROLL);
            assertThat(adapter.canScrollVertical()).isTrue();
            assertThat(adapter.canScrollHorizontal()).isTrue();
            assertThat(adapter.canPan()).isTrue();
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
