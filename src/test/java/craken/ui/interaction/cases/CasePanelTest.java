package craken.ui.interaction.cases;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Point3D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import craken.ui.component.UiStyles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** 用例面板的输入状态与真实程序运行；程序使用 Windows 批处理，不再触发一次完整编译。 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
@EnabledOnOs(OS.WINDOWS)
final class CasePanelTest {
    @TempDir Path temporary;

    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        };
        try {
            Platform.startup(ready);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(ready);
        }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void inputAreaIsReadOnlyUntilClickedAndEscapeEndsInputKeepingText() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(temporary.resolve("input.c"))) {
                TextArea input = ui.input();
                assertFalse(ui.panel.isEditingInput());
                assertFalse(input.isEditable());
                assertFalse(input.isFocusTraversable());

                click(input);
                assertTrue(ui.panel.isEditingInput());
                assertTrue(input.isEditable());
                assertTrue(input.isFocusTraversable());
                assertTrue(((Button) ui.panel.lookup("#case-edit-input")).isDisabled());
                assertEquals("输入中 · 按 Esc 结束输入", ui.hint().getText());

                input.selectAll();
                input.replaceSelection("12 30\n");
                Event.fireEvent(input, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
                assertFalse(ui.panel.isEditingInput(), "Esc must end the input state");
                assertFalse(input.isEditable());
                assertFalse(input.isFocusTraversable());
                assertEquals("12 30\n", ui.panel.inputText(), "leaving input state keeps the text");
                assertFalse(((Button) ui.panel.lookup("#case-edit-input")).isDisabled());

                ((Button) ui.panel.lookup("#case-edit-input")).fire();
                assertTrue(ui.panel.isEditingInput(), "the button re-enters the input state");
                Event.fireEvent(ui.panel, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
                assertFalse(ui.panel.isEditingInput(), "Esc reaches the input state from panel focus too");
                assertEquals("12 30\n", ui.panel.inputText());
            }
            return null;
        });
    }

    @Test
    void programInputAndExpectedResultShareTheInputStateAndSitSideBySide() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(temporary.resolve("halves.c"))) {
                TextArea input = ui.input();
                TextArea expected = ui.expected();
                ui.layout();
                assertTrue(input.getWidth() > 0 && expected.getWidth() > 0);
                assertTrue(input.localToScene(0, 0).getX() < expected.localToScene(0, 0).getX(),
                        "the program input must stay on the left of the expected result");
                assertFalse(expected.isEditable());
                assertFalse(expected.isFocusTraversable());

                click(expected);
                assertTrue(ui.panel.isEditingInput(), "clicking the expected result enters the input state");
                assertTrue(expected.isEditable());
                assertTrue(input.isEditable(), "both halves share the input state");

                expected.selectAll();
                expected.replaceSelection("EXPECTED\n");
                Event.fireEvent(expected, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
                assertFalse(ui.panel.isEditingInput());
                assertFalse(expected.isEditable());
                assertFalse(input.isEditable());
                assertEquals("EXPECTED\n", ui.panel.expectedText());
                assertEquals("", ui.panel.inputText());
            }
            return null;
        });
    }

    @Test
    void verdictComparesTheOutputWithTheExpectedResult() throws Exception {
        Path program = echoProgram("fixed.bat", "@echo off\r\necho EXPECTED\r\n");
        Fixture ui = onFx(() -> new Fixture(temporary.resolve("verdict.c")));
        try {
            // 行尾差异不影响判定：程序输出 CRLF，预期只有一行文本。
            onFx(() -> {
                ui.panel.setInputText("");
                ui.panel.setExpectedText("EXPECTED");
                int generation = ui.panel.beginRun();
                assertEquals(CasePanel.Verdict.NONE, ui.panel.verdict(), "a new run clears the verdict");
                assertEquals("", ui.verdict().getText());
                ui.panel.run(generation, temporary, program, ui.panel.inputText());
                return null;
            });
            awaitFx(() -> ui.panel.verdict() == CasePanel.Verdict.PASSED);
            onFx(() -> {
                assertEquals("通过", ui.verdict().getText());
                assertTrue(ui.verdict().getStyleClass().contains("case-verdict-passed"));
                return null;
            });

            onFx(() -> {
                ui.panel.setExpectedText("OTHER");
                ui.panel.run(ui.panel.beginRun(), temporary, program, ui.panel.inputText());
                return null;
            });
            awaitFx(() -> ui.panel.verdict() == CasePanel.Verdict.FAILED);
            onFx(() -> {
                assertEquals("不匹配", ui.verdict().getText());
                assertTrue(ui.verdict().getStyleClass().contains("case-verdict-failed"));
                return null;
            });

            // 预期为空（或只有空白）时不判定，列表保持中性。
            onFx(() -> {
                ui.panel.setExpectedText("   ");
                ui.panel.run(ui.panel.beginRun(), temporary, program, ui.panel.inputText());
                return null;
            });
            awaitFx(() -> ui.panel.statusText().startsWith("已退出"));
            assertEquals(CasePanel.Verdict.NONE, onFx(ui.panel::verdict));
            assertEquals("", onFx(() -> ui.verdict().getText()));

            // 停止/取消这一轮不会留下上一轮的判定颜色。
            onFx(() -> {
                ui.panel.setExpectedText("EXPECTED");
                ui.panel.beginRun();
                assertEquals(CasePanel.Verdict.NONE, ui.panel.verdict());
                return null;
            });
        } finally {
            onFx(() -> { ui.close(); return null; });
        }
    }

    @Test
    void inputSitsAboveOutputWithItsOwnSectionHeader() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(temporary.resolve("layout.c"))) {
                ui.layout();
                TextArea input = ui.input();
                TextArea output = ui.output();
                assertTrue(input.getHeight() > 0);
                assertTrue(output.getHeight() > 0);
                assertTrue(input.localToScene(0, 0).getY() < output.localToScene(0, 0).getY(),
                        "the input section must stay above the output section");
            }
            return null;
        });
    }

    @Test
    void runFeedsThePreWrittenInputAndKeepsStandardInputOpenUntilEofIsSent() throws Exception {
        Path program = twoLineProgram("two-lines.bat");
        Fixture ui = onFx(() -> new Fixture(temporary.resolve("two-lines.c")));
        try {
            onFx(() -> {
                ui.panel.setInputText("first-case\n");
                int generation = ui.panel.beginRun();
                assertEquals("等待编译…", ui.panel.statusText());
                assertEquals("", ui.panel.outputText(), "a new run clears the previous output");
                assertTrue(ui.sendEof().isDisabled(), "EOF is only meaningful while a case runs");
                ui.panel.run(generation, temporary, program, ui.panel.inputText());
                return null;
            });
            awaitFx(() -> ui.panel.outputText().contains("FIRST=first-case"));
            onFx(() -> {
                // 没有发送 EOF：程序仍在等第二行，不会读到 EOF 提前收尾。
                assertEquals("运行中…", ui.panel.statusText());
                assertFalse(ui.panel.outputText().contains("SECOND="), ui.panel.outputText());
                assertFalse(ui.sendEof().isDisabled());
                ui.sendEof().fire();
                return null;
            });
            awaitFx(() -> ui.panel.statusText().startsWith("已退出"));
            assertEquals("已退出（退出码 0）", onFx(ui.panel::statusText));
            String output = onFx(ui.panel::outputText);
            assertTrue(output.contains("FIRST=first-case"), output);
            assertTrue(output.contains("SECOND="), output);
            onFx(() -> {
                assertTrue(ui.sendEof().isDisabled(), "EOF becomes unavailable once the case exited");
                // 第二轮替换预置输入并清空上一轮输出。
                ui.panel.setInputText("second-case\n");
                int generation = ui.panel.beginRun();
                assertEquals("", ui.panel.outputText());
                ui.panel.run(generation, temporary, program, ui.panel.inputText());
                return null;
            });
            awaitFx(() -> ui.panel.outputText().contains("FIRST=second-case"));
            String second = onFx(ui.panel::outputText);
            assertFalse(second.contains("first-case"), second);
        } finally {
            onFx(() -> { ui.close(); return null; });
        }
    }

    @Test
    void stopEndsTheCaseProgramWithoutClosingThePanel() throws Exception {
        Path program = echoProgram("slow.bat", "@echo off\r\nping -n 40 127.0.0.1 > nul\r\n");
        CasePanel panel = onFx(() -> new CasePanel(temporary.resolve("slow.c")));
        try {
            onFx(() -> {
                int generation = panel.beginRun();
                panel.run(generation, temporary, program, "");
                return null;
            });
            awaitFx(panel::isRunning);
            onFx(() -> {
                assertFalse(((Button) panel.lookup("#case-stop")).isDisabled());
                panel.stopRun();
                return null;
            });
            assertEquals("已停止", onFx(panel::statusText));
            assertFalse(onFx(panel::isRunning));
            onFx(() -> {
                assertTrue(((Button) panel.lookup("#case-stop")).isDisabled());
                panel.enterInputState();
                assertTrue(panel.isEditingInput(), "stopping a case must not close its panel");
                return null;
            });
        } finally {
            onFx(() -> { panel.close(); return null; });
        }
    }

    @Test
    void cancelNotifiesOnlyTheMatchingAwaitingRun() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(temporary.resolve("cancel.c"))) {
                int first = ui.panel.beginRun();
                ui.panel.cancelRun(first);
                assertEquals("已取消，未运行", ui.panel.statusText());

                int second = ui.panel.beginRun();
                ui.panel.cancelRun(first);
                assertEquals("等待编译…", ui.panel.statusText(), "an old generation must not cancel the new run");
                ui.panel.notRun(second, "未运行（编译失败）");
                assertEquals("未运行（编译失败）", ui.panel.statusText());
                ui.panel.cancelRun(second);
                assertEquals("未运行（编译失败）", ui.panel.statusText(), "finished runs keep their own result");
            }
            return null;
        });
    }

    @Test
    void deleteButtonAsksTheHostToRemoveTheCase() throws Exception {
        onFx(() -> {
            var requested = new AtomicInteger();
            CasePanel panel = new CasePanel(temporary.resolve("delete.c"));
            panel.setOnDeleteRequest(requested::incrementAndGet);
            new Scene(panel, 720, 240);
            panel.beginRun();
            Button delete = (Button) panel.lookup("#case-delete");
            assertFalse(delete.isDisabled());
            delete.fire();
            assertEquals(1, requested.get());
            assertFalse(panel.isClosed(), "the panel only asks; the container removes the tag");

            panel.close();
            assertTrue(delete.isDisabled(), "a closed case cannot be deleted again");
            panel.requestDelete();
            assertEquals(1, requested.get());
            return null;
        });
    }

    /** 读一行再读第二行的最小程序：第二行没有输入时不发送 EOF 就会一直等待。 */
    private Path twoLineProgram(String name) throws Exception {
        return Files.writeString(temporary.resolve(name),
                "@echo off\r\nset /p first=\r\necho FIRST=%first%\r\nset /p second=\r\necho SECOND=%second%\r\n",
                StandardCharsets.US_ASCII);
    }

    private Path echoProgram(String name, String content) throws Exception {
        return Files.writeString(temporary.resolve(name), content, StandardCharsets.US_ASCII);
    }

    private static void click(Node node) {
        var point = node.localToScene(6, 6);
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_PRESSED,
                point.getX(), point.getY(), point.getX(), point.getY(), MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true,
                new PickResult(node, new Point3D(6, 6, 0), 1)));
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_RELEASED,
                point.getX(), point.getY(), point.getX(), point.getY(), MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, false, false, false, true,
                new PickResult(node, new Point3D(6, 6, 0), 1)));
    }

    private static void awaitFx(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (true) {
            if (onFx(condition::getAsBoolean)) return;
            if (System.nanoTime() > deadline) fail("condition not reached in time");
            Thread.sleep(20);
        }
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    /** 面板挂在一个不显示的场景里，只验证布局、状态与真实进程行为。 */
    private static final class Fixture implements AutoCloseable {
        final CasePanel panel;

        Fixture(Path sourcePath) {
            panel = new CasePanel(sourcePath);
            Scene scene = new Scene(panel, 720, 240);
            UiStyles.install(scene);
            layout();
        }

        void layout() {
            panel.applyCss();
            panel.resize(720, 240);
            panel.layout();
        }

        TextArea input() { return (TextArea) panel.lookup("#case-input"); }

        TextArea expected() { return (TextArea) panel.lookup("#case-expected"); }

        TextArea output() { return (TextArea) panel.lookup("#case-output"); }

        javafx.scene.control.Label hint() { return (javafx.scene.control.Label) panel.lookup("#case-input-hint"); }

        Button sendEof() { return (Button) panel.lookup("#case-send-eof"); }

        javafx.scene.control.Label verdict() { return (javafx.scene.control.Label) panel.lookup("#case-verdict"); }

        @Override
        public void close() {
            panel.close();
        }
    }
}
