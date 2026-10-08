package craken.ui.component.editor;

import craken.ui.component.UiStyles;
import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
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
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 真实鼠标点击落在 Swing 组件上（原生承载窗），不一定经过 FX 事件链；
 * 在分屏里点进某个编辑器后，活动文件必须切换成它，否则 Pipeline/调试会拿到另一个文件。
 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class UiEditorActivationTest {
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
    void clickingInsideASplitEditorActivatesThatFile() throws Exception {
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
            EditorFile b = onFx(() -> area.openFileBeside(second, a, Orientation.HORIZONTAL));
            assertEqualsOnFx(b, area, "打开第二个文件后活动文件应是第二个");
            Thread.sleep(300);
            swingClick(a);
            for (int attempt = 0; attempt < 40; attempt++) {
                if (onFx(area::activeFile) == a) break;
                Thread.sleep(25);
            }
            assertSame(a, onFx(area::activeFile), "点进第一个编辑器后活动文件必须切回它");
            swingClick(b);
            for (int attempt = 0; attempt < 40; attempt++) {
                if (onFx(area::activeFile) == b) break;
                Thread.sleep(25);
            }
            assertSame(b, onFx(area::activeFile), "点进第二个编辑器后活动文件回到它");
        } finally {
            onFx(() -> { stage.close(); return null; });
        }
    }

    private static void assertEqualsOnFx(EditorFile expected, EditorArea area, String message) throws Exception {
        assertSame(expected, onFx(area::activeFile), message);
    }

    private static void swingClick(EditorFile file) throws Exception {
        var node = swingNode(file);
        SwingUtilities.invokeAndWait(() -> {
            JTextComponent text = (JTextComponent) ((JScrollPane) node.getContent()).getViewport().getView();
            text.dispatchEvent(new MouseEvent(text, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    0, 8, 8, 1, false, MouseEvent.BUTTON1));
        });
        Thread.sleep(120);
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
