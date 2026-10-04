package craken.ui.interaction.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import craken.ui.component.UiStyles;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.InteractionItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 JediTerm 键盘事件、终端渲染和 PowerShell PTY；不打开用户可见的测试窗口。 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
@EnabledOnOs(OS.WINDOWS)
final class TerminalPanelTest {
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
    void runPanelRestartsTheSameExecutableAndKeepsItsWorkingDirectory() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("run directory")).toRealPath();
        Path executable = Files.copy(Path.of(System.getenv("SystemRoot"), "System32", "whoami.exe"),
                root.resolve("user's executable.exe"));
        try (Fixture ui = Fixture.createRun(root, executable)) {
            ui.awaitReady();
            ui.awaitLine("[Craken] Process exited with code 0");
            assertEquals("程序运行终端", onFx(() -> ui.terminal.lookup("#terminal-surface").getAccessibleText()));
            ui.enter("'RUN_DIRECTORY=' + (Get-Location).Path");
            ui.awaitLine("RUN_DIRECTORY=" + root);
            PowerShellSession original = onFx(() -> ui.terminal.session());
            JediTermWidget originalWidget = ui.widget();
            onFx(() -> { ui.terminal.restart(); return null; });
            ui.awaitReady();
            ui.awaitLine("[Craken] Process exited with code 0");
            assertNotSame(original, onFx(() -> ui.terminal.session()));
            assertNotSame(originalWidget, ui.widget());
            ui.await(() -> original.state() == PowerShellSession.State.CLOSED);
        }
    }

    @Test
    void bodyKeyboardInputRunsAtProjectRootAndSupportsReadHostAndAnsiColors() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("项目 with spaces")).toRealPath();
        try (Fixture ui = Fixture.create(root)) {
            ui.awaitReady();
            onFx(() -> {
                assertEquals(root, ui.area.projectRoot());
                assertEquals(root, ui.terminal.workingDirectory());
                assertInstanceOf(SwingNode.class, ui.terminal.lookup("#terminal-surface"));
                assertNull(ui.terminal.lookup("#terminal-input"));
                return null;
            });
            ui.enter("'ROOT=' + (Get-Location).Path");
            ui.awaitLine("ROOT=" + root);
            ui.enter("$reply = Read-Host 'NAME_PROMPT'; 'ANSWER=' + $reply");
            ui.awaitText("NAME_PROMPT:");
            ui.enter("你好，标准终端");
            ui.awaitLine("ANSWER=你好，标准终端");
            ui.enter("[Console]::WriteLine([char]27 + '[31mANSI_COLOR' + [char]27 + '[0m')");
            ui.awaitLine("ANSI_COLOR");
            assertFalse(ui.text().contains("\u001b[31m"), "VT escape sequences must be interpreted, not printed");
            assertTrue(onEdt(() -> {
                TerminalTextBuffer buffer = ui.widget().getTerminalTextBuffer();
                buffer.lock();
                try {
                    return lines(buffer).stream().filter(line -> line.getText().strip().equals("ANSI_COLOR"))
                            .anyMatch(line -> TerminalColor.index(1).equals(line.getStyleAt(0).getForeground()));
                } finally { buffer.unlock(); }
            }), "ANSI red must become the terminal cell's foreground style");
            ui.snapshot(Path.of("build", "terminal-research", "standard-terminal.png"));
        }
    }

    @Test
    void completionHistoryInterruptAndResizeTravelThroughTheTerminal() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("completion-root")).toRealPath();
        Files.writeString(root.resolve("terminal-completion-proof.txt"), "completion fixture");
        try (Fixture ui = Fixture.create(root)) {
            ui.awaitReady();
            ui.type("Get-Item .\\terminal-completion-pr");
            ui.press(KeyEvent.VK_TAB, 0);
            ui.awaitText("terminal-completion-proof.txt");
            ui.type(" | ForEach-Object { 'TAB=' + $_.Name }");
            ui.press(KeyEvent.VK_ENTER, 0);
            ui.awaitLine("TAB=terminal-completion-proof.txt");

            ui.enter("$counter = 0");
            ui.enter("$counter++; 'HISTORY=' + $counter");
            ui.awaitLine("HISTORY=1");
            ui.press(KeyEvent.VK_UP, 0);
            ui.press(KeyEvent.VK_ENTER, 0);
            ui.awaitLine("HISTORY=2");

            ui.enter("'BUSY_READY'; Start-Sleep -Seconds 30; 'SHOULD_NOT_RUN'");
            ui.awaitLine("BUSY_READY");
            ui.fxPress(KeyCode.C, true);
            ui.awaitPrompt();
            ui.enter("'AFTER_INTERRUPT'");
            ui.awaitLine("AFTER_INTERRUPT");
            assertFalse(ui.text().lines().anyMatch(line -> line.strip().equals("SHOULD_NOT_RUN")));

            int previousWidth = onEdt(() -> ui.widget().getTerminalTextBuffer().getWidth());
            ui.resize(640, 180);
            ui.await(() -> onEdt(() -> ui.widget().getTerminalTextBuffer().getWidth()) != previousWidth);
            int newWidth = onEdt(() -> ui.widget().getTerminalTextBuffer().getWidth());
            ui.enter("'WIDTH=' + [Console]::WindowWidth");
            ui.awaitLine("WIDTH=" + newWidth);
        }
    }

    @Test
    void javafxControlKeysReachTheConsoleWithoutFiringApplicationAccelerators() throws Exception {
        try (Fixture ui = Fixture.create(temporary)) {
            ui.awaitReady();
            var shortcuts = new AtomicInteger();
            onFx(() -> {
                ui.terminal.getScene().getAccelerators().put(
                        new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN), shortcuts::incrementAndGet);
                return null;
            });
            ui.enter("'CONTROL_READY'; $key = [Console]::ReadKey($true); 'CONTROL=' + [int]$key.KeyChar");
            ui.awaitLine("CONTROL_READY");
            // 真正的 JavaFX Windows 事件：修饰键 text 为空，Ctrl+W 的按下 text 仍是 w。
            ui.fxPress(KeyCode.CONTROL, true);
            ui.fxPress(KeyCode.W, true);
            ui.awaitLine("CONTROL=23");
            assertEquals(0, shortcuts.get(), "the terminal must own Ctrl+W before Scene accelerators");

            ui.enter("'ESCAPE_READY'; $key = [Console]::ReadKey($true); 'ESCAPE=' + [int]$key.KeyChar");
            ui.awaitLine("ESCAPE_READY");
            ui.fxPress(KeyCode.SHIFT, false);
            ui.fxPress(KeyCode.ESCAPE, false);
            ui.awaitLine("ESCAPE=27");
        }
    }

    @Test
    void switchingAndClearingPreserveTheSessionAndRestartReturnsToProjectRoot() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("lifecycle-root")).toRealPath();
        Path child = Files.createDirectories(root.resolve("child"));
        Long originalPid = null;
        Long restartedPid = null;
        Fixture ui = Fixture.create(root);
        try {
            ui.awaitReady();
            ui.enter("$kept = 41; Set-Location child; 'FIRST=' + $PID");
            originalPid = ui.awaitNumber("FIRST");
            JediTermWidget originalWidget = ui.widget();
            assertInstanceOf(TerminalPanel.Widget.class, originalWidget);
            onFx(() -> {
                var item = ui.area.activeItem();
                ui.area.addItem(new InteractionItem<>("其他交互区", new TextField("独立容器")));
                assertNull(ui.terminal.getParent());
                ui.area.select(item);
                assertSame(originalWidget, ui.terminal.widget());
                ui.terminal.clearOutput();
                return null;
            });
            onEdt(() -> null);
            assertFalse(ui.text().contains("FIRST="), "clear removes terminal scrollback");
            ui.enter("$kept++; 'KEPT=' + $kept; 'SAME=' + $PID; 'PWD=' + (Get-Location).Path");
            ui.awaitLine("KEPT=42");
            assertEquals(originalPid, ui.awaitNumber("SAME"));
            ui.awaitLine("PWD=" + child);

            onFx(() -> { ui.terminal.restart(); return null; });
            ui.awaitReady();
            assertNotSame(originalWidget, ui.widget());
            assertInstanceOf(TerminalPanel.Widget.class, ui.widget());
            ui.enter("'RESTART=' + $PID; 'ROOT=' + (Get-Location).Path; 'RESET=' + ($null -eq $kept)");
            restartedPid = ui.awaitNumber("RESTART");
            assertNotEquals(originalPid, restartedPid);
            ui.awaitLine("ROOT=" + root);
            ui.awaitLine("RESET=True");
            awaitExited(originalPid);
            onFx(() -> { ui.terminal.stop(); return null; });
            awaitExited(restartedPid);
        } finally {
            ui.close();
            if (originalPid != null) awaitExited(originalPid);
            if (restartedPid != null) awaitExited(restartedPid);
        }
    }

    @Test
    void shownNativeSurfaceRestoresAcrossDetachReattachAndClose() throws Exception {
        try (Fixture ui = Fixture.create(temporary)) {
            ui.awaitReady();
            ui.showStage();
            JediTermWidget originalWidget = ui.widget();
            Window originalHost = onEdt(() -> assertNativeSurfacePrepared(originalWidget));
            var originalItem = onFx(() -> {
                var item = ui.area.activeItem();
                ui.area.addItem(new InteractionItem<>("其他交互区", new TextField("独立容器")));
                assertNull(ui.terminal.getScene());
                return item;
            });
            onEdt(() -> {
                assertFalse(originalHost.isOpaque(), "detach restores the hidden host's original transparency");
                return null;
            });

            onFx(() -> {
                ui.area.select(originalItem);
                assertSame(originalWidget, ui.terminal.widget(), "reattach must preserve the terminal session");
                return null;
            });
            // 生产代码先等下一轮 FX，再把 prepare 排到 SwingNode 的重建工作之后。
            onFx(() -> null);
            Window attachedHost = onEdt(() -> assertNativeSurfacePrepared(originalWidget));
            assertNotSame(originalHost, attachedHost, "a shown SwingNode must recreate its host on reattach");
            onFx(() -> { ui.terminal.close(); return null; });
            onEdt(() -> {
                assertNull(ui.terminal.widget());
                assertFalse(attachedHost.isOpaque(), "close releases the local optimization before closing the widget");
                return null;
            });
        }
    }

    private static Window assertNativeSurfacePrepared(JediTermWidget terminal) {
        Window host = SwingUtilities.getWindowAncestor(terminal);
        assertNotNull(host, "SwingNode must have attached the terminal to its own host");
        assertTrue(host.isOpaque());
        assertEquals(terminal.getBackground(), host.getBackground());
        assertFalse(terminal.isDoubleBuffered());
        assertFalse(terminal.getTerminalPanel().isDoubleBuffered());
        return host;
    }

    @Test
    void restartAndCloseWhileWidgetConstructionIsQueuedCannotPublishAStaleWidget() throws Exception {
        var releaseEdt = new CountDownLatch(1);
        var edtBlocked = new CompletableFuture<Void>();
        SwingUtilities.invokeLater(() -> {
            edtBlocked.complete(null);
            try { releaseEdt.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        edtBlocked.get(10, TimeUnit.SECONDS);
        record StartupRace(TerminalPanel panel, List<PowerShellSession> sessions) { }
        StartupRace race;
        try {
            race = onFx(() -> {
                var panel = new TerminalPanel(temporary);
                panel.activate();
                var first = panel.session();
                panel.restart();
                var second = panel.session();
                panel.close();
                assertNull(panel.widget());
                return new StartupRace(panel, List.of(first, second));
            });
        } finally {
            releaseEdt.countDown();
        }
        onEdt(() -> null);
        assertNull(onFx(() -> race.panel().widget()), "queued constructors must not republish a closed widget");
        for (var session : race.sessions()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (session.state() != PowerShellSession.State.CLOSED && System.nanoTime() < deadline) Thread.sleep(25);
            assertEquals(PowerShellSession.State.CLOSED, session.state());
        }
    }

    private static void awaitExited(long pid) throws Exception {
        var process = ProcessHandle.of(pid);
        if (process.isPresent() && process.get().isAlive()) process.get().onExit().get(10, TimeUnit.SECONDS);
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "shell was left running: " + pid);
    }

    private static List<TerminalLine> lines(TerminalTextBuffer buffer) {
        var lines = new ArrayList<TerminalLine>();
        buffer.getHistoryLinesStorage().forEach(lines::add);
        buffer.getScreenLinesStorage().forEach(lines::add);
        return lines;
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
        final InteractionArea area;
        final TerminalPanel terminal;
        private Stage stage;

        private Fixture(Path root) {
            this(root, null);
        }

        private Fixture(Path root, Path executable) {
            area = new InteractionArea(root);
            if (executable == null) {
                terminal = (TerminalPanel) area.activeItem().content();
            } else {
                terminal = new TerminalPanel(root, executable);
                area.addItem(new InteractionItem<>("运行测试", terminal, terminal::activate, terminal::close));
            }
            Scene scene = new Scene(area, 1200, 360);
            UiStyles.install(scene);
            area.applyCss();
            area.resize(1200, 360);
            area.layout();
        }

        static Fixture create(Path root) throws Exception { return onFx(() -> new Fixture(root)); }

        static Fixture createRun(Path root, Path executable) throws Exception {
            return onFx(() -> new Fixture(root, executable));
        }

        void showStage() throws Exception {
            onFx(() -> {
                stage = new Stage(StageStyle.UNDECORATED);
                stage.setScene(area.getScene());
                stage.setX(-10000);
                stage.setY(-10000);
                stage.show();
                // 同步快照保证 peer 已创建、SwingNode 内部 scene 监听已注册。
                area.getScene().snapshot(null);
                return null;
            });
            onEdt(() -> null);
        }

        JediTermWidget widget() { return terminal.widget(); }

        void awaitReady() throws Exception {
            await(() -> onFx(() -> terminal.session() != null
                    && terminal.session().state() == PowerShellSession.State.RUNNING && widget() != null));
            resize(1050, 300);
            awaitText("PS ");
        }

        void resize(int width, int height) throws Exception {
            onEdt(() -> {
                widget().setSize(width, height);
                layoutChildren(widget());
                return null;
            });
        }

        private static void layoutChildren(Container container) {
            container.doLayout();
            for (var component : container.getComponents()) {
                if (component instanceof Container child) layoutChildren(child);
            }
        }

        void type(String text) throws Exception {
            onEdt(() -> {
                var panel = widget().getTerminalPanel();
                for (char character : text.toCharArray()) {
                    panel.processKeyEvent(new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                            KeyEvent.getExtendedKeyCodeForChar(character), character));
                    panel.processKeyEvent(new KeyEvent(panel, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
                            KeyEvent.VK_UNDEFINED, character));
                    panel.processKeyEvent(new KeyEvent(panel, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,
                            KeyEvent.getExtendedKeyCodeForChar(character), character));
                }
                return null;
            });
        }

        void press(int keyCode, int modifiers) throws Exception {
            onEdt(() -> {
                var panel = widget().getTerminalPanel();
                char character = controlCharacter(keyCode, modifiers);
                panel.processKeyEvent(new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers,
                        keyCode, character));
                if (character != KeyEvent.CHAR_UNDEFINED) {
                    panel.processKeyEvent(new KeyEvent(panel, KeyEvent.KEY_TYPED, System.currentTimeMillis(), modifiers,
                            KeyEvent.VK_UNDEFINED, character));
                }
                panel.processKeyEvent(new KeyEvent(panel, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), modifiers,
                        keyCode, character));
                return null;
            });
        }

        private static char controlCharacter(int keyCode, int modifiers) {
            // 与 AWT 的真实按键事件一致：Tab/Ctrl+C 的控制字符在 KEY_PRESSED 中发送。
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0
                    && keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
                return (char) (keyCode - KeyEvent.VK_A + 1);
            }
            return switch (keyCode) {
                case KeyEvent.VK_TAB -> '\t';
                case KeyEvent.VK_ENTER -> '\n';
                case KeyEvent.VK_BACK_SPACE -> '\b';
                case KeyEvent.VK_ESCAPE -> '\u001b';
                case KeyEvent.VK_DELETE -> '\u007f';
                default -> KeyEvent.CHAR_UNDEFINED;
            };
        }

        void enter(String text) throws Exception { type(text); press(KeyEvent.VK_ENTER, 0); }

        void fxPress(KeyCode code, boolean control) throws Exception {
            onFx(() -> {
                var surface = terminal.lookup("#terminal-surface");
                String text = code.isLetterKey() ? code.getChar().toLowerCase(java.util.Locale.ROOT) : "";
                Event.fireEvent(surface, new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, javafx.scene.input.KeyEvent.CHAR_UNDEFINED,
                        text, code, false, control, false, false));
                return null;
            });
        }

        String text() throws Exception {
            return onEdt(() -> {
                if (widget() == null) return "";
                TerminalTextBuffer buffer = widget().getTerminalTextBuffer();
                buffer.lock();
                try {
                    var result = new StringBuilder();
                    for (var line : lines(buffer)) {
                        // 去掉空格单元及宽字符第二列的内部占位符，不改变实际终端文字。
                        result.append(line.getText().replace("\0", "").replace("\uE000", ""));
                        if (!line.isWrapped()) result.append('\n');
                    }
                    return result.toString();
                } finally { buffer.unlock(); }
            });
        }

        void awaitText(String text) throws Exception { await(() -> text().contains(text)); }

        void awaitPrompt() throws Exception {
            await(() -> onEdt(() -> {
                var terminalWidget = widget();
                var buffer = terminalWidget.getTerminalTextBuffer();
                buffer.lock();
                try {
                    return buffer.getLine(terminalWidget.getTerminal().getCursorY() - 1).getText()
                            .replace("\uE000", "").replace("\0", "").stripTrailing().matches("PS .*>");
                } finally { buffer.unlock(); }
            }));
        }

        void awaitLine(String line) throws Exception {
            await(() -> text().lines().anyMatch(actual -> actual.stripTrailing().equals(line)));
        }

        long awaitNumber(String key) throws Exception {
            Pattern pattern = Pattern.compile("(?m)^" + Pattern.quote(key) + "=(\\d+)\\s*$");
            await(() -> pattern.matcher(text()).find());
            var matcher = pattern.matcher(text());
            assertTrue(matcher.find());
            return Long.parseLong(matcher.group(1));
        }

        void await(Callable<Boolean> condition) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
            while (!condition.call() && System.nanoTime() < deadline) Thread.sleep(25);
            assertTrue(condition.call(), "Timed out. Terminal output:\n" + text());
        }

        void snapshot(Path destination) throws Exception {
            Files.createDirectories(destination.toAbsolutePath().getParent());
            onEdt(() -> {
                var terminalWidget = widget();
                var image = new BufferedImage(terminalWidget.getWidth(), terminalWidget.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try { terminalWidget.printAll(graphics); }
                finally { graphics.dispose(); }
                ImageIO.write(image, "png", destination.toFile());
                return null;
            });
        }

        @Override public void close() throws Exception {
            PowerShellSession closingSession = onFx(() -> terminal.session());
            onFx(() -> {
                area.close();
                terminal.close();
                terminal.activate();
                terminal.restart();
                Button stop = (Button) terminal.lookup("#terminal-stop");
                assertTrue(stop.isDisabled());
                return null;
            });
            onEdt(() -> null);
            // 先排空 scene-detach，再隐藏 Stage，避免 FX 21 的延迟 WINDOW_HIDDEN 回调访问旧 host。
            onFx(() -> {
                if (stage != null) stage.close();
                return null;
            });
            onEdt(() -> null);
            onFx(() -> null);
            if (closingSession != null) {
                await(() -> closingSession.state() == PowerShellSession.State.CLOSED);
            }
        }
    }
}
