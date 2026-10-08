package craken.ui.debug;

import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import craken.debug.DebugVariable;
import craken.ui.component.UiStyles;
import craken.ui.component.terminal.UiTerminalWidget;
import craken.ui.display.DisplayArea;
import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import craken.ui.frame.AppFrame;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.InteractionItem;
import craken.ui.interaction.inputoutput.InputOutputTab;
import craken.ui.interaction.inputoutput.InputOutputTabs;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.text.BadLocationException;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 调试工作台：半屏布局、开始前的捕获变量检索、开始后的插桩可视化与栈区。
 */
@Tag("visualization-adapter")
final class DebugWorkbenchTest {
    private static final String SOURCE = """
            #include "stdlib.mh"
            int counter;
            int add(int a, int b) {
                int sum = a + b;
                return sum;
            }
            int main(void) {
                counter = counter + 1;
                int total = add(2, 3);
                total = total + counter;
                return total;
            }
            """;
    private static final String DUPLICATE = """
            int first(void) { int value = 1; return value; }
            int second(void) { int value = 2; return value; }
            int main(void) { return first() + second(); }
            """;
    /** 指针变量捕获：g 指向一块堆分配的结构体。 */
    private static final String POINTER = """
            #include "stdlib.mh"
            typedef struct { int n; } Graph;
            int main(void) {
                Graph *g = (Graph *)malloc(sizeof(Graph));
                g->n = 5;
                return g->n;
            }
            """;
    /** 指针链捕获：head 指向两个由 next 相连的堆节点。 */
    private static final String LINKED_LIST = """
            #include "stdlib.mh"
            typedef struct Node { int data; struct Node *next; } Node;
            int main(void) {
                Node *head = (Node *)malloc(sizeof(Node));
                head->data = 10;
                head->next = (Node *)malloc(sizeof(Node));
                head->next->data = 20;
                head->next->next = 0;
                return head->data;
            }
            """;
    /** 标准输出与标准错误分别写入调试 IO 项的画面。 */
    private static final String PROGRAM_IO = """
            #include "stdio.mh"
            int main(void) {
                char *message = "DEBUG_IO_STDERR\\n";
                int index = 0;
                printf("DEBUG_IO_STDOUT\\n");
                while (message[index] != 0) {
                    fputc(message[index], stderr);
                    index = index + 1;
                }
                return 0;
            }
            """;
    /** 标准输入在“开始”之后、读取语句执行之前由 IO 项排队。 */
    private static final String PROGRAM_INPUT = """
            #include "stdio.mh"
            int main(void) {
                int value = getchar();
                printf("DEBUG_IO_INPUT=%d\\n", value);
                return 0;
            }
            """;
    @TempDir Path directory;

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException alreadyStarted) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test void noFileDisablesTheDebugIcon() throws Exception {
        try (Fixture ui = fixture(null)) {
            onFx(() -> {
                assertTrue(ui.icon().isDisabled());
                ui.icon().fire();
                assertNull(ui.frame.lookup("#debug-panel"));
                return null;
            });
        }
    }

    @Test void openingTheWorkbenchUsesAHalfScreenSplit() throws Exception {
        try (Fixture ui = fixture(SOURCE)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            assertTrue(onFx(() -> ui.panel() != null));
            double divider = ui.awaitDividerPosition();
            assertEquals(0.5, divider, 0.02, "右侧信息栏与中心编辑区等宽");
            onFx(() -> {
                assertEquals(0.62, ui.split().getDividerPositions()[0], 0.02, "左半部分更宽");
                assertEquals(0.8, ui.memorySplit().getDividerPositions()[0], 0.02, "栈区占上 80%");
                return null;
            });
        }
    }

    @Test void typingSearchesTheIrAndUniqueMatchesJoinTheList() throws Exception {
        try (Fixture ui = fixture(SOURCE)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                ui.captureInput().setText("counter");
                assertTrue(ui.captureCandidates().getItems().isEmpty(), "输入后先等 300ms 防抖窗口");
                return null;
            });
            ui.awaitCaptureSearch("counter");
            onFx(() -> {
                assertEquals(1, ui.captureCandidates().getItems().size(), "唯一命中直接出现在下拉列表");
                assertEquals("counter", ui.captureCandidates().getItems().getFirst().sourceName());
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                assertEquals(1, ui.captureList().getItems().size());
                return null;
            });
            // 清空输入与候选推迟到下一帧，避免打断 ComboBox 自己的选中事件链。
            onFx(() -> {
                assertTrue(ui.captureInput().getText().isEmpty(), "选择后清空输入");
                assertTrue(ui.captureCandidates().getItems().isEmpty(), "选择后候选收起");
                return null;
            });
        }
    }

    @Test void duplicateVariableNamesAreChosenFromTheDropdownWithKeyboard() throws Exception {
        try (Fixture ui = fixture(DUPLICATE)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            ui.awaitCaptureSearch("value");
            onFx(() -> {
                assertEquals(2, ui.captureCandidates().getItems().size());
                assertEquals(List.of("first", "second"), ui.captureCandidates().getItems().stream()
                        .map(DebugVariable::function).toList());
                ui.captureInput().fireEvent(key(KeyCode.DOWN));
                ui.captureInput().fireEvent(key(KeyCode.DOWN));
                assertEquals(1, ui.captureCandidates().getSelectionModel().getSelectedIndex(),
                        "方向键在候选间移动");
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                assertEquals(1, ui.captureList().getItems().size());
                assertEquals("second", ui.captureList().getItems().getFirst().variable().function(),
                        "回车选中下拉里高亮的定义");
                return null;
            });
            onFx(() -> {
                assertTrue(ui.captureInput().getText().isEmpty(), "选择后清空输入");
                assertTrue(ui.captureCandidates().getItems().isEmpty(), "选择后收起候选");
                return null;
            });
        }
    }

    @Test void captureListShowsDeleteOnHoverAndRemovesTheItem() throws Exception {
        try (Fixture ui = fixture(SOURCE)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                ui.captureInput().setText("counter");
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                ui.panel().hoverCaptureCell(0, true);
                var delete = ui.captureList().lookupAll(".debug-capture-delete").stream()
                        .filter(javafx.scene.Node::isVisible).findFirst().orElseThrow();
                ((Button) delete).fire();
                assertTrue(ui.captureList().getItems().isEmpty());
                return null;
            });
        }
    }

    @Test void startProjectsCapturedVariablesAndFillsTheStackArea() throws Exception {
        try (Fixture ui = fixture(SOURCE)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                ui.captureInput().setText("counter");
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                ui.start().fire();
                return null;
            });
            ui.awaitWorker();
            onFx(() -> {
                assertTrue(ui.host().isVisible());
                var model = ui.host().displayModel();
                assertNotNull(model, "开始后必须发布一帧捕获画面");
                assertTrue(model.pages().values().stream().flatMap(page -> page.nodes().values().stream())
                                .anyMatch(node -> "counter".equals(node.content().label())),
                        "选中的变量出现在画布上");
                assertFalse(ui.stack().getItems().isEmpty(), "栈区显示名称与值");
                assertTrue(ui.previous().isDisabled(), "起点没有上一步");
                assertFalse(ui.next().isDisabled());
                assertFalse(ui.restartButton().isDisabled());
                assertFalse(ui.stepInto().isDisabled());
                assertTrue(ui.heap().getItems().isEmpty(), "堆区暂未实现");
                ui.runToEnd().fire();
                return null;
            });
            ui.awaitWorker();
            onFx(() -> {
                assertTrue(ui.status().getText().contains("COMPLETED"), ui.status().getText());
                var model = ui.host().displayModel();
                assertTrue(model.pages().values().stream().flatMap(page -> page.nodes().values().stream())
                                .anyMatch(node -> "1".equals(node.content().fields().get("value"))),
                        "捕获变量的值随停止点刷新");
                return null;
            });
        }
    }

    @Test void breakpointCommandsWalkForwardAndBackward() throws Exception {
        try (Fixture ui = fixture(SOURCE)) {
            int first = lineOf("counter = counter + 1;");
            int second = lineOf("return total;");
            onFx(() -> { ui.file().editor().setBreakpoint(first, true); return null; });
            onFx(() -> { ui.file().editor().setBreakpoint(second, true); return null; });
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                ui.captureInput().setText("total");
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                ui.start().fire();
                return null;
            });
            ui.awaitWorker();
            onFx(() -> { ui.nextBreakpoint().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                assertTrue(ui.status().getText().contains("BREAKPOINT"), ui.status().getText());
                assertTrue(ui.status().getText().contains("行 " + first), ui.status().getText());
                ui.nextBreakpoint().fire();
                return null;
            });
            ui.awaitWorker();
            onFx(() -> {
                assertTrue(ui.status().getText().contains("行 " + second), ui.status().getText());
                ui.previousBreakpoint().fire();
                return null;
            });
            ui.awaitWorker();
            onFx(() -> {
                assertTrue(ui.status().getText().contains("行 " + first), ui.status().getText());
                ui.previous().fire();
                return null;
            });
            ui.awaitWorker();
            onFx(() -> {
                assertTrue(ui.previousBreakpoint().isDisabled(), "第一个断点之前没有可返回的断点");
                return null;
            });
        }
    }

    @Test void currentLineIsHighlightedInTheDebuggedEditor() throws Exception {
        try (Fixture ui = fixture(SOURCE)) {
            int stop = lineOf("counter = counter + 1;");
            onFx(() -> { ui.file().editor().setBreakpoint(stop, true); return null; });
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> { ui.start().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> { ui.nextBreakpoint().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                assertTrue(ui.status().getText().contains("行 " + stop), ui.status().getText());
                return null;
            });
            assertEquals(List.of(stop), highlightedLines(ui.file()),
                    "暂停的当前行通过编辑器高亮 API 标记");

            onFx(() -> { ui.runToEnd().fire(); return null; });
            ui.awaitWorker();
            assertTrue(highlightedLines(ui.file()).isEmpty(), "运行到结束后清除当前行高亮");
        }
    }

    @Test void clickingAnArrayEntryOpensTheNextLayerPage() throws Exception {
        try (Fixture ui = fixture("""
                int main(void) {
                    int a[2][2];
                    a[0][0] = 5;
                    return a[0][0];
                }
                """)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                ui.captureInput().setText("a");
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                ui.start().fire();
                return null;
            });
            ui.awaitWorker();
            ui.awaitLayout();
            onFx(() -> {
                assertEquals(1, ui.host().visibleOccurrences().size(), "初始只显示第一层");
                var entry = ui.host().displayModel().pages().values().stream()
                        .flatMap(page -> page.nodes().values().stream())
                        .filter(node -> "a[0]".equals(node.content().label())
                                && node.location().pageId() == ui.host().displayModel().root().pageId())
                        .findFirst().orElseThrow();
                var pane = ui.host().visibleOccurrences().getFirst().nodeViews().get(entry.location());
                assertNotNull(pane, "入口卡片已渲染");
                pane.getOnMouseClicked().handle(new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                        javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true,
                        false, false, null));
                return null;
            });
            // 点击入口把展开交给后台命令线程，等它发布新快照后再判断可见层。
            ui.awaitWorker();
            ui.awaitLayout();
            onFx(() -> {
                var opened = ui.host().visibleOccurrences().getLast();
                assertTrue(opened.occurrence().page().pageId() > 1,
                        "点击入口后当前页进入下一层页面，实际页 #" + opened.occurrence().page().pageId());
                return null;
            });
        }
    }

    @Test void elementWriteFocusesTheDeepestLayerSlot() throws Exception {
        try (Fixture ui = fixture("""
                int main(void) {
                    int a[2][2];
                    a[0][0] = 5;
                    return a[0][0];
                }
                """)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                ui.captureInput().setText("a");
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                ui.start().fire();
                return null;
            });
            ui.awaitWorker();
            for (int step = 0; step < 12; step++) {
                boolean ready = onFx(() -> ui.status().getText().contains("行 4"));
                if (ready) break;
                onFx(() -> { ui.stepInto().fire(); return null; });
                ui.awaitWorker();
            }
            onFx(() -> {
                var model = ui.host().displayModel();
                assertTrue(model.pages().values().stream().flatMap(page -> page.nodes().values().stream())
                        .anyMatch(node -> "5".equals(node.content().label())), "赋值的元素显示 5");
                var accessed = model.interaction().accessed();
                assertNotNull(accessed, "有访问焦点");
                assertEquals("5", model.node(accessed).content().label(),
                        "焦点落在被写入的元素槽（第三层），而不是最高层");
                assertTrue(accessed.page().pageId() > 1, "焦点页不是第一层");
                return null;
            });
        }
    }

    /** 用户报告的场景：捕获 Graph *g 后必须能在停止点展开第二层，点击 g 打开该层。 */
    @Test void capturedPointerVariableExpandsAndClickingOpensTheSecondLayer() throws Exception {
        try (Fixture ui = fixture(POINTER)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                ui.captureInput().setText("g");
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                assertEquals(1, ui.captureList().getItems().size(), "回车选中第一个候选");
                ui.start().fire();
                return null;
            });
            ui.awaitWorker();
            ui.awaitLayout();
            for (int step = 0; step < 20; step++) {
                boolean expanded = onFx(() -> ui.host().displayModel() != null
                        && ui.host().displayModel().pages().values().stream()
                                .filter(page -> page.type().key()
                                        .equals(craken.debug.visualization.DebugCaptureProjector.GRAPH_PAGE_TYPE))
                                .flatMap(page -> page.nodes().values().stream())
                                .anyMatch(node -> "5".equals(node.content().fields().get("n"))));
                if (expanded) break;
                onFx(() -> { ui.stepInto().fire(); return null; });
                ui.awaitWorker();
                ui.awaitLayout();
            }
            onFx(() -> {
                var model = ui.host().displayModel();
                assertTrue(model.pages().size() > 1, "停止点必须为指针目标建立第二层页面");
                var root = model.pages().get(model.root().pageId());
                var card = root.nodes().values().stream()
                        .filter(node -> "g".equals(node.content().label())).findFirst().orElseThrow();
                String value = root.nodes().get(card.location().nodeId()).content().fields().get("value");
                assertTrue(value.startsWith("0x"), "g 卡片显示指针值，实际 " + value);
                assertTrue(model.ownership().values().stream().anyMatch(binding ->
                                binding.key().pre().equals(card.location())
                                        && binding.sources().stream().anyMatch(source -> source.startsWith("debug:slot:"))),
                        "指针卡片必须带下一层入口");
                var pane = ui.host().visibleOccurrences().getFirst().nodeViews().get(card.location());
                assertNotNull(pane, "g 卡片已渲染");
                pane.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                        MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true,
                        false, false, null));
                return null;
            });
            ui.awaitWorker();
            ui.awaitLayout();
            onFx(() -> {
                var model = ui.host().displayModel();
                var location = ui.host().visibleOccurrences().getLast().occurrence().node();
                assertNotNull(location, "焦点落在对象图页的节点");
                assertNotEquals(model.root().pageId(), location.pageId(), "点击 g 后焦点进入第二层页面");
                var opened = model.pages().get(location.pageId());
                assertEquals(craken.debug.visualization.DebugCaptureProjector.GRAPH_PAGE_TYPE, opened.type().key(),
                        "第二层是单页对象图");
                assertTrue(opened.nodes().values().stream()
                                .anyMatch(node -> "5".equals(node.content().fields().get("n"))),
                        "对象图页显示 g 指向结构体的字段值");
                return null;
            });
        }
    }

    @Test void controlsExposeTheDocumentedCommands() throws Exception {
        try (Fixture ui = fixture(SOURCE)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                assertFalse(ui.start().isDisabled());
                for (Button button : new Button[]{ui.restartButton(), ui.runToEnd(), ui.next(), ui.previous(),
                        ui.stepInto(), ui.stepOut(), ui.nextBreakpoint(), ui.previousBreakpoint()}) {
                    assertTrue(button.isDisabled(), button.getId() + " 在开始前不可用");
                }
                return null;
            });
        }
    }

    /** 用户报告的场景：调试程序的输入输出进入与运行共用的 IO 项，重复开始复用同一项。 */
    @Test void debugSessionStreamsProgramIoIntoTheSharedIoTab() throws Exception {
        try (Fixture ui = fixture(PROGRAM_IO)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> { ui.start().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> { ui.runToEnd().fire(); return null; });
            ui.awaitWorker();
            InteractionItem<?> item = onFx(() -> {
                InteractionItem<?> current = ui.interactions.activeItem();
                assertEquals("IO 1", current.title());
                InputOutputTabs.Entry entry = ui.interactions.inputOutputTabs(ui.editors).open(ui.file());
                assertSame(current, entry.item(), "调试 IO 项来自运行/调试共用的 IO 项表");
                assertEquals("程序已结束", ui.debugStatus().getText());
                assertTrue(ui.debugIo().isFinished(), "程序结束后忽略键盘输入");
                return current;
            });
            ui.awaitDebugText("DEBUG_IO_STDOUT");
            ui.awaitDebugText("DEBUG_IO_STDERR");
            assertTrue(ui.debugLineStartsAtColumnZero("DEBUG_IO_STDERR"),
                    "解释器输出没有 pty，换行必须补 CR 回到行首，不能阶梯缩进");
            assertTrue(ui.debugErrorIsRed(), "标准错误用红色区别于标准输出");
            // 与运行 IO 使用同一个终端组件，字体、字号、配色与行距由同一处代码决定。
            SwingNode surface = onFx(() -> assertInstanceOf(SwingNode.class,
                    ui.debugIo().lookup("#debug-io-surface")));
            assertInstanceOf(UiTerminalWidget.class, onEdt(surface::getContent),
                    "调试 IO 复用运行 IO 的终端组件（同一字体来源）");
            assertEquals(UiStyles.tabDefaultFont(),
                    assertInstanceOf(UiTerminalWidget.class, onEdt(surface::getContent))
                            .settingsProvider().getTerminalFont(),
                    "IO 项使用这个 tab 的默认字体（与交互列表标签、编辑器一致）");
            // 重启回到捕获阶段，再次开始：同一编辑器组件复用同一个 IO 项并清空旧输出。
            onFx(() -> { ui.restartButton().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> { ui.start().fire(); return null; });
            ui.awaitWorker();
            ui.awaitDebugTerminal();
            assertFalse(ui.debugText().contains("DEBUG_IO_STDOUT"), "新的运行从空输出开始");
            onFx(() -> {
                assertSame(item, ui.interactions.activeItem(), "重复开始复用同一个 IO 项");
                assertEquals("IO 1", item.title());
                assertEquals("调试中", ui.debugStatus().getText());
                ui.runToEnd().fire();
                return null;
            });
            ui.awaitWorker();
            ui.awaitDebugText("DEBUG_IO_STDOUT");
        }
    }

    /** 用户报告的场景：调试程序的标准输入来自共用的 IO 项，而不是构造时的固定文本。 */
    @Test void debugSessionTakesStandardInputFromTheSharedIoTab() throws Exception {
        try (Fixture ui = fixture(PROGRAM_INPUT)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> { ui.start().fire(); return null; });
            ui.awaitWorker();
            ui.awaitDebugTerminal();
            onFx(() -> { ui.debugIo().typeText("A\r"); return null; });
            ui.awaitDebugText("A");
            onFx(() -> { ui.runToEnd().fire(); return null; });
            ui.awaitWorker();
            ui.awaitDebugText("DEBUG_IO_INPUT=65");
        }
    }

    /** 用户报告的场景：链表必须在一页里显示全部节点，next 指针画成边而不是逐层分页。 */
    @Test void capturedLinkedListRendersAllNodesOnOneGraphPage() throws Exception {
        try (Fixture ui = fixture(LINKED_LIST)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                ui.captureInput().setText("head");
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                assertEquals(1, ui.captureList().getItems().size(), "回车选中唯一候选");
                ui.start().fire();
                return null;
            });
            ui.awaitWorker();
            ui.awaitLayout();
            for (int step = 0; step < 30; step++) {
                boolean rendered = onFx(() -> ui.host().displayModel() != null
                        && ui.host().displayModel().pages().values().stream()
                                .anyMatch(page -> page.type().key()
                                        .equals(craken.debug.visualization.DebugCaptureProjector.GRAPH_PAGE_TYPE)
                                        && page.nodes().size() == 2
                                        && page.nodes().values().stream()
                                                .anyMatch(node -> "20".equals(
                                                        node.content().fields().get("data")))));
                if (rendered) break;
                onFx(() -> { ui.stepInto().fire(); return null; });
                ui.awaitWorker();
                ui.awaitLayout();
            }
            onFx(() -> {
                var model = ui.host().displayModel();
                assertEquals(2, model.pages().size(), "变量卡片 + 一张链表对象图页");
                var graphPage = model.pages().values().stream()
                        .filter(page -> page.type().key()
                                .equals(craken.debug.visualization.DebugCaptureProjector.GRAPH_PAGE_TYPE))
                        .findFirst().orElseThrow();
                assertEquals(List.of("10", "20"), graphPage.nodes().values().stream()
                        .map(node -> node.content().fields().get("data")).sorted().toList());
                assertEquals(1, graphPage.topology().size(), "next 指针一条边");
                return null;
            });
            onFx(() -> {
                var model = ui.host().displayModel();
                var head = model.pages().get(model.root().pageId()).nodes().values().stream()
                        .filter(node -> "head".equals(node.content().label())).findFirst().orElseThrow();
                var pane = ui.host().visibleOccurrences().getFirst().nodeViews().get(head.location());
                assertNotNull(pane, "head 卡片已渲染");
                pane.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                        MouseButton.PRIMARY, 1, false, false, false, false, true, false, false,
                        true, false, false, null));
                return null;
            });
            ui.awaitWorker();
            ui.awaitLayout();
            onFx(() -> {
                var graphOccurrence = ui.host().visibleOccurrences().getLast();
                assertTrue(graphOccurrence.occurrence().page().pageId() > 1, "点击 head 后进入对象图页");
                var part = graphOccurrence.parts().getFirst();
                assertTrue(part.errorText().isEmpty(), part.errorText());
                assertEquals(1, part.geometry().edgePaths().size(), "next 一条边");
                for (var path : part.geometry().edgePaths().values()) {
                    assertEquals(path.start().y(), path.end().y(), 1.0,
                            "链表箭头必须水平：" + path.start() + " -> " + path.end());
                }
                return null;
            });
        }
    }

    @Test void restartReturnsToTheCapturePhaseAndKeepsEditableSelection() throws Exception {
        try (Fixture ui = fixture(SOURCE)) {
            onFx(() -> { ui.icon().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                ui.captureInput().setText("counter");
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                ui.start().fire();
                return null;
            });
            ui.awaitWorker();
            onFx(() -> { assertTrue(ui.host().isVisible(), "开始后进入可视化"); return null; });
            onFx(() -> { ui.restartButton().fire(); return null; });
            ui.awaitWorker();
            onFx(() -> {
                assertFalse(ui.host().isVisible(), "重启回到开始前的捕获阶段");
                assertEquals(1, ui.captureList().getItems().size(), "捕获列表保留");
                assertFalse(ui.start().isDisabled());
                ui.captureInput().setText("total");
                ui.captureInput().fireEvent(key(KeyCode.ENTER));
                assertEquals(2, ui.captureList().getItems().size(), "重启后仍可增删变量");
                return null;
            });
        }
    }

    private int lineOf(String marker) {
        int line = 1;
        for (String text : SOURCE.split("\n", -1)) {
            if (text.contains(marker)) return line;
            line++;
        }
        throw new IllegalArgumentException("Marker not found: " + marker);
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static List<Integer> highlightedLines(EditorFile file) {
        List<Integer> lines = new ArrayList<>();
        file.editor().onTextArea(area -> {
            for (var highlight : area.getHighlighter().getHighlights()) {
                try {
                    lines.add(area.getLineOfOffset(highlight.getStartOffset()) + 1);
                } catch (BadLocationException failure) {
                    throw new IllegalStateException("highlight start is outside the editor", failure);
                }
            }
        });
        return lines;
    }

    private Fixture fixture(String source) throws Exception {
        return onFx(() -> new Fixture(directory, source));
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        try { return result.get(30, TimeUnit.SECONDS); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            throw (Exception) failure.getCause();
        }
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        SwingUtilities.invokeLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    /** 终端缓冲区可能按屏幕宽度折行，取全部历史行与屏幕行拼成可断言的文本。 */
    private static String terminalText(JediTermWidget widget) {
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
        } finally {
            buffer.unlock();
        }
    }

    private static TerminalLine terminalLine(JediTermWidget widget, String marker) {
        TerminalTextBuffer buffer = widget.getTerminalTextBuffer();
        buffer.lock();
        try {
            var lines = new ArrayList<TerminalLine>();
            buffer.getHistoryLinesStorage().forEach(lines::add);
            buffer.getScreenLinesStorage().forEach(lines::add);
            for (TerminalLine line : lines) {
                if (line.getText().contains(marker)) return line;
            }
            return null;
        } finally {
            buffer.unlock();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final EditorArea editors = new EditorArea();
        final DisplayArea display = new DisplayArea();
        final InteractionArea interactions;
        final AppFrame frame;
        final DebugController controller;
        final ExecutorService worker;
        private boolean closed;

        Fixture(Path directory, String source) throws Exception {
            interactions = new InteractionArea(directory);
            // 测试不启动 PowerShell：默认终端项在挂到场景前移除，IO 项只由调试会话创建。
            interactions.closeItem(interactions.activeItem());
            frame = new AppFrame(editors, display, interactions, editors.tabBar());
            controller = new DebugController(editors, frame, display, interactions);
            worker = (ExecutorService) read(controller, "worker");
            new Scene(frame, 1280, 800);
            if (source != null) {
                Path path = Files.writeString(directory.resolve("debug.mc"), source);
                EditorFile file = editors.openFile(path);
                assertNotNull(file);
            }
        }

        ToggleButton icon() { return assertInstanceOf(ToggleButton.class, frame.lookup("#app-debug-button")); }
        DebugPanel panel() { return assertInstanceOf(DebugPanel.class, display.getChildren().getFirst()); }
        SplitPane split() { return assertInstanceOf(SplitPane.class, panel().lookup("#debug-split")); }
        SplitPane memorySplit() { return assertInstanceOf(SplitPane.class, panel().lookup("#debug-memory-split")); }
        TextField captureInput() { return assertInstanceOf(TextField.class, panel().lookup("#debug-capture-input")); }
        @SuppressWarnings("unchecked")
        ListView<DebugPanel.CaptureItem> captureList() { return (ListView<DebugPanel.CaptureItem>) panel().lookup("#debug-capture-list"); }
        @SuppressWarnings("unchecked")
        ComboBox<DebugVariable> captureCandidates() {
            return (ComboBox<DebugVariable>) assertInstanceOf(ComboBox.class,
                    panel().lookup("#debug-capture-candidates"));
        }
        Button start() { return assertInstanceOf(Button.class, panel().lookup("#debug-start")); }
        Button restartButton() { return assertInstanceOf(Button.class, panel().lookup("#debug-restart")); }
        Button runToEnd() { return assertInstanceOf(Button.class, panel().lookup("#debug-run-to-end")); }
        Button next() { return assertInstanceOf(Button.class, panel().lookup("#debug-next")); }
        Button previous() { return assertInstanceOf(Button.class, panel().lookup("#debug-previous")); }
        Button stepInto() { return assertInstanceOf(Button.class, panel().lookup("#debug-step-into")); }
        Button stepOut() { return assertInstanceOf(Button.class, panel().lookup("#debug-step-out")); }
        Button nextBreakpoint() { return assertInstanceOf(Button.class, panel().lookup("#debug-next-breakpoint")); }
        Button previousBreakpoint() { return assertInstanceOf(Button.class, panel().lookup("#debug-prev-breakpoint")); }
        Label status() { return assertInstanceOf(Label.class, panel().lookup("#debug-status")); }
        @SuppressWarnings("unchecked")
        ListView<DebugWorkbenchSession.MemoryRow> stack() { return (ListView<DebugWorkbenchSession.MemoryRow>) panel().lookup("#debug-stack-list"); }
        @SuppressWarnings("unchecked")
        ListView<DebugWorkbenchSession.MemoryRow> heap() { return (ListView<DebugWorkbenchSession.MemoryRow>) panel().lookup("#debug-heap-list"); }
        craken.ui.component.visualization.UiVisualizationContainer host() {
            return assertInstanceOf(craken.ui.component.visualization.UiVisualizationContainer.class,
                    panel().lookup("#debug-visualization"));
        }
        EditorFile file() { return editors.activeFile(); }

        InputOutputTab activeIoTab() {
            return assertInstanceOf(InputOutputTab.class, interactions.activeItem().content());
        }
        DebugIoPanel debugIo() {
            return assertInstanceOf(DebugIoPanel.class, activeIoTab().channel().node());
        }
        Label debugStatus() {
            return assertInstanceOf(Label.class, activeIoTab().lookup("#debug-io-status"));
        }

        /** 等终端控件在 Swing EDT 建好（输出在此之前由面板队列暂存）。 */
        void awaitDebugTerminal() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                if (onFx(() -> debugIo().widget()) != null) return;
                Thread.sleep(20);
            }
            fail("调试终端未在超时前创建");
        }

        String debugText() throws Exception {
            UiTerminalWidget widget = onFx(() -> debugIo().widget());
            if (widget == null) return "";
            return onEdt(() -> terminalText(widget));
        }

        /** 标记是否落在行首：解释器输出不经过 pty，换行必须由面板补 CR（否则每行都向右缩进）。 */
        boolean debugLineStartsAtColumnZero(String marker) throws Exception {
            UiTerminalWidget widget = onFx(() -> debugIo().widget());
            assertNotNull(widget, "调试终端已创建");
            return onEdt(() -> {
                TerminalTextBuffer buffer = widget.getTerminalTextBuffer();
                buffer.lock();
                try {
                    for (int row = 0; row < buffer.getScreenLinesCount(); row++) {
                        TerminalLine line = buffer.getLine(row);
                        if (line == null || !line.getText().contains(marker)) continue;
                        return line.getText().startsWith(marker);
                    }
                    return false;
                } finally {
                    buffer.unlock();
                }
            });
        }

        void awaitDebugText(String expected) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            String text = "";
            while (System.nanoTime() < deadline) {
                text = debugText();
                if (text.contains(expected)) return;
                Thread.sleep(25);
            }
            fail("调试 IO 未在超时前出现 " + expected + "：\n" + text);
        }

        /** 标准错误与标准输出的字形颜色不同：前者是 ANSI 红，后者是终端默认前景色。 */
        boolean debugErrorIsRed() throws Exception {
            UiTerminalWidget widget = onFx(() -> debugIo().widget());
            assertNotNull(widget, "调试终端已创建");
            return onEdt(() -> {
                TerminalLine stdout = terminalLine(widget, "DEBUG_IO_STDOUT");
                TerminalLine error = terminalLine(widget, "DEBUG_IO_STDERR");
                assertNotNull(stdout, "标准输出行存在");
                assertNotNull(error, "标准错误行存在");
                return !stdout.getStyleAt(0).getForeground().equals(error.getStyleAt(0).getForeground());
            });
        }

        double awaitDividerPosition() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                double position = onFx(() -> assertInstanceOf(SplitPane.class, frame.lookup(".app-content"))
                        .getDividerPositions()[0]);
                if (Math.abs(position - 0.5) < 0.02) return position;
                Thread.sleep(20);
            }
            return onFx(() -> assertInstanceOf(SplitPane.class, frame.lookup(".app-content")).getDividerPositions()[0]);
        }

        void awaitWorker() throws Exception {
            worker.submit(() -> { }).get(30, TimeUnit.SECONDS);
            onFx(() -> { frame.applyCss(); frame.layout(); return null; });
        }

        /** 输入后等待 300ms 防抖检索把候选发布到下拉列表。 */
        void awaitCaptureSearch(String term) throws Exception {
            onFx(() -> { captureInput().setText(term); return null; });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                if (onFx(() -> !captureCandidates().getItems().isEmpty())) return;
                Thread.sleep(50);
            }
            throw new IllegalStateException("候选下拉未在防抖窗口内发布：" + term);
        }

        void awaitLayout() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                boolean idle = onFx(() -> {
                    frame.applyCss(); frame.layout();
                    return !panel().visualizationHost().isLayoutPending();
                });
                if (idle) { onFx(() -> null); return; }
                Thread.sleep(30);
            }
            throw new IllegalStateException("layout never idled");
        }

        @Override public void close() throws Exception {
            if (closed) return;
            closed = true;
            onFx(() -> {
                try {
                    controller.close();
                    interactions.close();
                }
                finally {
                    editors.setCloseDecisionHandler(file -> EditorArea.CloseChoice.DISCARD);
                    assertTrue(editors.closeAll());
                }
                return null;
            });
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }

        private static Object read(Object instance, String name) throws Exception {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        }
    }
}
