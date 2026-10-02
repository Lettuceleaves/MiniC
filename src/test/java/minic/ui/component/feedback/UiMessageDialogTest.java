package minic.ui.component.feedback;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import minic.ui.component.UiStyles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static minic.ui.component.feedback.UiMessageDialog.Result.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实弹窗仅使用独立测试窗口，不操作用户正在编辑的文件或窗口。 */
@EnabledIfSystemProperty(named = "minic.ui.test", matches = "true")
final class UiMessageDialogTest {
    private static final String MESSAGE = "是否保存对“all_syntax.mc”的修改？";
    private static final String DETAILS = "关闭后，未保存的修改将丢失。\n\n"
            + "C:\\Users\\Administrator\\Desktop\\tmp\\all_syntax.mc";

    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> { Platform.setImplicitExit(false); started.complete(null); };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void actionButtonsReturnSaveDiscardAndCancelWithoutChangingTheirMeaning() throws Exception {
        onFx(() -> {
            for (UiMessageDialog.Result choice : List.of(SAVE, DISCARD, CANCEL)) {
                try (var ui = new Fixture(MESSAGE, DETAILS)) {
                    ui.show();
                    Button button = actionButton(ui.dialog, choice);
                    assertNotNull(button);
                    button.fire();
                    assertFalse(ui.dialog.isShowing());
                    assertSame(choice, ui.dialog.getResult());
                }
            }
            return null;
        });
    }

    @Test
    void titleCloseAndEscapeBothReturnCancel() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(MESSAGE, DETAILS)) {
                ui.show();
                Button close = (Button) ui.dialog.getDialogPane().lookup(".ui-modal-dialog-close");
                assertNotNull(close);
                close.fire();
                assertFalse(ui.dialog.isShowing());
                assertSame(CANCEL, ui.dialog.getResult(), "the title close button must never discard edits");
            }
            try (var ui = new Fixture(MESSAGE, DETAILS)) {
                ui.show();
                Event.fireEvent(ui.dialog.getDialogPane(), new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ESCAPE, false, false, false, false));
                assertFalse(ui.dialog.isShowing(), "Escape must dismiss the modal dialog");
                assertSame(CANCEL, ui.dialog.getResult(), "Escape must preserve pending edits");
            }
            return null;
        });
    }

    @Test
    void retainsOwnerModalityDefaultActionAndKeyboardAccessibleButtons() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(MESSAGE, DETAILS)) {
                ui.show();
                assertSame(ui.owner, ui.dialog.getOwner());
                assertEquals(Modality.WINDOW_MODAL, ui.dialog.getModality());
                assertEquals(StageStyle.UNDECORATED,
                        ((Stage) ui.dialog.getDialogPane().getScene().getWindow()).getStyle());
                assertTrue(actionButton(ui.dialog, SAVE).isDefaultButton());
                assertFalse(actionButton(ui.dialog, DISCARD).isDefaultButton());
                assertTrue(actionButton(ui.dialog, CANCEL).isCancelButton());
                assertEquals(List.of("保存", "不保存", "取消"), ui.dialog.actions().stream()
                        .map(ui.dialog::actionButton).map(Button::getText).toList());
                for (UiMessageDialog.Result choice : List.of(SAVE, DISCARD, CANCEL)) {
                    assertTrue(actionButton(ui.dialog, choice).isFocusTraversable());
                }
            }
            return null;
        });
    }

    @Test
    void longFileNameAndPathWrapInsideTheDialog() throws Exception {
        String name = "用于验证长文件名保存提示不会溢出的源代码文件_".repeat(4) + ".mc";
        String details = "C:\\Users\\Administrator\\Desktop\\" + "很长的项目目录\\".repeat(10) + name;
        onFx(() -> {
            try (var ui = new Fixture("是否保存对“" + name + "”的修改？", details)) {
                ui.show();
                Label message = ui.label(".ui-message-dialog-message");
                Label path = ui.label(".ui-message-dialog-details");
                assertTrue(message.isWrapText());
                assertTrue(path.isWrapText());
                assertEquals(details, path.getText());
                assertTrue(path.getHeight() > path.getFont().getSize() * 2,
                        "the full path must wrap into several visible lines");
                Bounds pane = ui.dialog.getDialogPane().localToScene(ui.dialog.getDialogPane().getBoundsInLocal());
                for (Label label : List.of(message, path)) {
                    Bounds bounds = label.localToScene(label.getBoundsInLocal());
                    assertTrue(bounds.getMinX() >= pane.getMinX());
                    assertTrue(bounds.getMaxX() <= pane.getMaxX() + 1);
                    assertTrue(bounds.getMaxY() <= pane.getMaxY() + 1);
                }
                assertTrue(ui.dialog.getWidth() <= ui.owner.getWidth(),
                        "long paths must not expand the dialog beyond its owner");
            }
            return null;
        });
    }

    @Test
    void confirmationHasReadableLightThemeAndCreatesVisualReviewSnapshot() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(MESSAGE, DETAILS)) {
                ui.show();
                var pane = ui.dialog.getDialogPane();
                Color background = (Color) pane.getBackground().getFills().getFirst().getFill();
                assertTrue(background.getBrightness() > 0.9, "the dialog surface must use the requested light theme");
                for (String selector : List.of(".ui-modal-dialog-title", ".ui-message-dialog-message",
                        ".ui-message-dialog-details")) {
                    Color foreground = (Color) ui.label(selector).getTextFill();
                    assertTrue(contrast(foreground, background) >= 4.5,
                            selector + " must remain readable against the light background");
                }
                assertNull(pane.getGraphic(), "do not reintroduce the stock oversized alert icon");
                Path snapshot = Path.of("build", "message-dialog-check", "save-confirmation.png");
                Files.createDirectories(snapshot.getParent());
                assertTrue(ImageIO.write(SwingFXUtils.fromFXImage(pane.snapshot(null, null), null),
                        "png", snapshot.toFile()));
            }
            return null;
        });
    }

    @Test
    void noticeAcknowledgesItsOnlyActionThroughButtonTitleCloseAndEscape() throws Exception {
        onFx(() -> {
            for (int dismissal = 0; dismissal < 3; dismissal++) {
                UiMessageDialog dialog = UiMessageDialog.notice(null, "提示", "操作完成", "");
                try {
                    show(dialog);
                    assertEquals(1, dialog.actions().size());
                    Button acknowledge = actionButton(dialog, CONFIRMED);
                    assertEquals("确定", acknowledge.getText());
                    if (dismissal == 0) acknowledge.fire();
                    else if (dismissal == 1) closeButton(dialog).fire();
                    else pressEscape(dialog);
                    assertFalse(dialog.isShowing());
                    assertSame(CONFIRMED, dialog.getResult());
                } finally {
                    dialog.close();
                }
            }
            return null;
        });
    }

    @Test
    void confirmationDistinguishesAcceptanceFromEveryCancellationPath() throws Exception {
        onFx(() -> {
            for (int dismissal = 0; dismissal < 4; dismissal++) {
                UiMessageDialog dialog = UiMessageDialog.confirm(null, "确认", "执行此操作？", null);
                try {
                    show(dialog);
                    assertEquals(List.of("确定", "取消"), dialog.actions().stream()
                            .map(dialog::actionButton).map(Button::getText).toList());
                    if (dismissal == 0) actionButton(dialog, CONFIRMED).fire();
                    else if (dismissal == 1) actionButton(dialog, CANCEL).fire();
                    else if (dismissal == 2) closeButton(dialog).fire();
                    else pressEscape(dialog);
                    assertFalse(dialog.isShowing());
                    assertSame(dismissal == 0 ? CONFIRMED : CANCEL, dialog.getResult());
                } finally {
                    dialog.close();
                }
            }
            return null;
        });
    }

    private static Button actionButton(UiMessageDialog dialog, UiMessageDialog.Result result) {
        return dialog.actions().stream().filter(action -> action.result() == result)
                .findFirst().map(dialog::actionButton).orElseThrow();
    }

    private static Button closeButton(UiMessageDialog dialog) {
        Button close = (Button) dialog.getDialogPane().lookup(".ui-modal-dialog-close");
        assertNotNull(close);
        return close;
    }

    private static void pressEscape(UiMessageDialog dialog) {
        Event.fireEvent(dialog.getDialogPane(), new KeyEvent(KeyEvent.KEY_PRESSED,
                "", "", KeyCode.ESCAPE, false, false, false, false));
    }

    private static void show(UiMessageDialog dialog) {
        dialog.show();
        dialog.getDialogPane().applyCss();
        dialog.getDialogPane().layout();
    }

    private static double contrast(Color first, Color second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen())
                + 0.0722 * linear(color.getBlue());
    }

    private static double linear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    private static final class Fixture implements AutoCloseable {
        final Stage owner = new Stage(StageStyle.UNDECORATED);
        final UiMessageDialog dialog;

        Fixture(String message, String details) {
            Scene scene = new Scene(new StackPane(), 900, 650);
            UiStyles.install(scene);
            owner.setScene(scene);
            owner.setTitle("MiniC dialog test");
            owner.show();
            dialog = UiMessageDialog.saveChanges(owner, "关闭文件", message, details);
        }

        void show() {
            UiMessageDialogTest.show(dialog);
        }

        Label label(String selector) {
            Label label = (Label) dialog.getDialogPane().lookup(selector);
            assertNotNull(label, selector);
            return label;
        }

        @Override public void close() {
            dialog.close();
            owner.close();
        }
    }
}
