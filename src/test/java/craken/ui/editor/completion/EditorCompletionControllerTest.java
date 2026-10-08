package craken.ui.editor.completion;

import craken.ui.component.UiStyles;
import craken.ui.component.editor.UiCodeEditor;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 补全粘合层的端到端验证：Swing 文档变更 -> 后台计算 -> 光标浮窗 -> 回车替换前缀。
 * 依赖真实窗口显示与焦点，仅在 UI 测试模式下运行。
 */
@Tag("visualization-ui")
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class EditorCompletionControllerTest {
    private static final CompletionCatalog CATALOG = new CompletionCatalog(
            List.of("stdio.h", "stdlib.h", "vector"),
            List.of("stdio.mh"));

    private Stage stage;
    private UiCodeEditor editor;
    private EditorCompletionController controller;

    @BeforeAll
    static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try {
            Platform.startup(start);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(start);
        }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(40)
    void typingAKeywordPrefixThenEnterInsertsTheKeyword() throws Exception {
        open("int main() {\n    \n}\n", 17);
        try {
            type("wh");
            awaitPopup();
            pressEnter();
            awaitText("int main() {\n    while\n}\n");
            assertEquals("int main() {\n    while\n}\n", editor.text());
        } finally {
            close();
        }
    }

    @Test
    @Timeout(40)
    void ctrlSpaceForcesTheListAndEnterInsertsTheFirstKeyword() throws Exception {
        open("int main() {\n    \n}\n", 17);
        try {
            pressComplete();
            awaitPopup();
            pressEnter();
            awaitText("int main() {\n    bool\n}\n");
            assertEquals("int main() {\n    bool\n}\n", editor.text());
        } finally {
            close();
        }
    }

    @Test
    @Timeout(40)
    void typingInsideAnIncludeCompletesTheHeaderName() throws Exception {
        open("#include <", 10);
        try {
            type("st");
            awaitPopup();
            pressEnter();
            awaitText("#include <stdio.h");
            assertEquals("#include <stdio.h", editor.text());
        } finally {
            close();
        }
    }

    @Test
    @Timeout(40)
    void aDeclaredVariableIsSuggestedAndInserted() throws Exception {
        open("int counter = 0;\nint main() {\n    \n}\n", 34);
        try {
            type("cou");
            awaitPopup();
            pressEnter();
            awaitText("int counter = 0;\nint main() {\n    counter\n}\n");
            assertEquals("int counter = 0;\nint main() {\n    counter\n}\n", editor.text());
        } finally {
            close();
        }
    }

    private void open(String source, int caret) throws Exception {
        var editorRef = new CompletableFuture<UiCodeEditor>();
        var stageRef = new CompletableFuture<Stage>();
        Platform.runLater(() -> {
            var created = new UiCodeEditor(source);
            var scene = new Scene(created, 1000, 640);
            UiStyles.install(scene);
            var window = new Stage(StageStyle.DECORATED);
            window.setScene(scene);
            window.show();
            editorRef.complete(created);
            stageRef.complete(window);
        });
        editor = editorRef.get(10, TimeUnit.SECONDS);
        stage = stageRef.get(10, TimeUnit.SECONDS);
        controller = new EditorCompletionController(editor, CATALOG);
        editor.moveTo(caret);
        onFx(() -> { editor.requestEditorFocus(); return null; });
        for (int attempt = 0; attempt < 40 && !onFx(() -> swingNode().isFocused()); attempt++) {
            Thread.sleep(25);
        }
        assertTrue(onFx(() -> swingNode().isFocused()), "编辑器要能拿到焦点");
        Thread.sleep(200);
    }

    private void close() throws Exception {
        onFx(() -> {
            controller.close();
            editor.dispose();
            stage.close();
            return null;
        });
    }

    private void type(String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = (JTextArea) textArea();
            int caret = area.getCaretPosition();
            area.replaceRange(text, caret, caret);
        });
    }

    private void pressEnter() throws Exception {
        onFx(() -> {
            Event.fireEvent(swingNode(), new KeyEvent(
                    KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false));
            return null;
        });
    }

    private void pressComplete() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Action action = textArea().getActionMap().get("craken-editor-complete");
            assertNotNull(action, "Ctrl+Space 补全动作必须已安装");
            action.actionPerformed(new ActionEvent(
                    textArea(), ActionEvent.ACTION_PERFORMED, "craken-editor-complete"));
        });
    }

    private void awaitPopup() throws Exception {
        await(this::popupShowing);
    }

    private void awaitText(String expected) throws Exception {
        await(() -> expected.equals(editor.text()));
    }

    private void await(Condition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (condition.test()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("condition not reached in time");
    }

    private boolean popupShowing() throws Exception {
        return onFx(() -> {
            try {
                Object bridge = field(editor, "caretList");
                Object popup = field(bridge, "popup");
                Method isShowing = popup.getClass().getDeclaredMethod("isShowing");
                isShowing.setAccessible(true);
                return (boolean) isShowing.invoke(popup);
            } catch (Exception failure) {
                throw new IllegalStateException("cannot read caret list visibility", failure);
            }
        });
    }

    private JTextComponent textArea() {
        var node = swingNode();
        return (JTextComponent) ((javax.swing.JScrollPane) node.getContent())
                .getViewport().getView();
    }

    private SwingNode swingNode() {
        try {
            return (SwingNode) field(editor, "swingNode");
        } catch (Exception failure) {
            throw new IllegalStateException("editor has no swing node", failure);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface Condition {
        boolean test() throws Exception;
    }
}
