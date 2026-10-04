package craken.ui.interaction;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import craken.ui.component.UiStyles;
import craken.ui.interaction.inputoutput.InputOutputPanel;
import craken.ui.interaction.terminal.TerminalPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class InputOutputItemTest {
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
    void publicFactoryExposesInputOutputSeparatelyFromPowerShell() throws Exception {
        onFx(() -> {
            try (var area = new InteractionArea(temporary)) {
                var shell = area.activeItem();
                assertInstanceOf(TerminalPanel.class, shell.content());
                InteractionItem<InputOutputPanel> io = area.newInputOutput(temporary, temporary.resolve("example.exe"));
                assertEquals("IO 1", io.title());
                assertSame(io, area.activeItem());
                assertEquals(temporary.toAbsolutePath().normalize(), io.content().workingDirectory());
                assertNull(io.content().getScene());
                assertFalse(io.content().isFinished());
                assertNull(io.content().exitCode());
                assertNull(io.content().lookup("#terminal-restart"));
                assertNotNull(io.content().lookup("#input-output-surface"));
                assertTrue(area.closeItem(io));
                assertTrue(io.isClosed());
                assertFalse(shell.isClosed());
                assertSame(shell, area.activeItem());
            }
            return null;
        });
    }

    @Test
    void defaultIoNamesIncreaseIndependentlyAndAreNotReusedAfterClosing() throws Exception {
        onFx(() -> {
            try (var area = new InteractionArea(temporary)) {
                assertEquals("PowerShell 1", area.activeItem().title());
                var first = area.newInputOutput(temporary, temporary.resolve("same.exe"));
                assertEquals("IO 1", first.title());
                assertEquals("PowerShell 2", area.newTerminal().title());
                var second = area.newInputOutput(temporary, temporary.resolve("same.exe"));
                assertEquals("IO 2", second.title());
                area.closeItem(first);
                assertEquals("IO 2", second.title(), "closing a neighbor must not rename an existing item");
                assertEquals("IO 3", area.newInputOutput(temporary, temporary.resolve("other.exe")).title());
            }
            try (var otherArea = new InteractionArea(temporary)) {
                assertEquals("IO 1", otherArea.newInputOutput(temporary, temporary.resolve("same.exe")).title());
            }
            return null;
        });
    }

    @Test
    void completionEnterClosesItsOwnFactoryItemEvenAfterSelectingANeighbor() throws Exception {
        InteractionArea area = onFx(() -> new InteractionArea(temporary));
        Stage stage = onFx(Stage::new);
        try {
            InteractionItem<InputOutputPanel> io = onFx(() -> {
                area.closeItem(area.activeItem());
                var item = area.newInputOutput("输入输出 · 启动失败用例", temporary, temporary.resolve("missing.exe"));
                Scene scene = new Scene(area, 900, 240);
                UiStyles.install(scene);
                stage.setScene(scene);
                area.applyCss();
                area.layout();
                return item;
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!onFx(() -> io.content().isFinished()) && System.nanoTime() < deadline) Thread.sleep(25);
            assertTrue(onFx(() -> io.content().isFinished()), "failed startup should offer a close prompt");
            InteractionItem<Label> neighbor = onFx(() -> {
                var other = area.addItem(new InteractionItem<>("不受影响", new Label("keep")));
                // A delayed event belonging to the completed view must never close activeItem().
                var surface = io.content().lookup("#input-output-surface");
                Event.fireEvent(surface, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                Event.fireEvent(surface, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                return other;
            });
            onFx(() -> null);
            onFx(() -> {
                assertTrue(io.isClosed());
                assertFalse(area.items().contains(io));
                assertEquals(1, area.items().size());
                assertSame(neighbor, area.activeItem());
                assertFalse(neighbor.isClosed());
                assertFalse(stage.isShowing());
                return null;
            });
        } finally {
            onFx(() -> { area.close(); stage.close(); stage.setScene(null); return null; });
        }
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var future = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { future.complete(action.call()); }
            catch (Throwable error) { future.completeExceptionally(error); }
        });
        return future.get(20, TimeUnit.SECONDS);
    }
}
