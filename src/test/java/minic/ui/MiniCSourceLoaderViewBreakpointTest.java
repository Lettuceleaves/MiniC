package minic.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCSourceLoaderViewBreakpointTest {
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
    void sharesBreakpointsAcrossSourceLoaderInstances() {
        startJavafx();
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        MiniCSourceLoaderView compileView = new MiniCSourceLoaderView(viewModel);

        compileView.setBreakpoint(3, true);
        MiniCSourceLoaderView debugView = new MiniCSourceLoaderView(viewModel);

        assertThat(viewModel.debugBreakpointLinesProperty().get()).containsExactly(3);
        assertThat(debugView.breakpointLines()).containsExactly(3);

        debugView.setBreakpoint(5, true);

        assertThat(compileView.breakpointLines()).containsExactly(3, 5);
        assertThat(viewModel.debugBreakpointLinesProperty().get()).containsExactly(3, 5);
    }
}
