package minic.ui;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import minic.ui.control.MiniCControlTargetType;
import minic.ui.control.MiniCViewportAdapter;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCCodeEditorViewportControlTest {
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
    void exposesTextViewportAdapterForZoomAndVerticalScroll() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCCodeEditor editor = editorWithLines(80, 360);
            MiniCViewportAdapter adapter = editor.viewportAdapter();

            assertThat(adapter.type()).isEqualTo(MiniCControlTargetType.TEXT);
            assertThat(adapter.canZoom()).isTrue();
            assertThat(adapter.canScrollVertical()).isTrue();
            assertThat(adapter.canScrollHorizontal()).isFalse();
            assertThat(adapter.canPan()).isFalse();

            double fontBefore = editor.editorFontSizeForTesting();
            adapter.zoomAt(new Point2D(20, 20), 1.0);
            assertThat(editor.editorFontSizeForTesting()).isGreaterThan(fontBefore);

            double scrollBefore = editor.estimatedScrollYForTesting();
            adapter.scrollVertical(120);
            assertThat(editor.estimatedScrollYForTesting()).isGreaterThan(scrollBefore);
        });
    }

    @Test
    void clampsTextViewportZoomToEditorFontBounds() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCCodeEditor editor = new MiniCCodeEditor();
            MiniCViewportAdapter adapter = editor.viewportAdapter();

            for (int index = 0; index < 40; index++) {
                adapter.zoomAt(Point2D.ZERO, 1.0);
            }
            assertThat(editor.editorFontSizeForTesting()).isEqualTo(24.0);

            for (int index = 0; index < 40; index++) {
                adapter.zoomAt(Point2D.ZERO, -1.0);
            }
            assertThat(editor.editorFontSizeForTesting()).isEqualTo(10.0);
        });
    }

    @Test
    void keepsFullyVisibleExecutionLineInPlace() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCCodeEditor editor = editorWithLines(20, 360);
            MiniCViewportAdapter adapter = editor.viewportAdapter();
            editor.setCurrentExecutionLine(3);

            assertThat(adapter.isActiveFullyVisible()).isTrue();
            double scrollBefore = editor.estimatedScrollYForTesting();

            adapter.centerActiveIfNeeded();

            assertThat(editor.estimatedScrollYForTesting()).isEqualTo(scrollBefore);
        });
    }

    @Test
    void centersExecutionLineWhenItIsBelowVisibleViewport() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCCodeEditor editor = editorWithLines(80, 120);
            MiniCViewportAdapter adapter = editor.viewportAdapter();
            editor.setCurrentExecutionLine(70);

            assertThat(adapter.isActiveFullyVisible()).isFalse();

            adapter.centerActiveIfNeeded();

            assertThat(editor.estimatedScrollYForTesting()).isGreaterThan(0);
            assertThat(adapter.isActiveFullyVisible()).isTrue();
        });
    }

    @Test
    void executionRangeTakesPriorityOverExecutionLineWhenCentering() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCCodeEditor editor = editorWithLines(80, 120);
            MiniCViewportAdapter adapter = editor.viewportAdapter();
            String source = editor.getText();
            int rangeStart = offsetForLine(source, 60);
            int rangeEnd = offsetForLine(source, 62);
            editor.setCurrentExecutionLine(2);
            editor.setCurrentExecutionRange(rangeStart, rangeEnd);

            assertThat(adapter.isActiveFullyVisible()).isFalse();

            adapter.centerActiveIfNeeded();

            assertThat(editor.estimatedScrollYForTesting()).isGreaterThan(0);
            assertThat(adapter.isActiveFullyVisible()).isTrue();
        });
    }

    @Test
    void centersTopExecutionLineWithoutScrollingBeforeDocumentStart() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCCodeEditor editor = editorWithLines(80, 120);
            MiniCViewportAdapter adapter = editor.viewportAdapter();
            adapter.scrollVertical(600);
            editor.setCurrentExecutionLine(1);

            adapter.centerActive();

            assertThat(editor.estimatedScrollYForTesting()).isEqualTo(0);
            assertThat(adapter.isActiveFullyVisible()).isTrue();
        });
    }

    @Test
    void sourceLoaderDelegatesToSourceEditorViewportAdapter() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCSourceLoaderView view = new MiniCSourceLoaderView(new MiniCWorkbenchViewModel());

            MiniCViewportAdapter adapter = view.viewportAdapter();

            assertThat(adapter.type()).isEqualTo(MiniCControlTargetType.TEXT);
            assertThat(adapter.canZoom()).isTrue();
            assertThat(adapter.canScrollVertical()).isTrue();
        });
    }

    private MiniCCodeEditor editorWithLines(int count, double height) {
        MiniCCodeEditor editor = new MiniCCodeEditor();
        editor.setText(IntStream.rangeClosed(1, count)
                .mapToObj(line -> "int value" + line + ";")
                .reduce((left, right) -> left + "\n" + right)
                .orElse(""));
        editor.resize(600, height);
        editor.applyCss();
        editor.layout();
        return editor;
    }

    private int offsetForLine(String source, int line) {
        int offset = 0;
        for (int current = 1; current < line; current++) {
            offset = source.indexOf('\n', offset) + 1;
            if (offset <= 0) {
                return source.length();
            }
        }
        return offset;
    }

    private void runOnFxThread(ThrowingRunnable action) {
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

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
