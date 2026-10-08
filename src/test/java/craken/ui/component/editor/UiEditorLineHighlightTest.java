package craken.ui.component.editor;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 调试器每步都会同步当前行高亮；同一行重复设置必须是空操作，
 * 否则 SwingNode 里会堆积局部重绘，编辑器出现整块未刷新（点击才恢复）。
 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class UiEditorLineHighlightTest {
    @BeforeAll
    static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException alreadyStarted) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test
    void repeatedHighlightOfTheSameLineKeepsTheSameTag() throws Exception {
        var editor = new CompletableFuture<UiCodeEditor>();
        Platform.runLater(() -> editor.complete(new UiCodeEditor()));
        var instance = editor.get(10, TimeUnit.SECONDS);
        instance.setSource("line one\nline two\nline three\nline four\n");

        instance.highlightLine(3);
        Object first = tag(instance);
        instance.highlightLine(3);
        assertSame(first, tag(instance), "重复高亮同一行必须是无操作，否则 SwingNode 会堆积局部重绘");

        instance.highlightLine(4);
        assertNotSame(first, tag(instance), "换行高亮仍要换新标签");
    }

    private static Object tag(UiCodeEditor editor) throws Exception {
        var tag = new CompletableFuture<Object>();
        editor.onTextArea(area -> tag.complete(area.getHighlighter().getHighlights()[0]));
        return tag.get(10, TimeUnit.SECONDS);
    }
}

