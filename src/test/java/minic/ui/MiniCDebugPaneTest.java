package minic.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCDebugPaneTest {
    private static boolean javafxStarted;

    private static void startJavafx() {
        if (javafxStarted) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        javafxStarted = true;
    }

    @Test
    void exposesSplitControlWithoutCreatingAnotherDebugSession() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            assertThat(button(pane, "开始")).isNull();
            assertThat(button(pane, "打开")).isNull();
            assertThat(button(pane, "保存")).isNull();
            assertThat(button(pane, "从头开始")).isNotNull();
            assertThat(button(pane, "拆分")).isNotNull();
            assertThat(button(pane, "元数据")).isNotNull();
            assertThat(button(pane, "数据结构")).isNotNull();
            assertThat(button(pane, "AST")).isNotNull();
            assertThat(button(pane, "IR")).isNotNull();
            assertThat(button(pane, "ASM")).isNotNull();
            assertThat(button(pane, "设断点")).isNull();
            assertThat(button(pane, "清断点")).isNull();
            assertThat(button(pane, "快进")).isNull();
            assertThat(button(pane, "运行到断点")).isNull();
            assertThat(button(pane, "单步")).isNull();
            assertThat(button(pane, "步入")).isNull();
            assertThat(button(pane, "步返")).isNull();
            assertThat(button(pane, "暂停")).isNull();
            assertThat(button(pane, "重启")).isNull();
            assertThat(button(pane, "关闭")).isNull();
            assertThat(button(pane, "单退")).isNull();
            assertThat(button(pane, "步退")).isNull();
            assertThat(button(pane, "返回调用处")).isNull();
            assertThat(List.of("下个断点", "本层下一句", "下一句", "上个断点", "本层上一句", "上一句"))
                    .allSatisfy(text -> {
                        Button debugButton = button(pane, text);
                        assertThat(debugButton.getStyleClass()).contains("debug-control-paired-button");
                        assertThat(debugButton.getPrefWidth()).isEqualTo(92);
                        assertThat(debugButton.getMinWidth()).isEqualTo(92);
                        assertThat(debugButton.getMaxWidth()).isEqualTo(92);
                        assertThat(debugButton.getPrefHeight()).isEqualTo(28);
                    });
            assertThat(List.of("从头开始", "运行到结束", "拆分"))
                    .allSatisfy(text -> {
                        Button debugButton = button(pane, text);
                        assertThat(debugButton.getStyleClass()).contains("debug-control-single-button");
                        assertThat(debugButton.getPrefWidth()).isEqualTo(92);
                        assertThat(debugButton.getMinWidth()).isEqualTo(92);
                        assertThat(debugButton.getMaxWidth()).isEqualTo(92);
                        assertThat(debugButton.getPrefHeight()).isEqualTo(28);
                    });
            assertThat(hboxesWithStyle(pane, "debug-controls"))
                    .singleElement()
                    .extracting(HBox::getAlignment)
                    .isEqualTo(Pos.TOP_LEFT);
            assertThat(hboxesWithStyle(pane, "debug-paired-row"))
                    .allSatisfy(row -> assertThat(row.getAlignment()).isEqualTo(Pos.TOP_LEFT));
            assertThat(pairedDebugControlRows(pane))
                    .containsExactly(
                            List.of("从头开始", "下个断点", "本层下一句", "下一句"),
                            List.of("运行到结束", "上个断点", "本层上一句", "上一句")
                    );
            assertThat(viewModel.debugStartedProperty().get()).isFalse();

            button(pane, "拆分").fire();

            assertThat(viewModel.debugStartedProperty().get()).isFalse();
        });
    }

    @Test
    void debuggerSourceEditorUsesDraggableScrollBars() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            VirtualizedScrollPane<?> scrollPane = virtualizedScrollPaneWithStyle(pane, "debug-source-editor-scroll");

            assertThat(scrollPane).isNotNull();
            assertThat(scrollPane.getVbarPolicy()).isEqualTo(ScrollPane.ScrollBarPolicy.ALWAYS);
            assertThat(scrollPane.getHbarPolicy()).isEqualTo(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        });
    }

    @Test
    void debuggerSourceEditorRendersVisibleRightSideScrollBar() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);
            Scene scene = new Scene(pane, 1200, 760);
            pane.applyCss();
            pane.layout();

            VirtualizedScrollPane<?> editorScroll = virtualizedScrollPaneWithStyle(pane, "debug-source-editor-scroll");
            assertThat(editorScroll).isNotNull();
            editorScroll.applyCss();
            editorScroll.resize(500, 640);
            editorScroll.layout();

            List<ScrollBar> verticalScrollBars = scrollBars(editorScroll).stream()
                    .filter(scrollBar -> scrollBar.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                    .filter(scrollBar -> scrollBar.isVisible())
                    .filter(scrollBar -> scrollBar.getBoundsInParent().getWidth() > 0)
                    .toList();
            assertThat(verticalScrollBars).isNotEmpty();
            assertThat(verticalScrollBars)
                    .anySatisfy(scrollBar -> assertThat(scrollBar.getBoundsInParent().getMaxX())
                            .isGreaterThanOrEqualTo(editorScroll.getWidth() - 16));
            assertThat(verticalScrollBars)
                    .anySatisfy(scrollBar -> assertThat(scrollBar.lookup(".thumb").getBoundsInParent().getHeight())
                            .isGreaterThan(0));
            assertThat(scene.getRoot()).isSameAs(pane);
        });
    }

    @Test
    void laysOutViewSelectorSourceAndDebugViewSideBySide() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            Parent workspace = (Parent) directChildWithStyle(pane, "debug-workspace");
            assertThat(workspace).isNotNull();
            assertThat(workspace.getChildrenUnmodifiable().getFirst().getStyleClass()).contains("debug-view-selector");
            assertThat(workspace.getChildrenUnmodifiable().get(1)).isInstanceOf(SplitPane.class);
            assertThat(buttonTextsWithStyle(pane, "debug-view-button"))
                    .contains("元数据", "数据结构", "AST", "IR", "ASM");
            assertThat(containsNode(pane, MiniCSourceLoaderView.class)).isTrue();
            assertThat(containsNode(pane, SplitPane.class)).isTrue();
        });
    }

    @Test
    void rendersStructuredMetadataSections() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-ui.mc", "int main() { return 0; }");
            viewModel.startDebug();

            assertThat(label(pane, "状态")).isNotNull();
            assertThat(sectionTitles(pane))
                    .contains("调用栈", "变量", "断点", "事件日志", "Snapshot 时间线");
        });
    }

    @Test
    void rendersProcessSpaceSectionsInDataStructureTab() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-data-ui.mc", "int main() { return 0; }");
            viewModel.startDebug();
            button(pane, "数据结构").fire();

            assertThat(labelsWithStyle(pane, "debug-process-title"))
                    .contains("runtime")
                    .doesNotContain("static/data", "stack", "heap", "io");
            assertThat(labelsWithStyle(pane, "debug-section-title"))
                    .contains("visual structures", "warnings");
        });
    }

    @Test
    void rendersDataStructureVisualsAsShapes() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-visual-shapes-ui.mc", """
                    // @visual array name=arr kind=array root=value
                    // @visual-node graph=graph id=1 label=head
                    // @visual-node graph=graph id=2 label=tail
                    // @visual-edge graph=graph from=1 to=2 label=next directed=true
                    int main() {
                        int value = 1;
                        return value;
                    }
                    """);
            viewModel.startDebug();
            button(pane, "数据结构").fire();

            assertThat(containsStyle(pane, "debug-visual-diagram")).isTrue();
            assertThat(containsStyle(pane, "debug-array-cell")).isTrue();
            assertThat(containsStyle(pane, "debug-graph-node")).isTrue();
            assertThat(containsStyle(pane, "debug-graph-edge")).isTrue();
            assertThat(containsStyle(pane, "debug-graph-edge-head")).isTrue();
            assertThat(containsStyle(pane, "debug-pointer-arrow")).isTrue();
        });
    }

    @Test
    void rendersBeginnerExplanationsInDataStructureTab() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-visual-explanation-ui.mc", """
                    // @visual array name=slot kind=array root=value
                    int main() {
                        int value = 1;
                        value = value + 1;
                        return value;
                    }
                    """);
            viewModel.startDebug();
            viewModel.debugFastForward();
            button(pane, "数据结构").fire();

            assertThat(labelsWithStyle(pane, "debug-section-line"))
                    .anySatisfy(text -> assertThat(text).contains("cells=1"))
                    .noneSatisfy(text -> assertThat(text).contains("C 代码"));
        });
    }

    @Test
    void rendersNestedStructFieldsAndPointerTargetsInDataStructureTab() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-nested-data-ui.mc", """
                    // @visual root=n1 kind=struct fields=value,left,right
                    struct Node {
                        int value;
                        struct Node *left;
                        struct Node *right;
                    };

                    int main() {
                        struct Node n1;
                        struct Node n2;
                        struct Node n3;
                        struct Node *root = &n1;
                        n1.value = 10;
                        n1.left = &n2;
                        n1.right = &n3;
                        n2.value = 5;
                        n2.left = NULL;
                        n2.right = NULL;
                        n3.value = 15;
                        n3.left = NULL;
                        n3.right = NULL;
                        return n1.value;
                    }
                    """);
            viewModel.setDebugBreakpoints(List.of(22));
            viewModel.startDebug();
            viewModel.debugRunToBreakpoint();
            button(pane, "数据结构").fire();

            assertThat(labelsWithStyle(pane, "debug-section-line"))
                    .anySatisfy(text -> assertThat(text).contains("value", "int", "10"))
                    .anySatisfy(text -> assertThat(text).contains("left", "pointer", "-> stack:"))
                    .anySatisfy(text -> assertThat(text).contains("right", "pointer", "-> stack:"))
                    .noneSatisfy(text -> assertThat(text).contains("pointerTarget="));
        });
    }

    @Test
    void rendersGraphTreeWithRecursiveTreeLayout() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-tree-layout-ui.mc", """
                    // @visual graph name=avl kind=tree root=root
                    // @visual-node graph=avl id=1 label=10
                    // @visual-node graph=avl id=2 label=5
                    // @visual-node graph=avl id=3 label=15
                    // @visual-edge graph=avl from=1 to=2 label=left directed=true
                    // @visual-edge graph=avl from=1 to=3 label=right directed=true
                    int main() {
                        int root = 1;
                        return root;
                    }
                    """);
            viewModel.startDebug();
            button(pane, "数据结构").fire();

            Circle root = circleWithAccessibleText(pane, "debug-graph-node", "1");
            Circle left = circleWithAccessibleText(pane, "debug-graph-node", "2");
            Circle right = circleWithAccessibleText(pane, "debug-graph-node", "3");

            assertThat(root).isNotNull();
            assertThat(left).isNotNull();
            assertThat(right).isNotNull();
            assertThat(root.getCenterY()).isLessThan(left.getCenterY());
            assertThat(root.getCenterY()).isLessThan(right.getCenterY());
            assertThat(root.getCenterX()).isGreaterThan(left.getCenterX());
            assertThat(root.getCenterX()).isLessThan(right.getCenterX());
        });
    }

    @Test
    void rendersRuntimeMappedGraphAsTreeLayout() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-runtime-tree-layout-ui.mc", """
                    // @visual graph name=avl kind=tree root=root mode=runtime function=build visit=index
                    // @visual-map node graph=avl id=index label=index
                    // @visual-map edge graph=avl key=left from=index to=left
                    // @visual-map edge graph=avl key=right from=index to=right
                    int build(int index) {
                        int left = 0;
                        int right = 0;
                        if (index == 1) {
                            left = 2;
                            right = 3;
                        }
                        if (index == 0) {
                            return 0;
                        }
                        return index + build(left) + build(right);
                    }
                    int main() {
                        int root = 1;
                        return build(root);
                    }
                    """);
            viewModel.startDebug();
            viewModel.debugFastForward();
            button(pane, "数据结构").fire();

            Circle root = circleWithAccessibleText(pane, "debug-graph-node", "1");
            Circle left = circleWithAccessibleText(pane, "debug-graph-node", "2");
            Circle right = circleWithAccessibleText(pane, "debug-graph-node", "3");

            assertThat(root).isNotNull();
            assertThat(left).isNotNull();
            assertThat(right).isNotNull();
            assertThat(root.getCenterY()).isLessThan(left.getCenterY());
            assertThat(root.getCenterY()).isLessThan(right.getCenterY());
            assertThat(root.getCenterX()).isGreaterThan(left.getCenterX());
            assertThat(root.getCenterX()).isLessThan(right.getCenterX());
            assertThat(rectangleWithAccessibleText(pane, "debug-null-node", "null-2-left")).isNotNull();
            assertThat(rectangleWithAccessibleText(pane, "debug-null-node", "null-2-right")).isNotNull();
        });
    }

    @Test
    void rendersHashChainTableWithChainsBelowBuckets() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-hash-chain-layout-ui.mc", """
                    // @visual graph name=hash kind=hash-chain-table root=root
                    // @visual-node graph=hash id=b0 label=[0] visual-role=bucket bucketIndex=0
                    // @visual-node graph=hash id=b1 label=[1] visual-role=bucket bucketIndex=1
                    // @visual-node graph=hash id=b2 label=[2] visual-role=bucket bucketIndex=2
                    // @visual-node graph=hash id=n0 label=10 visual-role=chain-node bucketIndex=0 chainDepth=0
                    // @visual-node graph=hash id=n1 label=20 visual-role=chain-node bucketIndex=0 chainDepth=1
                    // @visual-node graph=hash id=n2 label=30 visual-role=chain-node bucketIndex=2 chainDepth=0
                    // @visual-edge graph=hash from=b0 to=n0 label=bucket directed=true bucketIndex=0
                    // @visual-edge graph=hash from=n0 to=n1 label=next directed=true bucketIndex=0
                    // @visual-edge graph=hash from=b2 to=n2 label=bucket directed=true bucketIndex=2
                    int main() { return 0; }
                    """);
            viewModel.startDebug();
            button(pane, "数据结构").fire();

            Circle bucket0 = circleWithAccessibleText(pane, "debug-graph-node", "b0");
            Circle bucket1 = circleWithAccessibleText(pane, "debug-graph-node", "b1");
            Circle bucket2 = circleWithAccessibleText(pane, "debug-graph-node", "b2");
            List<Circle> chainNodes = circlesWithStyle(pane, "debug-graph-node").stream()
                    .filter(circle -> circle.getAccessibleText() == null || !circle.getAccessibleText().startsWith("b"))
                    .toList();

            assertThat(bucket0).isNotNull();
            assertThat(bucket1).isNotNull();
            assertThat(bucket2).isNotNull();
            assertThat(bucket0.getCenterY()).isEqualTo(bucket1.getCenterY());
            assertThat(bucket1.getCenterY()).isEqualTo(bucket2.getCenterY());
            assertThat(bucket0.getCenterX()).isLessThan(bucket1.getCenterX());
            assertThat(bucket1.getCenterX()).isLessThan(bucket2.getCenterX());
            assertThat(chainNodes).anySatisfy(node ->
                    assertThat(node.getCenterY()).isGreaterThan(bucket0.getCenterY()));
        });
    }

    @Test
    void switchesDebugViewsFromLeftSelector() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-selector-ui.mc", "int main() { return 0; }");
            viewModel.startDebug();

            Button ir = button(pane, "IR");
            ir.fire();

            assertThat(ir.getStyleClass()).contains("active");
            assertThat(label(pane, "状态")).isNull();
            assertThat(textContaining(pane, "current:")).isNotNull();
        });
    }

    @Test
    void rendersAstDebugViewAsGraph() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-ast-graph-ui.mc", """
                    int main() {
                        int value = 1;
                        return value;
                    }
                    """);
            viewModel.startDebug();
            button(pane, "AST").fire();

            assertThat(containsStyle(pane, "ast-graph")).isTrue();
            assertThat(containsStyle(pane, "ast-graph-node")).isTrue();
            assertThat(containsStyle(pane, "active")).isTrue();
            assertThat(labelsWithStyle(pane, "debug-section-title")).contains("当前 AST 节点");
        });
    }

    @Test
    void rendersCompleteIrAndAsmRowsWithActiveOutline() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            viewModel.loadSource("debug-ir-asm-ui.mc", """
                    extern int printf(char *format, ...);

                    int main() {
                        int a = 0;
                        a += 1;
                        printf("value = %d\\n", a);
                        return 42;
                    }
                    """);
            viewModel.startDebug();
            viewModel.debugStepOver();

            button(pane, "IR").fire();
            assertThat(labelsWithStyle(pane, "debug-code-text"))
                    .anyMatch(text -> text.contains("function main"))
                    .anyMatch(text -> text.contains("store"))
                    .anyMatch(text -> text.contains("call printf"));
            assertThat(containsNodeWithStyles(pane, "debug-code-row", "active")).isTrue();

            button(pane, "ASM").fire();
            assertThat(labelsWithStyle(pane, "debug-code-text"))
                    .anyMatch(text -> text.contains("main"))
                    .anyMatch(text -> text.contains("call"))
                    .anyMatch(text -> text.contains("ret"));
            assertThat(containsNodeWithStyles(pane, "debug-code-row", "active")).isTrue();
        });
    }

    @Test
    void preservesDebugViewportAcrossStepRefresh() {
        startJavafx();
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        MiniCDebugPane pane = runOnFxThreadWithResult(() -> {
            MiniCDebugPane debugPane = new MiniCDebugPane(viewModel);
            viewModel.loadSource("debug-scroll-ui.mc", """
                    int main() {
                        int value = 0;
                        value = value + 1;
                        value = value + 2;
                        value = value + 3;
                        return value;
                    }
                    """);
            viewModel.startDebug();
            viewModel.debugStepOver();
            button(debugPane, "IR").fire();
            ScrollPane scrollPane = scrollPaneWithContentStyle(debugPane, "debug-code-view");
            assertThat(scrollPane).isNotNull();
            scrollPane.setVvalue(0.73);
            return debugPane;
        });

        runOnFxThread(() -> {
            viewModel.debugStepOver();
        });
        runOnFxThread(() -> {
            ScrollPane scrollPane = scrollPaneWithContentStyle(pane, "debug-code-view");
            assertThat(scrollPane).isNotNull();
            assertThat(scrollPane.getVvalue()).isCloseTo(0.73, org.assertj.core.data.Offset.offset(0.001));
        });
    }

    @Test
    void debugStartDoesNotStartCompilerObservationSession() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);

            button(pane, "从头开始").fire();

            assertThat(viewModel.debugStartedProperty().get()).isTrue();
            assertThat(viewModel.sessionStartedProperty().get()).isFalse();
        });
    }

    @Test
    void previousStatementInCurrentLayerDoesNotReenterCompletedFunctionFromButton() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);
            viewModel.loadSource("debug-ui-step-back-over.mc", """
                    int inc(int value) {
                        int next = value + 1;
                        return next;
                    }

                    int main() {
                        int value = inc(1);
                        return value;
                    }
                    """);
            viewModel.startDebug();

            movePastIncCallWithButton(pane, viewModel);
            button(pane, "本层上一句").fire();

            assertThat(viewModel.debugStateProperty().get().currentSnapshot().callStackSummary())
                    .containsExactly("main");
        });
    }

    @Test
    void previousStatementInCurrentLayerDoesNotReenterFunctionSkippedByNextLayerButton() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);
            viewModel.loadSource("debug-ui-step-over-then-back-over.mc", """
                    int inc(int value) {
                        int next = value + 1;
                        return next;
                    }

                    int main() {
                        int value = inc(1);
                        return value;
                    }
                    """);
            viewModel.startDebug();
            viewModel.setDebugBreakpoint(7);
            button(pane, "下个断点").fire();
            button(pane, "本层下一句").fire();

            button(pane, "本层上一句").fire();

            assertThat(viewModel.debugStateProperty().get().currentSnapshot().callStackSummary())
                    .containsExactly("main");
        });
    }

    @Test
    void highlightsCurrentDebugSourceRangeAcrossExpressionSteps() {
        startJavafx();
        runOnFxThread(() -> {
            String source = """
                    int func1() { return 10; }
                    int func2() { return 20; }
                    int main() {
                        int a = 0;
                        a = 1 + func1() + func2();
                        return a;
                    }
                    """;
            MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
            MiniCDebugPane pane = new MiniCDebugPane(viewModel);
            viewModel.loadSource("debug-expression-highlight.mc", source);
            viewModel.startDebug();
            viewModel.setDebugBreakpoint(5);
            MiniCCodeEditor editor = editor(pane);

            button(pane, "下个断点").fire();

            int func1Start = source.lastIndexOf("func1()");
            int func2Start = source.lastIndexOf("func2()");
            int statementStart = source.indexOf("a = 1");
            int returnValueStart = source.lastIndexOf("a;");
            assertThat(styleAt(editor, func1Start)).contains("debug-execution-range");
            assertThat(styleAt(editor, func2Start)).contains("debug-execution-range");
            assertThat(styleAt(editor, statementStart)).contains("debug-execution-range");

            button(pane, "本层下一句").fire();

            assertThat(styleAt(editor, func1Start)).doesNotContain("debug-execution-range");
            assertThat(styleAt(editor, func2Start)).contains("debug-execution-range");

            button(pane, "本层下一句").fire();

            assertThat(styleAt(editor, statementStart)).doesNotContain("debug-execution-range");
            assertThat(styleAt(editor, func1Start)).doesNotContain("debug-execution-range");
            assertThat(styleAt(editor, func2Start)).doesNotContain("debug-execution-range");
            assertThat(styleAt(editor, returnValueStart)).contains("debug-execution-range");
        });
    }

    private static void runOnFxThread(Runnable action) {
        runOnFxThreadWithResult(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T runOnFxThreadWithResult(java.util.concurrent.Callable<T> action) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<T> result = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }

    private static Button button(javafx.scene.Node node, String text) {
        if (node instanceof Button button && button.getText().equals(text)) {
            return button;
        }
        if (node instanceof SplitPane splitPane) {
            for (javafx.scene.Node item : splitPane.getItems()) {
                Button found = button(item, text);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Button found = button(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<List<String>> pairedDebugControlRows(javafx.scene.Node node) {
        List<List<String>> rows = new ArrayList<>();
        collectPairedDebugControlRows(node, rows);
        return rows;
    }

    private static List<HBox> hboxesWithStyle(javafx.scene.Node node, String styleClass) {
        List<HBox> rows = new ArrayList<>();
        collectHboxesWithStyle(node, styleClass, rows);
        return rows;
    }

    private static void collectHboxesWithStyle(javafx.scene.Node node, String styleClass, List<HBox> rows) {
        if (node instanceof HBox hbox && hbox.getStyleClass().contains(styleClass)) {
            rows.add(hbox);
        }
        if (node instanceof SplitPane splitPane) {
            splitPane.getItems().forEach(child -> collectHboxesWithStyle(child, styleClass, rows));
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectHboxesWithStyle(child, styleClass, rows));
        }
    }

    private static void collectPairedDebugControlRows(javafx.scene.Node node, List<List<String>> rows) {
        if (node instanceof HBox hbox && hbox.getStyleClass().contains("debug-paired-row")) {
            rows.add(hbox.getChildren().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .map(Button::getText)
                    .toList());
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectPairedDebugControlRows(child, rows));
        }
    }

    private static void movePastIncCallWithButton(MiniCDebugPane pane, MiniCWorkbenchViewModel viewModel) {
        viewModel.setDebugBreakpoint(7);
        button(pane, "下个断点").fire();
        boolean enteredInc = false;
        while (!"COMPLETED".equals(viewModel.debugStateProperty().get().executionState())) {
            button(pane, "下一句").fire();
            List<String> stack = viewModel.debugStateProperty().get().currentSnapshot().callStackSummary();
            if (stack.contains("inc")) {
                enteredInc = true;
            }
            if (enteredInc && stack.equals(List.of("main"))) {
                return;
            }
        }
        throw new AssertionError("did not return from inc call");
    }

    private static MiniCCodeEditor editor(javafx.scene.Node node) {
        if (node instanceof MiniCCodeEditor editor) {
            return editor;
        }
        if (node instanceof ScrollPane scrollPane) {
            return editor(scrollPane.getContent());
        }
        if (node instanceof SplitPane splitPane) {
            for (javafx.scene.Node item : splitPane.getItems()) {
                MiniCCodeEditor found = editor(item);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                MiniCCodeEditor found = editor(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static VirtualizedScrollPane<?> virtualizedScrollPaneWithStyle(javafx.scene.Node node, String styleClass) {
        if (node instanceof VirtualizedScrollPane<?> scrollPane && scrollPane.getStyleClass().contains(styleClass)) {
            return scrollPane;
        }
        if (node instanceof SplitPane splitPane) {
            for (javafx.scene.Node item : splitPane.getItems()) {
                VirtualizedScrollPane<?> found = virtualizedScrollPaneWithStyle(item, styleClass);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof ScrollPane scrollPane) {
            return virtualizedScrollPaneWithStyle(scrollPane.getContent(), styleClass);
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                VirtualizedScrollPane<?> found = virtualizedScrollPaneWithStyle(child, styleClass);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<ScrollBar> scrollBars(javafx.scene.Node node) {
        List<ScrollBar> result = new ArrayList<>();
        collectScrollBars(node, result);
        return result;
    }

    private static void collectScrollBars(javafx.scene.Node node, List<ScrollBar> result) {
        if (node instanceof ScrollBar scrollBar) {
            result.add(scrollBar);
        }
        if (node instanceof SplitPane splitPane) {
            splitPane.getItems().forEach(child -> collectScrollBars(child, result));
        }
        if (node instanceof ScrollPane scrollPane) {
            collectScrollBars(scrollPane.getContent(), result);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectScrollBars(child, result));
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Collection<String> styleAt(MiniCCodeEditor editor, int position) {
        try {
            Field input = MiniCCodeEditor.class.getDeclaredField("input");
            input.setAccessible(true);
            Object area = input.get(editor);
            return (java.util.Collection<String>) area.getClass().getMethod("getStyleOfChar", int.class)
                    .invoke(area, position);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Label label(javafx.scene.Node node, String text) {
        if (node instanceof Label label && label.getText().equals(text)) {
            return label;
        }
        if (node instanceof ScrollPane scrollPane) {
            return label(scrollPane.getContent(), text);
        }
        if (node instanceof SplitPane splitPane) {
            for (javafx.scene.Node item : splitPane.getItems()) {
                Label found = label(item, text);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Label found = label(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<String> sectionTitles(javafx.scene.Node node) {
        ArrayList<String> titles = new ArrayList<>();
        collectSectionTitles(node, titles);
        return titles;
    }

    private static List<String> labelsWithStyle(javafx.scene.Node node, String styleClass) {
        ArrayList<String> labels = new ArrayList<>();
        collectLabelsWithStyle(node, styleClass, labels);
        return labels;
    }

    private static List<String> buttonTextsWithStyle(javafx.scene.Node node, String styleClass) {
        ArrayList<String> buttons = new ArrayList<>();
        collectButtonTextsWithStyle(node, styleClass, buttons);
        return buttons;
    }

    private static void collectSectionTitles(javafx.scene.Node node, List<String> titles) {
        if (node == null) {
            return;
        }
        if (node instanceof Label label && label.getStyleClass().contains("debug-section-title")) {
            titles.add(label.getText());
        }
        if (node instanceof ScrollPane scrollPane) {
            collectSectionTitles(scrollPane.getContent(), titles);
        }
        if (node instanceof SplitPane splitPane) {
            splitPane.getItems().forEach(item -> collectSectionTitles(item, titles));
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectSectionTitles(child, titles));
        }
    }

    private static void collectLabelsWithStyle(javafx.scene.Node node, String styleClass, List<String> labels) {
        if (node == null) {
            return;
        }
        if (node instanceof Label label && label.getStyleClass().contains(styleClass)) {
            labels.add(label.getText());
        }
        if (node instanceof ScrollPane scrollPane) {
            collectLabelsWithStyle(scrollPane.getContent(), styleClass, labels);
        }
        if (node instanceof SplitPane splitPane) {
            splitPane.getItems().forEach(item -> collectLabelsWithStyle(item, styleClass, labels));
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectLabelsWithStyle(child, styleClass, labels));
        }
    }

    private static void collectButtonTextsWithStyle(javafx.scene.Node node, String styleClass, List<String> buttons) {
        if (node == null) {
            return;
        }
        if (node instanceof Button button && button.getStyleClass().contains(styleClass)) {
            buttons.add(button.getText());
        }
        if (node instanceof ScrollPane scrollPane) {
            collectButtonTextsWithStyle(scrollPane.getContent(), styleClass, buttons);
        }
        if (node instanceof SplitPane splitPane) {
            splitPane.getItems().forEach(item -> collectButtonTextsWithStyle(item, styleClass, buttons));
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectButtonTextsWithStyle(child, styleClass, buttons));
        }
    }

    private static Label textContaining(javafx.scene.Node node, String text) {
        if (node instanceof Label label && label.getText().contains(text)) {
            return label;
        }
        if (node instanceof ScrollPane scrollPane) {
            return textContaining(scrollPane.getContent(), text);
        }
        if (node instanceof SplitPane splitPane) {
            for (javafx.scene.Node item : splitPane.getItems()) {
                Label found = textContaining(item, text);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Label found = textContaining(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static javafx.scene.Node directChildWithStyle(Parent parent, String styleClass) {
        return parent.getChildrenUnmodifiable().stream()
                .filter(child -> child.getStyleClass().contains(styleClass))
                .findFirst()
                .orElse(null);
    }

    private static boolean containsNode(javafx.scene.Node node, Class<?> type) {
        if (type.isInstance(node)) {
            return true;
        }
        if (node instanceof SplitPane splitPane) {
            return splitPane.getItems().stream().anyMatch(child -> containsNode(child, type));
        }
        if (node instanceof ScrollPane scrollPane) {
            return containsNode(scrollPane.getContent(), type);
        }
        if (node instanceof Parent parent) {
            return parent.getChildrenUnmodifiable().stream().anyMatch(child -> containsNode(child, type));
        }
        return false;
    }

    private static boolean containsStyle(javafx.scene.Node node, String styleClass) {
        if (node == null) {
            return false;
        }
        if (node.getStyleClass().contains(styleClass)) {
            return true;
        }
        if (node instanceof ScrollPane scrollPane) {
            return containsStyle(scrollPane.getContent(), styleClass);
        }
        if (node instanceof SplitPane splitPane) {
            return splitPane.getItems().stream().anyMatch(child -> containsStyle(child, styleClass));
        }
        if (node instanceof Parent parent) {
            return parent.getChildrenUnmodifiable().stream().anyMatch(child -> containsStyle(child, styleClass));
        }
        return false;
    }

    private static List<Circle> circlesWithStyle(javafx.scene.Node node, String styleClass) {
        ArrayList<Circle> circles = new ArrayList<>();
        collectCirclesWithStyle(node, styleClass, circles);
        return circles;
    }

    private static Circle circleWithAccessibleText(javafx.scene.Node node, String styleClass, String accessibleText) {
        return circlesWithStyle(node, styleClass).stream()
                .filter(circle -> accessibleText.equals(circle.getAccessibleText()))
                .findFirst()
                .orElse(null);
    }

    private static Rectangle rectangleWithAccessibleText(javafx.scene.Node node, String styleClass, String accessibleText) {
        return rectanglesWithStyle(node, styleClass).stream()
                .filter(rectangle -> accessibleText.equals(rectangle.getAccessibleText()))
                .findFirst()
                .orElse(null);
    }

    private static void collectCirclesWithStyle(javafx.scene.Node node, String styleClass, List<Circle> circles) {
        if (node == null) {
            return;
        }
        if (node instanceof Circle circle && circle.getStyleClass().contains(styleClass)) {
            circles.add(circle);
        }
        if (node instanceof ScrollPane scrollPane) {
            collectCirclesWithStyle(scrollPane.getContent(), styleClass, circles);
        }
        if (node instanceof SplitPane splitPane) {
            splitPane.getItems().forEach(child -> collectCirclesWithStyle(child, styleClass, circles));
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectCirclesWithStyle(child, styleClass, circles));
        }
    }

    private static List<Rectangle> rectanglesWithStyle(javafx.scene.Node node, String styleClass) {
        ArrayList<Rectangle> rectangles = new ArrayList<>();
        collectRectanglesWithStyle(node, styleClass, rectangles);
        return rectangles;
    }

    private static void collectRectanglesWithStyle(javafx.scene.Node node, String styleClass, List<Rectangle> rectangles) {
        if (node == null) {
            return;
        }
        if (node instanceof Rectangle rectangle && rectangle.getStyleClass().contains(styleClass)) {
            rectangles.add(rectangle);
        }
        if (node instanceof ScrollPane scrollPane) {
            collectRectanglesWithStyle(scrollPane.getContent(), styleClass, rectangles);
        }
        if (node instanceof SplitPane splitPane) {
            splitPane.getItems().forEach(child -> collectRectanglesWithStyle(child, styleClass, rectangles));
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectRectanglesWithStyle(child, styleClass, rectangles));
        }
    }

    private static boolean containsNodeWithStyles(javafx.scene.Node node, String firstStyle, String secondStyle) {
        if (node == null) {
            return false;
        }
        if (node.getStyleClass().contains(firstStyle) && node.getStyleClass().contains(secondStyle)) {
            return true;
        }
        if (node instanceof ScrollPane scrollPane) {
            return containsNodeWithStyles(scrollPane.getContent(), firstStyle, secondStyle);
        }
        if (node instanceof SplitPane splitPane) {
            return splitPane.getItems().stream().anyMatch(child -> containsNodeWithStyles(child, firstStyle, secondStyle));
        }
        if (node instanceof Parent parent) {
            return parent.getChildrenUnmodifiable().stream().anyMatch(child -> containsNodeWithStyles(child, firstStyle, secondStyle));
        }
        return false;
    }

    private static ScrollPane scrollPaneWithContentStyle(javafx.scene.Node node, String styleClass) {
        if (node instanceof ScrollPane scrollPane && containsStyle(scrollPane.getContent(), styleClass)) {
            return scrollPane;
        }
        if (node instanceof SplitPane splitPane) {
            for (javafx.scene.Node item : splitPane.getItems()) {
                ScrollPane found = scrollPaneWithContentStyle(item, styleClass);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                ScrollPane found = scrollPaneWithContentStyle(child, styleClass);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
