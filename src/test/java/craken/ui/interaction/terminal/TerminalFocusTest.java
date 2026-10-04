package craken.ui.interaction.terminal;

import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.scene.AccessibleAction;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import craken.ui.component.UiStyles;
import craken.ui.component.editor.UiCodeEditor;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.InteractionItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 同一 Scene 内两个真实 SwingNode 的焦点回归；不生成系统鼠标或键盘输入。 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
@EnabledOnOs(OS.WINDOWS)
@Timeout(40)
final class TerminalFocusTest {
    @TempDir Path temporary;

    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> { Platform.setImplicitExit(false); started.complete(null); };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void mountingAndStartingTerminalPreservesAnAlreadyFocusedEditor() throws Exception {
        try (Fixture ui = Fixture.create(temporary)) {
            onFx(() -> { ui.editor.requestEditorFocus(); return null; });
            try (EdtGate ignored = EdtGate.block()) {
                onFx(() -> {
                    ui.mount();
                    assertSame(ui.editorSurface, ui.scene.getFocusOwner(),
                            "mounting a background terminal must not change the current editor selection");
                    return null;
                });
            }
            ui.awaitReady();
            ui.assertFocusStays(ui.editorSurface);
        }
    }

    @Test
    void queuedTerminalActivationCannotOverrideANewerEditorSelection() throws Exception {
        try (Fixture ui = Fixture.createMounted(temporary)) {
            ui.awaitReady();
            try (EdtGate ignored = EdtGate.block()) {
                onFx(() -> {
                    ui.terminal.activate();
                    ui.editor.requestEditorFocus();
                    return null;
                });
            }
            ui.assertFocusStays(ui.editorSurface);
        }
    }

    @Test
    void rapidSwitchingHonorsTheLastSelectionInBothDirections() throws Exception {
        try (Fixture ui = Fixture.createMounted(temporary)) {
            ui.awaitReady();
            for (boolean endInTerminal : List.of(true, false, true, false)) {
                try (EdtGate ignored = EdtGate.block()) {
                    onFx(() -> {
                        for (int turn = 0; turn < 8; turn++) {
                            ui.editor.requestEditorFocus();
                            ui.terminal.activate();
                        }
                        if (!endInTerminal) ui.editor.requestEditorFocus();
                        return null;
                    });
                }
                ui.assertFocusStays(endInTerminal ? ui.terminalSurface : ui.editorSurface);
            }
        }
    }

    @Test
    void completingARestartCannotTakeFocusBackFromTheEditor() throws Exception {
        try (Fixture ui = Fixture.createMounted(temporary)) {
            ui.awaitReady();
            PowerShellSession original = ui.terminal.session();
            ui.sessions.add(original);
            try (EdtGate ignored = EdtGate.block()) {
                onFx(() -> {
                    ui.terminal.activate();
                    ui.terminal.restart();
                    ui.editor.requestEditorFocus();
                    return null;
                });
            }
            assertNotSame(original, ui.terminal.session());
            ui.awaitReady();
            ui.assertFocusStays(ui.editorSurface);
        }
    }

    @Test
    void reattachingASelectedPanelHonorsTheSubsequentEditorSelection() throws Exception {
        try (Fixture ui = Fixture.createMounted(temporary)) {
            ui.awaitReady();
            var terminalItem = onFx(() -> ui.area.activeItem());
            var originalWidget = ui.terminal.widget();
            onFx(() -> {
                ui.area.addItem(new InteractionItem<>("其他面板", new TextField("其他内容")));
                ui.editor.requestEditorFocus();
                return null;
            });
            ui.assertFocusStays(ui.editorSurface);
            try (EdtGate ignored = EdtGate.block()) {
                onFx(() -> {
                    ui.area.select(terminalItem);
                    ui.editor.requestEditorFocus();
                    return null;
                });
            }
            assertSame(originalWidget, ui.terminal.widget());
            ui.assertFocusStays(ui.editorSurface);
        }
    }

    @Test
    void clickingEitherSurfaceTransfersBothFxAndSwingFocus() throws Exception {
        try (Fixture ui = Fixture.createMounted(temporary)) {
            ui.awaitReady();
            for (SwingNode target : List.of(ui.terminalSurface, ui.editorSurface, ui.terminalSurface)) {
                onFx(() -> { ui.click(target); return null; });
                ui.assertFocusStays(target);
            }
        }
    }

    @Test
    void tabTraversalFromFxTextFieldCanEnterTheEditorSurface() throws Exception {
        try (Fixture ui = Fixture.createMounted(temporary)) {
            ui.awaitReady();
            TextField beforeEditor = onFx(() -> {
                TextField field = ui.beforeEditor;
                field.setManaged(true);
                field.setVisible(true);
                ui.root.applyCss();
                ui.root.layout();
                field.requestFocus();
                assertSame(field, ui.scene.getFocusOwner());
                return field;
            });
            onFx(() -> {
                Event.fireEvent(beforeEditor, new KeyEvent(KeyEvent.KEY_PRESSED,
                        KeyEvent.CHAR_UNDEFINED, "", KeyCode.TAB, false, false, false, false));
                return null;
            });
            ui.assertFocusStays(ui.editorSurface);
        }
    }

    @Test
    void accessibleFocusRequestsCanEnterEitherSurface() throws Exception {
        try (Fixture ui = Fixture.createMounted(temporary)) {
            ui.awaitReady();
            for (SwingNode target : List.of(ui.terminalSurface, ui.editorSurface)) {
                onFx(() -> {
                    target.executeAccessibleAction(AccessibleAction.REQUEST_FOCUS);
                    return null;
                });
                ui.assertFocusStays(target);
            }
        }
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(15, TimeUnit.SECONDS);
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        SwingUtilities.invokeLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(15, TimeUnit.SECONDS);
    }

    /** 只滞留异步焦点和 widget 构造；此期间不得执行编辑器的同步 Swing API。 */
    private static final class EdtGate implements AutoCloseable {
        private final CountDownLatch released = new CountDownLatch(1);

        static EdtGate block() throws Exception {
            EdtGate gate = new EdtGate();
            var entered = new CompletableFuture<Void>();
            SwingUtilities.invokeLater(() -> {
                entered.complete(null);
                try { gate.released.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            });
            entered.get(10, TimeUnit.SECONDS);
            return gate;
        }

        @Override public void close() { released.countDown(); }
    }

    private static final class Fixture implements AutoCloseable {
        final UiCodeEditor editor = new UiCodeEditor("int main() { return 0; }\n");
        final SwingNode editorSurface = (SwingNode) editor.getChildrenUnmodifiable().getFirst();
        final TextField beforeEditor = new TextField("Tab into the editor");
        final VBox editorColumn = new VBox(beforeEditor, editor);
        final BorderPane root = new BorderPane(editorColumn);
        final Scene scene = new Scene(root, 1000, 700);
        final Stage stage = new Stage(StageStyle.UNDECORATED);
        final InteractionArea area;
        final TerminalPanel terminal;
        final SwingNode terminalSurface;
        final List<PowerShellSession> sessions = new ArrayList<>();
        final List<String> focusChanges = new ArrayList<>();
        final long focusStarted = System.nanoTime();

        private Fixture(Path directory) {
            beforeEditor.setManaged(false);
            beforeEditor.setVisible(false);
            VBox.setVgrow(editor, Priority.ALWAYS);
            area = new InteractionArea(directory);
            terminal = (TerminalPanel) area.activeItem().content();
            terminalSurface = (SwingNode) terminal.lookup("#terminal-surface");
            scene.focusOwnerProperty().addListener((observable, previous, current) ->
                    focusChanges.add(String.format("%8.3f ms: %s -> %s",
                            (System.nanoTime() - focusStarted) / 1_000_000.0,
                            describe(previous), describe(current))));
            UiStyles.install(scene);
            stage.setScene(scene);
            stage.setX(-10000);
            stage.setY(-10000);
            stage.show();
            stage.requestFocus();
            root.applyCss();
            root.layout();
        }

        static Fixture create(Path directory) throws Exception {
            Fixture fixture = onFx(() -> new Fixture(directory));
            onEdt(() -> null);
            onFx(() -> null);
            try {
                await(() -> onFx(fixture.stage::isFocused),
                        "the shown test Stage could not acquire native focus; AWT focus checks cannot run");
            } catch (Exception | Error failure) {
                fixture.close();
                throw failure;
            }
            return fixture;
        }

        static Fixture createMounted(Path directory) throws Exception {
            Fixture fixture = create(directory);
            onFx(() -> { fixture.mount(); return null; });
            return fixture;
        }

        void mount() { root.setBottom(area); }

        void click(SwingNode target) {
            var screen = target.localToScreen(100, 40);
            Event.fireEvent(target, new MouseEvent(MouseEvent.MOUSE_PRESSED,
                    100, 40, screen.getX(), screen.getY(), MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false,
                    false, false, true, null));
            Event.fireEvent(target, new MouseEvent(MouseEvent.MOUSE_RELEASED,
                    100, 40, screen.getX(), screen.getY(), MouseButton.PRIMARY, 1,
                    false, false, false, false, false, false, false,
                    false, false, true, null));
        }

        void awaitReady() throws Exception {
            await(() -> {
                PowerShellSession session = terminal.session();
                var widget = terminal.widget();
                if (session == null || session.state() != PowerShellSession.State.RUNNING || widget == null) {
                    return false;
                }
                return onEdt(() -> {
                    var buffer = widget.getTerminalTextBuffer();
                    buffer.lock();
                    try { return buffer.getScreenLines().contains("PS "); }
                    finally { buffer.unlock(); }
                });
            }, "PowerShell did not become ready");
        }

        void assertFocusStays(Node expected) throws Exception {
            // 排空两个事件队列并跨多个 pulse 检查，避免只验证异步抢焦点前的瞬间。
            for (int sample = 0; sample < 8; sample++) {
                onEdt(() -> null);
                Node actual = onFx(scene::getFocusOwner);
                if (actual != expected) {
                    // 保留第一次失配的断言；再观察半秒，区分短暂过渡和持续抢焦点。
                    Thread.sleep(500);
                    String diagnostic = onFx(() -> "Expected " + describe(expected)
                            + ", first mismatch " + describe(actual)
                            + ", after 500 ms " + describe(scene.getFocusOwner())
                            + "\nFocus transitions:\n" + String.join("\n", focusChanges));
                    assertSame(expected, actual,
                            "an earlier Swing/terminal callback overrode the user's latest selection\n"
                                    + diagnostic);
                }
                Thread.sleep(30);
            }
            assertTrue(onFx(stage::isFocused), "the fixture must keep its native window focused");
            Component expectedContent = onFx(() -> expected == editorSurface
                    ? editorSurface.getContent() : terminal.widget());
            Component awtOwner = onEdt(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
            assertTrue(onEdt(() -> awtOwner != null && (awtOwner == expectedContent
                            || SwingUtilities.isDescendingFrom(awtOwner, expectedContent))),
                    () -> "FX chose " + describe(expected) + " but AWT focus is still " + awtOwner
                            + "; expected a descendant of " + expectedContent);
        }

        private String describe(Node node) {
            if (node == editorSurface) return "editor";
            if (node == terminalSurface) return "terminal";
            if (node == null) return "none";
            return node.getClass().getSimpleName() + "[" + node.getId() + "]";
        }

        private static void await(Callable<Boolean> condition, String message) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!condition.call() && System.nanoTime() < deadline) Thread.sleep(25);
            assertTrue(condition.call(), message);
        }

        @Override public void close() throws Exception {
            PowerShellSession current = terminal.session();
            if (current != null) sessions.add(current);
            onFx(() -> { area.close(); root.setBottom(null); return null; });
            onEdt(() -> null);
            onFx(() -> { editor.dispose(); stage.close(); return null; });
            onEdt(() -> null);
            onFx(() -> null);
            for (PowerShellSession session : sessions) {
                await(() -> session.state() == PowerShellSession.State.CLOSED,
                        "focus fixture left its PowerShell process running");
            }
        }
    }
}
