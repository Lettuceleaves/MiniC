package minic.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCCodeEditorBreakpointGutterTest {
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
    void togglesBreakpointLinesAndBuildsGutterGraphic() throws Exception {
        startJavafx();
        MiniCCodeEditor editor = new MiniCCodeEditor();
        editor.setText("""
                int main() {
                    return 0;
                }
                """);

        editor.setBreakpoint(2, true);

        assertThat(editor.breakpointLines()).containsExactly(2);

        HBox graphic = paragraphGraphic(editor, 1);
        assertThat(graphic.getStyleClass()).contains("editor-gutter");
        Label breakpoint = (Label) graphic.getChildren().get(1);
        assertThat(breakpoint.getStyleClass()).contains("breakpoint-gutter", "active");
        assertThat(breakpoint.getText()).isEqualTo("●");

        breakpoint.fireEvent(mouseClick());

        assertThat(editor.breakpointLines()).isEmpty();
    }

    @Test
    void marksCurrentExecutionLineInGutter() throws Exception {
        startJavafx();
        MiniCCodeEditor editor = new MiniCCodeEditor();

        editor.setCurrentExecutionLine(3);

        HBox active = paragraphGraphic(editor, 2);
        HBox inactive = paragraphGraphic(editor, 1);

        assertThat(active.getStyleClass()).contains("current-execution");
        assertThat(((Label) active.getChildren().getFirst()).getText()).isEqualTo("▶");
        assertThat(inactive.getStyleClass()).doesNotContain("current-execution");
    }

    @Test
    void replacesBreakpointLinesWithoutFiringChangeAction() {
        startJavafx();
        MiniCCodeEditor editor = new MiniCCodeEditor();
        java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
        editor.setBreakpointChangeAction(changes::incrementAndGet);

        editor.replaceBreakpoints(java.util.List.of(4, 2, 2, 0));

        assertThat(editor.breakpointLines()).containsExactly(2, 4);
        assertThat(changes).hasValue(0);
    }

    private HBox paragraphGraphic(MiniCCodeEditor editor, int paragraphIndex) throws Exception {
        Method method = MiniCCodeEditor.class.getDeclaredMethod("paragraphGraphic", int.class);
        method.setAccessible(true);
        Node node = (Node) method.invoke(editor, paragraphIndex);
        return (HBox) node;
    }

    private MouseEvent mouseClick() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null
        );
    }
}
