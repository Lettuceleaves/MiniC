package minic.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
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

    @Test
    void refreshesEditorTextWhenSourceIsLoadedAfterViewCreation() {
        startJavafx();
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        MiniCSourceLoaderView compileView = new MiniCSourceLoaderView(viewModel);

        viewModel.loadSource("loaded.mc", "int main() { return 7; }");

        assertThat(editor(compileView).getText()).isEqualTo("int main() { return 7; }");
    }

    @Test
    void keepsEditorTokenStylesWhenStartingSessionClearsRealtimeAnalysis() throws Exception {
        startJavafx();
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        MiniCSourceLoaderView compileView = new MiniCSourceLoaderView(viewModel);
        MiniCCodeEditor editor = editor(compileView);
        editor.setText("int main() { return 7; }");
        editor.render(MiniCRealtimeAnalyzer.analyzeNow("loaded.mc", editor.getText(), 1));
        assertThat(styleAt(editor, 0)).contains("token-keyword");

        compileView.loadCurrentSource();

        assertThat(styleAt(editor, 0)).contains("token-keyword");
    }

    private MiniCCodeEditor editor(MiniCSourceLoaderView view) {
        return children(view).stream()
                .filter(MiniCCodeEditor.class::isInstance)
                .map(MiniCCodeEditor.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private java.util.Collection<String> styleAt(MiniCCodeEditor editor, int position) throws Exception {
        Field input = MiniCCodeEditor.class.getDeclaredField("input");
        input.setAccessible(true);
        Object area = input.get(editor);
        return (java.util.Collection<String>) area.getClass().getMethod("getStyleOfChar", int.class)
                .invoke(area, position);
    }

    private List<Node> children(Parent parent) {
        return parent.getChildrenUnmodifiable().stream().toList();
    }
}
