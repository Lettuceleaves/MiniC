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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import craken.ui.component.UiStyles;
import craken.ui.editor.EditorArea;
import craken.ui.frame.AppFrame;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.InteractionItem;
import craken.ui.interaction.cases.CasePanel;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实编辑器快照、编译器、可执行产物与 ConPTY：验证运行一次会并行执行绑定到该源文件的
 * 全部用例，用各自预置的输入驱动独立程序，输出留在用例标签里。
 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
@EnabledOnOs(OS.WINDOWS)
final class RunCaseWorkflowTest {
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
    void runExecutesEveryCaseInParallelWithTheForegroundIoAndReplacesOutputsNextTime() throws Exception {
        Path sourcePath = Files.writeString(temporary.resolve("sum.c"), """
                #include <stdio.h>
                int main(void) {
                    printf("READY\\n");
                    int left = 0;
                    int right = 0;
                    if (scanf("%d %d", &left, &right) != 2) return 3;
                    printf("SUM=%d\\n", left + right);
                    return 0;
                }
                """);
        try (Fixture ui = onFx(() -> new Fixture(temporary))) {
            onFx(() -> {
                ui.editors.openFile(sourcePath);
                Button newCase = (Button) ui.frame.lookup("#interaction-new-case");
                newCase.fire();
                newCase.fire();
                List<CasePanel> cases = ui.casePanels();
                assertEquals(List.of("CASE 1", "CASE 2"), ui.caseTitles());
                assertTrue(cases.getFirst().isEditingInput(), "a new case starts in the input state");
                cases.getFirst().setInputText("1 2\n");
                Event.fireEvent(cases.getFirst(), new KeyEvent(
                        KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
                assertFalse(cases.getFirst().isEditingInput(), "Esc ends the input state");
                assertEquals("1 2\n", cases.getFirst().inputText(), "leaving the input state keeps the text");
                cases.get(1).setInputText("20 22\n");
                cases.getFirst().setExpectedText("READY\nSUM=3");
                cases.get(1).setExpectedText("READY\nSUM=99");
                ((Button) ui.frame.lookup("#app-run-button")).fire();
                ui.run = ui.runPanel();
                return null;
            });
            ui.awaitTerminal();
            ui.await(() -> onFx(() -> ui.casePanels().stream()
                    .allMatch(panel -> panel.statusText().startsWith("已退出"))));
            onFx(() -> {
                List<CasePanel> cases = ui.casePanels();
                assertTrue(cases.getFirst().outputText().contains("SUM=3"), cases.getFirst().outputText());
                assertFalse(cases.getFirst().outputText().contains("SUM=42"), cases.getFirst().outputText());
                assertTrue(cases.get(1).outputText().contains("SUM=42"), cases.get(1).outputText());
                assertEquals("已退出（退出码 0）", cases.getFirst().statusText());
                assertFalse(cases.getFirst().isRunning());
                assertEquals(InteractionItem.Result.PASSED, ui.caseItems().getFirst().result(),
                        "matching output colours the case tab green");
                assertEquals(InteractionItem.Result.FAILED, ui.caseItems().get(1).result(),
                        "mismatching output colours the case tab red");
                return null;
            });
            // 前台程序还在等待输入，用例已经跑完：两者是并行的独立进程。
            ui.await(() -> ui.text().contains("READY"));
            assertFalse(onFx(() -> ui.terminal.isFinished()), "the foreground program must still be running");
            ui.feedTerminal("9 9\r");
            ui.await(() -> onFx(() -> ui.terminal.isFinished() && Integer.valueOf(0).equals(ui.terminal.exitCode())));

            // 下一次运行替换每个用例的输出，并继续复用同样的标签。
            onFx(() -> {
                ui.casePanels().getFirst().setInputText("3 4\n");
                ui.casePanels().get(1).setInputText("5 6\n");
                ui.casePanels().getFirst().setExpectedText("READY\nSUM=7");
                ui.casePanels().get(1).setExpectedText("READY\nSUM=11");
                ((Button) ui.frame.lookup("#app-run-button")).fire();
                ui.run = ui.runPanel();
                return null;
            });
            ui.awaitTerminal();
            ui.await(() -> onFx(() -> {
                List<CasePanel> cases = ui.casePanels();
                return cases.size() == 2
                        && cases.getFirst().outputText().contains("SUM=7")
                        && cases.get(1).outputText().contains("SUM=11");
            }));
            onFx(() -> {
                List<CasePanel> cases = ui.casePanels();
                assertFalse(cases.getFirst().outputText().contains("SUM=3"), "a rerun replaces the output");
                assertEquals("已退出（退出码 0）", cases.get(1).statusText());
                assertEquals(InteractionItem.Result.PASSED, ui.caseItems().getFirst().result());
                assertEquals(InteractionItem.Result.PASSED, ui.caseItems().get(1).result());
                return null;
            });
            ui.feedTerminal("1 2\r");
            ui.await(() -> onFx(() -> ui.terminal.isFinished() && Integer.valueOf(0).equals(ui.terminal.exitCode())));
        }
    }

    @Test
    void closingTheRunPanelCancelsCasesThatAreStillRunning() throws Exception {
        Path sourcePath = Files.writeString(temporary.resolve("spin.c"), """
                int main(void) { while (1) { } return 0; }
                """);
        try (Fixture ui = onFx(() -> new Fixture(temporary))) {
            onFx(() -> {
                ui.editors.openFile(sourcePath);
                ((Button) ui.frame.lookup("#interaction-new-case")).fire();
                ((Button) ui.frame.lookup("#app-run-button")).fire();
                ui.run = ui.runPanel();
                return null;
            });
            ui.awaitTerminal();
            ui.await(() -> onFx(() -> {
                CasePanel panel = ui.casePanels().getFirst();
                return panel.isRunning() && "运行中…".equals(panel.statusText());
            }));
            Path executable = onFx(() -> ui.run.artifact().path());
            onFx(() -> {
                assertTrue(ui.interactions.closeItem(ui.interactions.activeItem()));
                return null;
            });
            onFx(() -> {
                CasePanel panel = ui.casePanels().getFirst();
                assertEquals("已取消", panel.statusText(), "closing the run must end its running cases");
                assertFalse(panel.isRunning());
                assertFalse(((Button) ui.frame.lookup("#app-run-button")).isDisabled());
                return null;
            });
            awaitProgramReleased(executable);
        }
    }

    @Test
    void deletingARunningCaseTabStopsItsProgramAndRemovesTheTag() throws Exception {
        Path sourcePath = Files.writeString(temporary.resolve("delete-running.c"), """
                int main(void) { while (1) { } return 0; }
                """);
        try (Fixture ui = onFx(() -> new Fixture(temporary))) {
            onFx(() -> {
                ui.editors.openFile(sourcePath);
                ((Button) ui.frame.lookup("#interaction-new-case")).fire();
                ((Button) ui.frame.lookup("#app-run-button")).fire();
                ui.run = ui.runPanel();
                return null;
            });
            ui.awaitTerminal();
            ui.await(() -> onFx(() -> ui.casePanels().getFirst().isRunning()));
            Path executable = onFx(() -> ui.run.artifact().path());
            onFx(() -> {
                CasePanel panel = ui.casePanels().getFirst();
                ((Button) panel.lookup("#case-delete")).fire();
                assertTrue(panel.isClosed(), "deleting the tag closes the case");
                assertTrue(ui.casePanels().isEmpty(), "the deleted tag leaves the interaction list");
                return null;
            });
            onFx(() -> {
                // 前台的同一个程序仍在死循环；关闭这次运行结束它，再确认产物不再被占用。
                assertTrue(ui.interactions.closeItem(ui.interactions.activeItem()));
                return null;
            });
            awaitProgramReleased(executable);
        }
    }

    @Test
    void caseInputIsTypedLikeATerminalAndEndsOnlyAfterExplicitEof() throws Exception {
        Path sourcePath = Files.writeString(temporary.resolve("wait-input.c"), """
                #include <stdio.h>
                int main(void) {
                    int value = 0;
                    if (scanf("%d", &value) != 1) { printf("READ_FAILED\\n"); return 1; }
                    printf("GOT=%d\\n", value);
                    fflush(stdout);
                    int next = getchar();
                    while (next != EOF) next = getchar();
                    printf("SAW_EOF\\n");
                    return 2;
                }
                """);
        try (Fixture ui = onFx(() -> new Fixture(temporary))) {
            onFx(() -> {
                ui.editors.openFile(sourcePath);
                ((Button) ui.frame.lookup("#interaction-new-case")).fire();
                // 最后一行没有换行：按终端里“敲完一行按回车”补上，程序才能读到完整的数字。
                ui.casePanels().getFirst().setInputText("42");
                ((Button) ui.frame.lookup("#app-run-button")).fire();
                ui.run = ui.runPanel();
                return null;
            });
            ui.awaitTerminal();
            ui.await(() -> onFx(() -> ui.casePanels().getFirst().outputText().contains("GOT=42")));
            ui.await(() -> onFx(() -> "运行中…".equals(ui.casePanels().getFirst().statusText())));
            Thread.sleep(300);
            onFx(() -> {
                CasePanel panel = ui.casePanels().getFirst();
                assertFalse(panel.outputText().contains("READ_FAILED"), panel.outputText());
                assertEquals("运行中…", panel.statusText(),
                        "without EOF the program keeps waiting for the rest of the input");
                assertFalse(panel.outputText().contains("SAW_EOF"), "EOF must never be sent automatically");
                // 显式结束输入：等价于终端里按 Ctrl+Z 回车，程序读到 EOF 后正常退出。
                panel.sendEof();
                return null;
            });
            ui.await(() -> onFx(() -> "已退出（退出码 2）".equals(ui.casePanels().getFirst().statusText())));
            assertTrue(onFx(() -> ui.casePanels().getFirst().outputText().contains("SAW_EOF")));
            Path executable = onFx(() -> ui.run.artifact().path());
            onFx(() -> {
                // 前台的同一程序仍停在 scanf；关闭这次运行结束它，再确认产物不再被占用。
                assertTrue(ui.interactions.closeItem(ui.interactions.activeItem()));
                return null;
            });
            awaitProgramReleased(executable);
        }
    }

    /** 轮询到可执行产物不再被进程占用：跑飞的用例和前台程序都必须真的退出。 */
    private static void awaitProgramReleased(Path executable) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            try {
                if (Files.deleteIfExists(executable)) return;
            } catch (java.io.IOException stillRunning) {
                // 程序仍占用可执行文件；继续等待。
            }
            Thread.sleep(50);
        }
        fail("a case program was left running after closing the run panel");
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
            editors.setCloseDecisionHandler(file -> EditorArea.CloseChoice.DISCARD);
            interactions = new InteractionArea(root);
            interactions.closeItem(interactions.activeItem());
            frame = new AppFrame(editors, new Region(), interactions, editors.tabBar());
            controller = new RunController(editors, frame, interactions);
            scene = new Scene(frame, 1280, 800);
            UiStyles.install(scene);
            stage.setScene(scene);
            layout();
        }

        void layout() {
            frame.applyCss();
            frame.resize(1280, 800);
            frame.layout();
        }

        RunPanel runPanel() {
            InputOutputTab tab = assertInstanceOf(InputOutputTab.class, interactions.activeItem().content());
            return assertInstanceOf(RunPanel.class, tab.channel().node());
        }

        List<CasePanel> casePanels() {
            List<CasePanel> panels = new ArrayList<>();
            for (InteractionItem<?> item : interactions.items()) {
                if (item.content() instanceof CasePanel panel) panels.add(panel);
            }
            return panels;
        }

        List<InteractionItem<CasePanel>> caseItems() {
            List<InteractionItem<CasePanel>> items = new ArrayList<>();
            for (InteractionItem<?> item : interactions.items()) {
                if (item.content() instanceof CasePanel) {
                    @SuppressWarnings("unchecked")
                    InteractionItem<CasePanel> typed = (InteractionItem<CasePanel>) item;
                    items.add(typed);
                }
            }
            return items;
        }

        List<String> caseTitles() {
            return interactions.items().stream().map(InteractionItem::title)
                    .filter(title -> title.startsWith("CASE ")).toList();
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

        /** 直接通过终端连接器写入一行，让仍在等待输入的前台程序正常退出。 */
        void feedTerminal(String line) throws Exception {
            // 终端会话在进程启动回调之后才建立；连接器可能短暂为空。
            await(() -> onEdt(() -> widget.getTtyConnector() != null));
            onEdt(() -> { widget.getTtyConnector().write(line); return null; });
        }

        void await(Callable<Boolean> condition) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                if (condition.call()) return;
                Thread.sleep(25);
            }
            String diagnostics = onFx(() -> {
                StringBuilder report = new StringBuilder("cases:\n");
                for (CasePanel panel : casePanels()) {
                    report.append(panel.statusText()).append(" | ").append(panel.outputText()).append('\n');
                }
                if (run != null && run.lookup("#run-diagnostics") instanceof javafx.scene.control.TextInputControl input) {
                    report.append("compile diagnostics:\n").append(input.getText()).append('\n');
                }
                return report.toString();
            });
            fail("Timed out. " + diagnostics + "Terminal output:\n" + text());
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
