package craken.ui.debug;

import craken.SourceRange;
import craken.compiler.type.CrakenType;
import craken.debug.DebugVariable;
import craken.ui.component.UiStyles;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实窗口回归：在候选下拉浮层里点选/方向键选择时，不能同步清空 ComboBox 的 items 或选中，
 * 否则 JavaFX 的 ListViewBehavior 会在处理选中索引变更时抛 IndexOutOfBoundsException。
 */
@Tag("visualization-adapter")
final class DebugCaptureDropdownFxTest {
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException running) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test void clickingAndArrowingInTheCandidatePopupKeepsTheSelectionModelConsistent() throws Exception {
        var first = new DebugVariable("value", CrakenType.INT, DebugVariable.Kind.LOCAL, "first",
                new SourceRange(1, 4, 1, 12), false);
        var second = new DebugVariable("value", CrakenType.INT, DebugVariable.Kind.LOCAL, "second",
                new SourceRange(2, 4, 2, 12), false);
        var panel = onFx(() -> {
            var pane = new DebugPanel();
            pane.setCaptureSearch(term -> List.of(first, second));
            pane.setCaptureTypeText(variable -> "int");
            var stage = new Stage();
            var scene = new Scene(pane, 720, 520);
            UiStyles.install(scene);
            stage.setScene(scene);
            stage.show();
            return pane;
        });
        try {
            // JavaFX 事件分派会把监听器里的异常交给线程的未捕获处理器；必须显式收集才能让用例失败。
            var fxFailures = new CopyOnWriteArrayList<Throwable>();
            var previousHandler = onFx(() -> {
                var handler = Thread.currentThread().getUncaughtExceptionHandler();
                Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> fxFailures.add(failure));
                return handler;
            });
            var input = assertInstanceOf(TextField.class, panel.lookup("#debug-capture-input"));
            onFx(() -> { input.requestFocus(); input.setText("value"); return null; });
            awaitFx(() -> candidates(panel).getItems().size() == 2);
            onFx(() -> { candidates(panel).show(); return null; });
            awaitFx(() -> candidates(panel).isShowing());

            onFx(() -> {
                var list = popupListView();
                assertNotNull(list, "候选浮层里必须有候选列表");
                // 与鼠标按下单元格/方向键同一条路径：clearAndSelect 会触发 ComboBox 的选中提交。
                list.getSelectionModel().clearAndSelect(0);
                return null;
            });
            onFx(() -> {
                assertEquals(1, captureList(panel).getItems().size(), "点击候选后加入捕获列表");
                return null;
            });
            awaitFx(() -> candidates(panel).getItems().isEmpty());

            // 再用键盘走一遍：方向键移动高亮、回车加入第二个定义；任何残留的坏状态都会在这里抛异常。
            onFx(() -> { input.setText("value"); return null; });
            awaitFx(() -> candidates(panel).getItems().size() == 2);
            onFx(() -> {
                input.fireEvent(key(KeyCode.DOWN));
                input.fireEvent(key(KeyCode.DOWN));
                input.fireEvent(key(KeyCode.ENTER));
                return null;
            });
            onFx(() -> {
                assertEquals(2, captureList(panel).getItems().size(), "键盘选择第二个定义");
                Thread.currentThread().setUncaughtExceptionHandler(previousHandler);
                assertTrue(fxFailures.isEmpty(), () -> "FX 线程出现未捕获异常：" + fxFailures);
                return null;
            });
        } finally {
            onFx(() -> {
                if (panel.getScene() != null && panel.getScene().getWindow() instanceof Stage stage) stage.close();
                panel.close();
                return null;
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<DebugVariable> candidates(DebugPanel panel) {
        return (ComboBox<DebugVariable>) assertInstanceOf(ComboBox.class,
                panel.lookup("#debug-capture-candidates"));
    }

    @SuppressWarnings("unchecked")
    private static ListView<DebugPanel.CaptureItem> captureList(DebugPanel panel) {
        return (ListView<DebugPanel.CaptureItem>) assertInstanceOf(ListView.class,
                panel.lookup("#debug-capture-list"));
    }

    /** 候选浮层是独立窗口，需要从所有显示中的窗口里找它的列表。 */
    private static ListView<?> popupListView() {
        for (var window : Window.getWindows()) {
            if (!(window instanceof PopupWindow) || !window.isShowing() || window.getScene() == null) continue;
            for (var node : window.getScene().getRoot().lookupAll(".list-view")) {
                if (node instanceof ListView<?> list && list.isVisible()) return list;
            }
        }
        return null;
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static void awaitFx(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (onFx(condition)) return;
            Thread.sleep(30);
        }
        fail("条件未在预算内满足");
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
