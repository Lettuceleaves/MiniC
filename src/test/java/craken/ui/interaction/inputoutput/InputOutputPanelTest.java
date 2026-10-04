package craken.ui.interaction.inputoutput;

import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import craken.compiler.SourceFile;
import craken.ui.component.UiStyles;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.InteractionItem;
import craken.ui.run.RunCompilation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.SwingUtilities;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 Craken 原生产物 + ConPTY + JediTerm；不使用 shell 执行命令。 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
@EnabledOnOs(OS.WINDOWS)
final class InputOutputPanelTest {
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
    void rendererIsReadyBeforeStartingAndFastExitStillDrainsAllOutputBeforeFinishing() throws Exception {
        Path executable = compile("fast", """
                #include <stdio.h>
                int main() {
                    int i = 0;
                    while (i < 120) {
                        printf("DRAIN_LINE_%d\\n", i);
                        i = i + 1;
                    }
                    printf("FINAL_OUTPUT\\n");
                    return 37;
                }
                """);
        Fixture ui = Fixture.create(temporary, executable, false);
        var releaseEdt = new CountDownLatch(1);
        var edtBlocked = new CompletableFuture<Void>();
        SwingUtilities.invokeLater(() -> {
            edtBlocked.complete(null);
            try { releaseEdt.await(15, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        edtBlocked.get(10, TimeUnit.SECONDS);
        try (ui) {
            try {
                onFx(() -> {
                    Label message = (Label) ui.panel.lookup("#input-output-message");
                    assertTrue(message.getText().isEmpty());
                    assertFalse(message.isVisible());
                    assertFalse(message.isManaged());
                    ui.panel.start();
                    assertFalse(message.isVisible(), "startup must not overlay temporary executable text");
                    return null;
                });
                assertEquals(ProgramSession.State.NEW, ui.panel.session().state(),
                        "a backed-up EDT must not start a program before its output reader is ready");
                assertFalse(ui.panel.isFinished());
            } finally {
                releaseEdt.countDown();
            }
            ui.awaitFinished();
            assertEquals(37, ui.panel.exitCode());
            String text = ui.text();
            assertTrue(text.contains("DRAIN_LINE_0"), text);
            assertTrue(text.contains("DRAIN_LINE_119"), text);
            assertTrue(text.contains("FINAL_OUTPUT"), text);
            assertFalse(text.contains("Windows PowerShell"), text);
            assertFalse(text.contains("PS " + temporary), text);
            onFx(() -> {
                assertTrue(ui.status().contains("37"));
                assertTrue(ui.status().contains("按回车关闭此项"));
                assertTrue(((Button) ui.panel.lookup("#input-output-stop")).isDisabled());
                assertNull(ui.panel.lookup("#terminal-restart"));
                assertEquals("用户程序输入输出", ui.panel.lookup("#input-output-surface").getAccessibleText());
                assertFalse(ui.panel.lookup("#input-output-message").isVisible());
                return null;
            });
            ui.fx(KeyEvent.KEY_PRESSED);
            assertEquals(0, ui.closeCount.get(), "close waits for release, preventing repeats in the next item");
            ui.fx(KeyEvent.KEY_TYPED);
            ui.fx(KeyEvent.KEY_RELEASED);
            await(() -> ui.closeCount.get() == 1);
            ui.awt(java.awt.event.KeyEvent.KEY_PRESSED);
            ui.awt(java.awt.event.KeyEvent.KEY_RELEASED);
            onFx(() -> null);
            assertEquals(1, ui.closeCount.get(), "mixed FX/AWT delivery can request close only once");
        }
    }

    @ParameterizedTest
    @EnumSource(KeySource.class)
    void inputEnterThatFinishesTheProgramMustBeReleasedBeforeANewEnterCanClose(KeySource source) throws Exception {
        Path executable = compile("input", """
                #include <stdio.h>
                int main() {
                    printf("INPUT_READY\\n");
                    int character = getchar();
                    getchar();
                    printf("INPUT_ANSWER=%d\\n", character);
                    return 29;
                }
                """);
        try (Fixture ui = Fixture.create(temporary, executable, true)) {
            ui.awaitText("INPUT_READY");
            ui.type("x");
            ui.key(source, java.awt.event.KeyEvent.KEY_PRESSED);
            ui.awaitFinished();
            assertEquals(29, ui.panel.exitCode());
            assertTrue(ui.text().contains("INPUT_ANSWER=120"), ui.text());
            assertEquals(0, ui.closeCount.get());

            ui.key(source, java.awt.event.KeyEvent.KEY_TYPED);
            for (int repeat = 0; repeat < 4; repeat++) ui.key(source, java.awt.event.KeyEvent.KEY_PRESSED);
            ui.key(source, java.awt.event.KeyEvent.KEY_RELEASED);
            onFx(() -> null);
            assertEquals(0, ui.closeCount.get(), "the completing Enter and all its repeats belong to the program");
            assertFalse(ui.panel.session().write("must not reach an exited program\r"));

            ui.key(source, java.awt.event.KeyEvent.KEY_PRESSED);
            ui.key(source, java.awt.event.KeyEvent.KEY_TYPED);
            ui.key(source, java.awt.event.KeyEvent.KEY_PRESSED);
            assertEquals(0, ui.closeCount.get());
            ui.key(source, java.awt.event.KeyEvent.KEY_RELEASED);
            await(() -> ui.closeCount.get() == 1);
            assertTrue(ui.closeOnFxThread);
        }
    }

    @Test
    void imePlaceholderKeysDoNotBecomeNulOrSwallowCommittedChinese() throws Exception {
        Path executable = compile("chinese-input", """
                #include <stdio.h>
                int main() {
                    char word[64];
                    puts("等待中文输入");
                    scanf("%63s", word);
                    printf("收到：%s\\n", word);
                    return 0;
                }
                """);
        try (Fixture ui = Fixture.create(temporary, executable, true)) {
            ui.awaitText("等待中文输入");
            onEdt(() -> {
                var terminal = ui.panel.widget().getTerminalPanel();
                for (char character : "你好世界".toCharArray()) {
                    // Windows IME 经 SwingNode 转发：按下无文本，中文在后续 KEY_TYPED 提交。
                    terminal.processKeyEvent(new java.awt.event.KeyEvent(terminal,
                            java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, 229, '\0'));
                    terminal.processKeyEvent(new java.awt.event.KeyEvent(terminal,
                            java.awt.event.KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
                            java.awt.event.KeyEvent.VK_UNDEFINED, character));
                    terminal.processKeyEvent(new java.awt.event.KeyEvent(terminal,
                            java.awt.event.KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, 229, '\0'));
                }
                return null;
            });
            ui.fx(KeyEvent.KEY_PRESSED);
            ui.fx(KeyEvent.KEY_TYPED);
            ui.fx(KeyEvent.KEY_RELEASED);
            ui.awaitFinished();
            String text = ui.text();
            assertTrue(text.contains("收到：你好世界"), text);
            assertFalse(text.contains("^@"), text);
            assertEquals(0, ui.panel.exitCode());
            assertEquals(0, ui.closeCount.get(), "submitting input must not also close its panel");
            ui.fx(KeyEvent.KEY_PRESSED);
            ui.fx(KeyEvent.KEY_RELEASED);
            await(() -> ui.closeCount.get() == 1);
        }
    }

    @Test
    void startupFailureIsClosableWithoutAProcessExitCode() throws Exception {
        try (Fixture ui = Fixture.create(temporary, temporary.resolve("missing program.exe"), true)) {
            ui.awaitFinished();
            assertNull(ui.panel.exitCode());
            onFx(() -> {
                assertTrue(ui.status().contains("启动失败"));
                Label message = (Label) ui.panel.lookup("#input-output-message");
                assertTrue(message.isVisible(), "startup failures must still be shown");
                assertTrue(message.isManaged());
                assertTrue(message.getText().startsWith("程序启动失败："));
                assertTrue(ui.status().contains("按回车关闭此项"));
                return null;
            });
            ui.fx(KeyEvent.KEY_RELEASED);
            ui.fx(KeyEvent.KEY_TYPED);
            assertEquals(0, ui.closeCount.get(), "a release or typed event alone must never close the item");
            ui.fx(KeyEvent.KEY_PRESSED);
            ui.fx(KeyEvent.KEY_RELEASED);
            await(() -> ui.closeCount.get() == 1);
        }
    }

    @Test
    void stoppingLeavesOutputAndLetsOneEnterCloseOnlyItsOwnedInteractionItem() throws Exception {
        Path executable = compile("stop", """
                #include <stdio.h>
                int main() {
                    printf("STOP_READY\\n");
                    getchar();
                    return 0;
                }
                """);
        try (Fixture ui = Fixture.create(temporary, executable, true)) {
            ui.awaitText("STOP_READY");
            InteractionItem<TextField> neighbor = onFx(() -> {
                var item = ui.area.addItem(new InteractionItem<>("保留此项", new TextField("untouched")));
                ui.area.select(ui.item);
                ui.panel.setOnCloseRequest(() -> {
                    ui.closeCount.incrementAndGet();
                    ui.area.closeItem(ui.item);
                });
                ui.panel.stop();
                return item;
            });
            ui.awaitFinished();
            assertTrue(ui.text().contains("STOP_READY"));
            ui.fx(KeyEvent.KEY_PRESSED);
            ui.fx(KeyEvent.KEY_RELEASED);
            await(() -> ui.closeCount.get() == 1);
            onFx(() -> {
                assertTrue(ui.item.isClosed());
                assertFalse(neighbor.isClosed());
                assertEquals(List.of(neighbor), ui.area.items());
                assertEquals("untouched", neighbor.content().getText());
                return null;
            });
        }
    }

    @Test
    void shownNativeSurfaceRestoresOnSwitchingAndReleasesOnClose() throws Exception {
        Path executable = compile("surface", """
                #include <stdio.h>
                int main() { printf("SURFACE_READY\\n"); getchar(); return 0; }
                """);
        try (Fixture ui = Fixture.create(temporary, executable, true)) {
            ui.awaitText("SURFACE_READY");
            ui.showStage();
            JediTermWidget original = ui.panel.widget();
            Window originalHost = onEdt(() -> assertNativeSurfacePrepared(original));
            onFx(() -> {
                ui.area.addItem(new InteractionItem<>("其他", new TextField("other")));
                assertNull(ui.panel.getScene());
                return null;
            });
            onEdt(() -> {
                assertFalse(originalHost.isOpaque());
                return null;
            });
            onFx(() -> {
                ui.area.select(ui.item);
                assertSame(original, ui.panel.widget());
                return null;
            });
            onFx(() -> null);
            Window reattached = onEdt(() -> assertNativeSurfacePrepared(original));
            assertNotSame(originalHost, reattached);
            onFx(() -> { ui.area.closeItem(ui.item); return null; });
            onEdt(() -> {
                assertNull(ui.panel.widget());
                assertFalse(reattached.isOpaque());
                return null;
            });
        }
    }

    @Test
    void closeBeforeQueuedWidgetConstructionCannotPublishContentOrRestartTheProgram() throws Exception {
        Path executable = compile("closed", "int main() { return 0; }");
        var releaseEdt = new CountDownLatch(1);
        var edtBlocked = new CompletableFuture<Void>();
        SwingUtilities.invokeLater(() -> {
            edtBlocked.complete(null);
            try { releaseEdt.await(15, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        edtBlocked.get(10, TimeUnit.SECONDS);
        InputOutputPanel panel;
        try {
            panel = onFx(() -> {
                var result = new InputOutputPanel(temporary, executable);
                result.start();
                result.close();
                result.start();
                result.activate();
                assertNull(result.widget());
                return result;
            });
        } finally {
            releaseEdt.countDown();
        }
        onEdt(() -> null);
        assertNull(panel.widget());
        await(() -> panel.session().state() == ProgramSession.State.CLOSED);
    }

    private Path compile(String name, String source) throws Exception {
        var outcome = new RunCompilation().compile(new SourceFile(temporary.resolve(name + ".mc").toString(), source),
                temporary.resolve("artifacts"));
        assertTrue(outcome.succeeded(), () -> outcome.diagnostics().toString());
        return outcome.artifact().path();
    }

    private enum KeySource { FX, AWT }

    private static Window assertNativeSurfacePrepared(JediTermWidget widget) {
        Window host = SwingUtilities.getWindowAncestor(widget);
        assertNotNull(host);
        assertTrue(host.isOpaque());
        assertEquals(widget.getBackground(), host.getBackground());
        assertFalse(widget.isDoubleBuffered());
        assertFalse(widget.getTerminalPanel().isDoubleBuffered());
        return host;
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (!condition.call() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(condition.call(), "timed out awaiting input/output state");
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        SwingUtilities.invokeLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    private static final class Fixture implements AutoCloseable {
        final InteractionArea area;
        final InputOutputPanel panel;
        final InteractionItem<InputOutputPanel> item;
        final AtomicInteger closeCount = new AtomicInteger();
        volatile boolean closeOnFxThread;
        Stage stage;

        Fixture(Path root, Path executable, boolean start) {
            area = new InteractionArea(root);
            area.closeItem(area.activeItem()); // Do not start an unrelated shell in a program I/O test.
            panel = new InputOutputPanel(root, executable);
            item = area.addItem(new InteractionItem<>("输入输出测试", panel,
                    start ? panel::start : () -> { }, panel::activate, panel::close));
            panel.setOnCloseRequest(() -> {
                closeOnFxThread = Platform.isFxApplicationThread();
                closeCount.incrementAndGet();
            });
            Scene scene = new Scene(area, 1200, 360);
            UiStyles.install(scene);
            area.applyCss();
            area.resize(1200, 360);
            area.layout();
        }

        static Fixture create(Path root, Path executable, boolean start) throws Exception {
            return onFx(() -> new Fixture(root, executable, start));
        }

        void awaitFinished() throws Exception { await(panel::isFinished); }
        void awaitText(String expected) throws Exception {
            await(() -> panel.widget() != null && text().contains(expected));
        }

        String status() { return ((Label) panel.lookup("#input-output-status")).getText(); }

        String text() throws Exception {
            return onEdt(() -> {
                JediTermWidget widget = panel.widget();
                if (widget == null) return "";
                TerminalTextBuffer buffer = widget.getTerminalTextBuffer();
                buffer.lock();
                try {
                    var lines = new ArrayList<TerminalLine>();
                    buffer.getHistoryLinesStorage().forEach(lines::add);
                    buffer.getScreenLinesStorage().forEach(lines::add);
                    var text = new StringBuilder();
                    for (TerminalLine line : lines) {
                        text.append(line.getText().replace("\0", "").replace("\uE000", ""));
                        if (!line.isWrapped()) text.append('\n');
                    }
                    return text.toString();
                } finally { buffer.unlock(); }
            });
        }

        void type(String text) throws Exception {
            onEdt(() -> {
                var terminal = panel.widget().getTerminalPanel();
                for (char character : text.toCharArray()) {
                    for (int id : List.of(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.KEY_TYPED,
                            java.awt.event.KeyEvent.KEY_RELEASED)) {
                        terminal.processKeyEvent(new java.awt.event.KeyEvent(terminal, id,
                                System.currentTimeMillis(), 0, id == java.awt.event.KeyEvent.KEY_TYPED
                                ? java.awt.event.KeyEvent.VK_UNDEFINED
                                : java.awt.event.KeyEvent.getExtendedKeyCodeForChar(character), character));
                    }
                }
                return null;
            });
        }

        void awt(int id) throws Exception {
            onEdt(() -> {
                var terminal = panel.widget().getTerminalPanel();
                terminal.processKeyEvent(new java.awt.event.KeyEvent(terminal, id, System.currentTimeMillis(),
                        0, id == java.awt.event.KeyEvent.KEY_TYPED ? java.awt.event.KeyEvent.VK_UNDEFINED
                        : java.awt.event.KeyEvent.VK_ENTER, '\n'));
                return null;
            });
        }

        void fx(javafx.event.EventType<KeyEvent> type) throws Exception {
            onFx(() -> {
                Event.fireEvent(panel.lookup("#input-output-surface"), new KeyEvent(type,
                        type == KeyEvent.KEY_TYPED ? "\r" : KeyEvent.CHAR_UNDEFINED, "",
                        type == KeyEvent.KEY_TYPED ? KeyCode.UNDEFINED : KeyCode.ENTER,
                        false, false, false, false));
                return null;
            });
        }

        void key(KeySource source, int id) throws Exception {
            if (source == KeySource.AWT) awt(id);
            else fx(id == java.awt.event.KeyEvent.KEY_PRESSED ? KeyEvent.KEY_PRESSED
                    : id == java.awt.event.KeyEvent.KEY_RELEASED ? KeyEvent.KEY_RELEASED : KeyEvent.KEY_TYPED);
        }

        void showStage() throws Exception {
            onFx(() -> {
                stage = new Stage(StageStyle.UNDECORATED);
                stage.setScene(area.getScene());
                stage.setX(-10000);
                stage.setY(-10000);
                stage.show();
                area.getScene().snapshot(null);
                return null;
            });
            onEdt(() -> null);
        }

        @Override
        public void close() throws Exception {
            ProgramSession session = panel.session();
            onFx(() -> {
                area.close();
                panel.close();
                return null;
            });
            onEdt(() -> null);
            onFx(() -> {
                if (stage != null) stage.close();
                return null;
            });
            onEdt(() -> null);
            onFx(() -> null);
            if (session != null) await(() -> session.state() == ProgramSession.State.CLOSED);
        }
    }
}
