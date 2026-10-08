package craken.ui.component.editor;

import craken.ui.component.UiStyles;
import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拆分编辑器会在同一场景里给窗格换父节点：原来持有焦点的编辑器的 SwingNode 承载窗可能失效，
 * 表现是"看得见但点不动、打不了字"。拆分后第一个编辑器必须保持可交互。
 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class UiEditorSplitInputTest {
    @TempDir Path directory;

    @BeforeAll
    static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException alreadyStarted) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(40)
    void splittingKeepsTheFocusedEditorInteractive() throws Exception {
        Path first = Files.writeString(directory.resolve("first.mc"), "int main(void) { return 1; }\n");
        Path second = Files.writeString(directory.resolve("second.mc"), "int helper(void) { return 2; }\n");
        var areaRef = new CompletableFuture<EditorArea>();
        var stageRef = new CompletableFuture<Stage>();
        Platform.runLater(() -> {
            var area = new EditorArea();
            var scene = new Scene(area, 1000, 640);
            UiStyles.install(scene);
            var stage = new Stage(StageStyle.DECORATED);
            stage.setScene(scene);
            stage.show();
            stageRef.complete(stage);
            areaRef.complete(area);
        });
        var area = areaRef.get(10, TimeUnit.SECONDS);
        var stage = stageRef.get(10, TimeUnit.SECONDS);
        try {
            EditorFile a = onFx(() -> area.openFile(first));
            SwingNode node = swingNode(a);
            onFx(() -> { a.editor().requestEditorFocus(); return null; });
            for (int attempt = 0; attempt < 40 && !onFx(node::isFocused); attempt++) Thread.sleep(25);
            assertTrue(onFx(node::isFocused), "拆分前第一个编辑器要能拿到焦点");

            onFx(() -> area.openFileBeside(second, a, Orientation.HORIZONTAL));
            Thread.sleep(400);
            assertTrue(displayable(a), "拆分后第一个编辑器的承载窗必须仍然可显示");
            assertTrue(type(a, 'r'), "拆分后第一个编辑器必须仍能接收输入（承载窗被换父节点破坏后要自愈）");
            assertTrue(onFx(() -> a.editor().text().contains("r")), "输入要真正进入第一个编辑器的文档");
        } finally {
            onFx(() -> { stage.close(); return null; });
        }
    }

    private static boolean displayable(EditorFile file) throws Exception {
        var node = swingNode(file);
        var state = new CompletableFuture<Boolean>();
        SwingUtilities.invokeLater(() -> {
            JTextComponent text = (JTextComponent) ((JScrollPane) node.getContent()).getViewport().getView();
            java.awt.Window window = SwingUtilities.getWindowAncestor(text);
            state.complete(window != null && window.isDisplayable());
        });
        return state.get(5, TimeUnit.SECONDS);
    }

    private static boolean type(EditorFile file, char character) throws Exception {
        var node = swingNode(file);
        onFx(() -> {
            Event.fireEvent(node, new KeyEvent(KeyEvent.KEY_TYPED, String.valueOf(character),
                    String.valueOf(character), KeyCode.UNDEFINED, false, false, false, false));
            return null;
        });
        Thread.sleep(120);
        var applied = new CompletableFuture<Boolean>();
        SwingUtilities.invokeLater(() -> {
            JTextComponent text = (JTextComponent) ((JScrollPane) node.getContent()).getViewport().getView();
            applied.complete(text.getText().indexOf(character) >= 0);
        });
        return applied.get(5, TimeUnit.SECONDS);
    }

    private static SwingNode swingNode(EditorFile file) throws Exception {
        var editor = onFx(file::editor);
        var field = editor.getClass().getDeclaredField("swingNode");
        field.setAccessible(true);
        return (SwingNode) field.get(editor);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(30, TimeUnit.SECONDS);
    }
}
