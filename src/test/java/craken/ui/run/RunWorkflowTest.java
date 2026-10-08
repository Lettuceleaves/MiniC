package craken.ui.run;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import craken.ui.component.UiStyles;
import craken.ui.component.terminal.UiTerminalWidget;
import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import craken.ui.frame.AppFrame;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.InteractionItem;
import craken.ui.interaction.inputoutput.InputOutputPanel;
import craken.ui.interaction.inputoutput.InputOutputTab;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real editor snapshot, compiler, executable and ConPTY; its Stage is never shown. */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
@EnabledOnOs(OS.WINDOWS)
final class RunWorkflowTest {
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
    void runButtonExecutesSelectedUnsavedSnapshotAfterSwitchingTabsAndCollapsesDisplay() throws Exception {
        String oldSource = "#include <stdio.h>\nint main(void) { printf(\"CRAKEN_OLD_DISK_CONTENT\\n\"); return 1; }\n";
        String unsavedSource = "#include <stdio.h>\nint main(void) { printf(\"CRAKEN_UNSAVED_RUN_OK\\n\"); return 37; }\n";
        Path firstPath = Files.writeString(temporary.resolve("first.c"),
                "#include <stdio.h>\nint main(void) { printf(\"CRAKEN_WRONG_FIRST_TAB\\n\"); return 2; }\n");
        Path selectedPath = Files.writeString(temporary.resolve("selected unsaved.c"), oldSource);
        try (Fixture ui = onFx(() -> new Fixture(temporary))) {
            onFx(() -> {
                EditorFile first = ui.editors.openFile(firstPath);
                EditorFile selected = ui.editors.openFile(selectedPath);
                selected.editor().setSource(unsavedSource);
                assertSame(selected, ui.editors.activeFile());
                assertTrue(selected.isDirty());
                ui.layout();
                assertTrue(ui.displaySlot().getWidth() > 0, "the display must start expanded");
                Button runButton = assertInstanceOf(Button.class, ui.frame.lookup("#app-run-button"));
                assertFalse(runButton.isDisabled());
                runButton.fire();
                ui.run = ui.runPanel();
                // Stay in this same FX turn: the compile completion cannot race ahead of this switch.
                ui.editors.select(first);
                assertSame(first, ui.editors.activeFile());
                assertSame(ui.scene, ui.run.getScene(), "scene attachment is what enables the native run");
                assertFalse(ui.stage.isShowing());
                return null;
            });

            ui.awaitTerminal();
            ui.await(() -> {
                String text = ui.text();
                return containsLine(text, "CRAKEN_UNSAVED_RUN_OK")
                        && onFx(() -> ui.terminal.isFinished() && Integer.valueOf(37).equals(ui.terminal.exitCode()));
            });
            String terminalOutput = ui.text();
            assertFalse(terminalOutput.contains("CRAKEN_OLD_DISK_CONTENT"), terminalOutput);
            assertFalse(terminalOutput.contains("CRAKEN_WRONG_FIRST_TAB"), terminalOutput);
            assertFalse(terminalOutput.contains("PowerShell"), terminalOutput);
            assertEquals(oldSource, Files.readString(selectedPath), "running must not save the editor buffer");
            Path artifact = onFx(() -> {
                assertNotNull(ui.run.artifact(), "this test must compile a real executable");
                assertSame(ui.editors.files().getFirst(), ui.editors.activeFile());
                assertEquals(unsavedSource, ui.editors.files().get(1).editor().text());
                assertTrue(ui.editors.files().get(1).isDirty());
                assertFalse(ui.stage.isShowing());
                return ui.run.artifact().path();
            });
            assertTrue(Files.isRegularFile(artifact));
            assertTrue(artifact.toAbsolutePath().startsWith(temporary.resolve("build").resolve("craken-runs")));
            ui.await(() -> onFx(() -> {
                ui.layout();
                return ui.displaySlot().getWidth() == 0;
            }));
            assertEquals(0, onFx(() -> ui.displaySlot().getWidth()), 0.0);
            InteractionItem<Label> neighbor = onFx(() -> {
                InteractionItem<?> runItem = ui.interactions.activeItem();
                assertEquals("IO 1", runItem.title());
                var other = ui.interactions.addItem(new InteractionItem<>("保留这个面板", new Label("unrelated")));
                ui.interactions.select(runItem);
                var surface = ui.terminal.lookup("#input-output-surface");
                Event.fireEvent(surface, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                Event.fireEvent(surface, new KeyEvent(KeyEvent.KEY_TYPED, "\r", "", KeyCode.UNDEFINED,
                        false, false, false, false));
                Event.fireEvent(surface, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                return other;
            });
            ui.await(() -> onFx(() -> ui.run.isClosed()));
            onFx(() -> {
                assertEquals(1, ui.interactions.items().size());
                assertSame(neighbor, ui.interactions.activeItem());
                assertFalse(neighbor.isClosed(), "Enter must close only its own run, never the next item");
                return null;
            });
        }
    }

    @Test
    void compilationFinishingBehindAnotherInteractionPanelStillRunsTheExecutable() throws Exception {
        Path source = Files.writeString(temporary.resolve("background.c"),
                "#include <stdio.h>\nint main(void) { printf(\"CRAKEN_BACKGROUND_RUN_OK\\n\"); return 11; }\n");
        var compilationEntered = new CountDownLatch(1);
        var releaseCompilation = new CountDownLatch(1);
        RunController.Compiler gatedCompiler = (snapshot, outputRoot, onProgress) -> {
            compilationEntered.countDown();
            assertTrue(releaseCompilation.await(20, TimeUnit.SECONDS), "test did not release compilation");
            return new RunCompilation().compile(snapshot, outputRoot, onProgress);
        };
        try (Fixture ui = onFx(() -> new Fixture(temporary, gatedCompiler))) {
            onFx(() -> {
                ui.editors.openFile(source);
                ui.layout();
                Button runButton = assertInstanceOf(Button.class, ui.frame.lookup("#app-run-button"));
                assertFalse(runButton.isDisabled());
                runButton.fire();
                ui.run = ui.runPanel();
                assertSame(ui.scene, ui.run.getScene());
                return null;
            });
            assertTrue(compilationEntered.await(10, TimeUnit.SECONDS));
            InteractionItem<Label> placeholder = onFx(() -> {
                var other = ui.interactions.addItem(new InteractionItem<>("其他面板", new Label("保持当前面板")));
                assertSame(other, ui.interactions.activeItem());
                assertNull(ui.run.getScene(), "the run panel must be detached before compilation finishes");
                return other;
            });
            releaseCompilation.countDown();
            ui.awaitTerminal();
            ui.await(() -> {
                String text = ui.text();
                return containsLine(text, "CRAKEN_BACKGROUND_RUN_OK")
                        && onFx(() -> ui.terminal.isFinished() && Integer.valueOf(11).equals(ui.terminal.exitCode()));
            });
            onFx(() -> {
                assertSame(placeholder, ui.interactions.activeItem(), "completion must preserve the user's selected panel");
                assertNull(ui.run.getScene());
                assertNull(ui.terminal.getScene());
                assertNotNull(ui.run.artifact());
                assertTrue(Files.isRegularFile(ui.run.artifact().path()));
                assertFalse(ui.stage.isShowing());
                return null;
            });
        } finally {
            releaseCompilation.countDown();
        }
    }

    @Test
    void runTransfersItsOwnedFocusToProgramInputAndEnterClosesOnlyAfterExit() throws Exception {
        Path source = Files.writeString(temporary.resolve("interactive.c"), """
                #include <stdio.h>
                int main() { puts("FOCUSED_INPUT_READY"); getchar(); return 9; }
                """);
        try (Fixture ui = onFx(() -> new Fixture(temporary))) {
            onFx(() -> {
                ui.editors.openFile(source);
                ui.layout();
                ((Button) ui.frame.lookup("#app-run-button")).fire();
                ui.run = ui.runPanel();
                assertSame(ui.run, ui.scene.getFocusOwner());
                return null;
            });
            ui.awaitTerminal();
            ui.await(() -> ui.text().contains("FOCUSED_INPUT_READY"));
            onFx(() -> {
                assertSame(ui.terminal.lookup("#input-output-surface"), ui.scene.getFocusOwner());
                assertFalse(ui.terminal.isFinished());
                Event.fireEvent(ui.scene.getFocusOwner(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                Event.fireEvent(ui.scene.getFocusOwner(), new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                assertFalse(ui.run.isClosed(), "input Enter must not close the running program");
                return null;
            });
            ui.await(() -> ui.terminal.isFinished());
            assertEquals(9, ui.terminal.exitCode());
            onFx(() -> {
                assertFalse(ui.run.isClosed());
                Event.fireEvent(ui.scene.getFocusOwner(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                Event.fireEvent(ui.scene.getFocusOwner(), new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                return null;
            });
            ui.await(() -> onFx(() -> ui.run.isClosed()));
        }
    }

    private static boolean containsLine(String output, String expected) {
        return output.lines().map(String::stripTrailing).anyMatch(expected::equals);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        SwingUtilities.invokeLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    private static final class Fixture implements AutoCloseable {
        final EditorArea editors = new EditorArea();
        final InteractionArea interactions;
        final AppFrame frame;
        final RunController controller;
        final Scene scene;
        final Stage stage = new Stage(StageStyle.UNDECORATED);
        RunPanel run;
        InputOutputPanel terminal;
        JediTermWidget widget;

        Fixture(Path root) {
            this(root, null);
        }

        Fixture(Path root, RunController.Compiler compiler) {
            editors.setCloseDecisionHandler(file -> EditorArea.CloseChoice.DISCARD);
            interactions = new InteractionArea(root);
            interactions.closeItem(interactions.activeItem());
            frame = new AppFrame(editors, new Region(), interactions, editors.tabBar());
            controller = compiler == null ? new RunController(editors, frame, interactions)
                    : new RunController(editors, frame, interactions, compiler);
            scene = new Scene(frame, 1280, 800);
            UiStyles.install(scene);
            stage.setScene(scene);
            // TerminalPanelTest also initializes its scene and lays out Swing content without
            // showing a Stage. RunPanel.activate only requires getScene() to be non-null.
            layout();
        }

        void layout() {
            frame.applyCss();
            frame.resize(1280, 800);
            frame.layout();
        }

        Region displaySlot() { return (Region) frame.lookup(".app-display-slot"); }

        RunPanel runPanel() {
            InputOutputTab tab = assertInstanceOf(InputOutputTab.class, interactions.activeItem().content());
            return assertInstanceOf(RunPanel.class, tab.channel().node());
        }

        void awaitTerminal() throws Exception {
            await(() -> onFx(() -> {
                layout();
                if (!(run.getCenter() instanceof InputOutputPanel runningTerminal)) return false;
                terminal = runningTerminal;
                SwingNode surface = (SwingNode) terminal.lookup("#input-output-surface");
                if (!(surface.getContent() instanceof JediTermWidget runningWidget)) return false;
                widget = runningWidget;
                return true;
            }));
            assertEquals(UiStyles.tabDefaultFont(),
                    assertInstanceOf(UiTerminalWidget.class, widget).settingsProvider().getTerminalFont(),
                    "运行 IO 项的内容使用这个 tab 的默认字体");
            onEdt(() -> {
                widget.setSize(1050, 240);
                layoutChildren(widget);
                return null;
            });
        }

        private static void layoutChildren(Container container) {
            container.doLayout();
            for (var component : container.getComponents()) {
                if (component instanceof Container child) layoutChildren(child);
            }
        }

        String text() throws Exception {
            return onEdt(() -> {
                if (widget == null) return "";
                TerminalTextBuffer buffer = widget.getTerminalTextBuffer();
                buffer.lock();
                try {
                    var lines = new ArrayList<TerminalLine>();
                    buffer.getHistoryLinesStorage().forEach(lines::add);
                    buffer.getScreenLinesStorage().forEach(lines::add);
                    var output = new StringBuilder();
                    for (TerminalLine line : lines) {
                        output.append(line.getText().replace("\0", "").replace("\uE000", ""));
                        if (!line.isWrapped()) output.append('\n');
                    }
                    return output.toString();
                } finally { buffer.unlock(); }
            });
        }

        void await(Callable<Boolean> condition) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                if (condition.call()) return;
                Thread.sleep(25);
            }
            String diagnostics = onFx(() -> {
                if (run == null) return "RunPanel was not created";
                String status = run.lookup("#run-status") instanceof Label label ? label.getText() : "";
                String details = run.lookup("#run-diagnostics") instanceof TextInputControl input ? input.getText() : "";
                return status + "\n" + details;
            });
            fail("Timed out. Run diagnostics:\n" + diagnostics + "\nTerminal output:\n" + text());
        }

        @Override
        public void close() throws Exception {
            TtyConnector connection = onEdt(() -> widget == null ? null : widget.getTtyConnector());
            onFx(() -> {
                controller.close();
                interactions.close();
                if (terminal != null) terminal.close();
                assertTrue(editors.closeAll());
                return null;
            });
            onEdt(() -> null);
            // Drain detach work before closing only this fixture's never-shown Stage.
            onFx(() -> { stage.close(); stage.setScene(null); return null; });
            onEdt(() -> null);
            onFx(() -> null);
            if (connection != null) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (connection.isConnected() && System.nanoTime() < deadline) Thread.sleep(25);
                assertFalse(connection.isConnected(), "the workflow test's own program was left running");
            }
        }
    }
}
