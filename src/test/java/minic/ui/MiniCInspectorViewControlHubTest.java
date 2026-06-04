package minic.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import minic.ui.control.MiniCWorkbenchControlHub;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCInspectorViewControlHubTest {
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
    void compilerButtonsExecuteThroughSharedControlHubTrackingAction() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            new MiniCWorkbenchController(viewModel).startDefaultSession();
            MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
            AtomicInteger trackingCalls = new AtomicInteger();
            hub.setActiveTrackingAction(trackingCalls::incrementAndGet);
            hub.setActiveTrackingScheduler(Runnable::run);
            MiniCInspectorView inspector = new MiniCInspectorView(viewModel, hub);

            button(inspector, "下一步").fire();

            assertThat(trackingCalls).hasValue(1);
        });
    }

    private static void runOnFxThread(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
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

    private static Button button(javafx.scene.Node node, String text) {
        if (node instanceof Button button && button.getText().equals(text)) {
            return button;
        }
        if (node instanceof SplitPane splitPane) {
            for (javafx.scene.Node child : splitPane.getItems()) {
                Button found = button(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Button found = button(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
