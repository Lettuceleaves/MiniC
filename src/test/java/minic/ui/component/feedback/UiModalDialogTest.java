package minic.ui.component.feedback;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import minic.ui.component.UiStyles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static minic.ui.component.feedback.UiModalDialog.Action;
import static org.junit.jupiter.api.Assertions.*;

/** 通过独立窗口验证通用容器的内容、结果和窗口交互协议。 */
@EnabledIfSystemProperty(named = "minic.ui.test", matches = "true")
final class UiModalDialogTest {
    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> { Platform.setImplicitExit(false); started.complete(null); };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void hostsInteractiveContentAndReturnsTheCallersTypedResult() throws Exception {
        onFx(() -> {
            record Selection(String operation, int code) {}
            Selection accepted = new Selection("apply", 17);
            Selection cancelled = new Selection("cancel", 0);
            for (Selection expected : List.of(accepted, cancelled)) {
                TextField name = new TextField("原名称");
                CheckBox enabled = new CheckBox("启用");
                VBox form = new VBox(8, new Label("项目设置"), name, enabled);
                List<String> originalClasses = List.copyOf(form.getStyleClass());
                Action<Selection> apply = Action.primary("应用", accepted);
                Action<Selection> cancel = Action.cancel("返回", cancelled);
                try (var ui = new Fixture<>(form, List.of(apply, cancel))) {
                    ui.show();
                    assertSame(ui.dialog.getDialogPane().getScene(), form.getScene());
                    assertEquals(originalClasses, form.getStyleClass(),
                            "the dialog must not alter the caller's content styling");
                    name.setText("新名称");
                    enabled.fire();
                    assertEquals("新名称", name.getText());
                    assertTrue(enabled.isSelected());
                    ui.dialog.actionButton(expected == accepted ? apply : cancel).fire();
                    assertFalse(ui.dialog.isShowing());
                    assertSame(expected, ui.dialog.getResult());
                }
            }
            return null;
        });
    }

    @Test
    void enterInvokesPrimaryActionAndEscapeInvokesCancelAction() throws Exception {
        onFx(() -> {
            for (KeyCode key : List.of(KeyCode.ENTER, KeyCode.ESCAPE)) {
                Action<Integer> primary = Action.primary("应用", 42);
                Action<Integer> other = Action.secondary("稍后", 7);
                Action<Integer> cancel = Action.cancel("返回", -1);
                try (var ui = new Fixture<>(new Label("可复用内容"), List.of(primary, other, cancel))) {
                    ui.show();
                    assertTrue(ui.dialog.actionButton(primary).isDefaultButton());
                    assertFalse(ui.dialog.actionButton(other).isDefaultButton());
                    assertTrue(ui.dialog.actionButton(cancel).isCancelButton());
                    Event.fireEvent(ui.dialog.getDialogPane(), new KeyEvent(KeyEvent.KEY_PRESSED,
                            "", "", key, false, false, false, false));
                    assertFalse(ui.dialog.isShowing(), key + " must dismiss the dialog");
                    assertEquals(key == KeyCode.ENTER ? 42 : -1, ui.dialog.getResult());
                }
            }
            return null;
        });
    }

    @Test
    void titleUpdatesReachTheVisibleHeaderAndCloseReturnsCancel() throws Exception {
        onFx(() -> {
            Action<String> apply = Action.primary("应用", "apply");
            Action<String> cancel = Action.cancel("返回", "cancel");
            try (var ui = new Fixture<>(new Label("内容"), List.of(apply, cancel))) {
                ui.show();
                Label title = (Label) ui.dialog.getDialogPane().lookup(".ui-modal-dialog-title");
                assertNotNull(title);
                ui.dialog.setTitle("已更新标题");
                assertEquals("已更新标题", title.getText());
                assertTrue(ui.dialog.getDialogPane().getStyleClass().contains("ui-modal-dialog"));
                Button close = (Button) ui.dialog.getDialogPane().lookup(".ui-modal-dialog-close");
                assertNotNull(close);
                close.fire();
                assertFalse(ui.dialog.isShowing());
                assertEquals("cancel", ui.dialog.getResult());
            }
            return null;
        });
    }

    @Test
    void preservesWindowModalityAndAllowsDraggingByTheTitle() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture<>(new Label("内容"), List.of(Action.primary("确定", true)))) {
                ui.show();
                assertSame(ui.owner, ui.dialog.getOwner());
                assertEquals(Modality.WINDOW_MODAL, ui.dialog.getModality());
                Stage window = (Stage) ui.dialog.getDialogPane().getScene().getWindow();
                assertEquals(StageStyle.UNDECORATED, window.getStyle());
                Label title = (Label) ui.dialog.getDialogPane().lookup(".ui-modal-dialog-title");
                double beforeX = window.getX(), beforeY = window.getY();
                Event.fireEvent(title, mouse(MouseEvent.MOUSE_PRESSED, beforeX + 30, beforeY + 15));
                Event.fireEvent(title, mouse(MouseEvent.MOUSE_DRAGGED, beforeX + 70, beforeY + 45));
                assertEquals(beforeX + 40, window.getX(), 1);
                assertEquals(beforeY + 30, window.getY(), 1);
            }
            UiModalDialog<Boolean> standalone = new UiModalDialog<>(null, "独立弹窗", new Label("内容"),
                    List.of(Action.primary("确定", true)));
            assertEquals(Modality.APPLICATION_MODAL, standalone.getModality());
            standalone.close();
            return null;
        });
    }

    @Test
    void freezesTheActionListSoCallerMutationCannotChangeDisplayedChoices() throws Exception {
        onFx(() -> {
            Action<String> apply = Action.primary("应用", "apply");
            Action<String> cancel = Action.cancel("返回", "cancel");
            var source = new ArrayList<>(List.of(apply, cancel));
            try (var ui = new Fixture<>(new Label("内容"), source)) {
                source.clear();
                ui.show();
                assertEquals(List.of(apply, cancel), ui.dialog.actions());
                assertThrows(UnsupportedOperationException.class, () -> ui.dialog.actions().clear());
                ui.dialog.actionButton(cancel).fire();
                assertEquals("cancel", ui.dialog.getResult());
            }
            return null;
        });
    }

    @Test
    void rejectsAmbiguousActionsBeforeOpeningAWindow() throws Exception {
        onFx(() -> {
            assertThrows(IllegalArgumentException.class, () -> invalid(List.of()));
            assertThrows(IllegalArgumentException.class, () -> invalid(List.of(
                    Action.primary("应用", "apply"), Action.secondary("稍后", "later"))));
            assertThrows(IllegalArgumentException.class, () -> invalid(List.of(
                    Action.primary("应用", "apply"), Action.primary("确定", "ok"),
                    Action.cancel("返回", "cancel"))));
            assertThrows(IllegalArgumentException.class, () -> invalid(List.of(
                    Action.cancel("取消", "cancel"), Action.cancel("返回", "back"))));
            assertThrows(NullPointerException.class, () -> Action.primary("确定", null));
            assertThrows(NullPointerException.class, () -> Action.secondary("稍后", null));
            assertThrows(NullPointerException.class, () -> Action.cancel("取消", null));
            return null;
        });
    }

    private static void invalid(List<Action<String>> actions) {
        new UiModalDialog<>(null, "无效选项", new Label("内容"), actions);
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double screenX, double screenY) {
        return new MouseEvent(type, 5, 5, screenX, screenY, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, null);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    private static final class Fixture<R> implements AutoCloseable {
        final Stage owner = new Stage(StageStyle.UNDECORATED);
        final UiModalDialog<R> dialog;

        Fixture(Node content, List<Action<R>> actions) {
            Scene scene = new Scene(new StackPane(), 900, 650);
            UiStyles.install(scene);
            owner.setScene(scene);
            owner.setTitle("MiniC generic dialog test");
            owner.show();
            dialog = new UiModalDialog<>(owner, "通用弹窗", content, actions);
        }

        void show() {
            dialog.show();
            dialog.getDialogPane().applyCss();
            dialog.getDialogPane().layout();
        }

        @Override public void close() {
            dialog.close();
            owner.close();
        }
    }
}
