package minic.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.Parent;
import org.junit.jupiter.api.Test;

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
            assertThat(button(pane, "启动")).isNotNull();
            assertThat(button(pane, "拆分")).isNotNull();
            assertThat(button(pane, "元数据")).isNotNull();
            assertThat(button(pane, "数据结构")).isNotNull();
            assertThat(button(pane, "AST")).isNotNull();
            assertThat(button(pane, "IR")).isNotNull();
            assertThat(button(pane, "ASM")).isNotNull();
            assertThat(button(pane, "设断点")).isNotNull();
            assertThat(button(pane, "清断点")).isNotNull();
            assertThat(button(pane, "快进")).isNotNull();
            assertThat(button(pane, "运行到断点")).isNotNull();
            assertThat(button(pane, "单步")).isNotNull();
            assertThat(button(pane, "步入")).isNotNull();
            assertThat(button(pane, "步返")).isNotNull();
            assertThat(button(pane, "暂停")).isNotNull();
            assertThat(button(pane, "重启")).isNotNull();
            assertThat(button(pane, "关闭")).isNotNull();
            assertThat(button(pane, "单退")).isNotNull();
            assertThat(button(pane, "步退")).isNotNull();
            assertThat(button(pane, "返回调用处")).isNotNull();
            assertThat(viewModel.debugStartedProperty().get()).isFalse();

            button(pane, "拆分").fire();

            assertThat(viewModel.debugStartedProperty().get()).isFalse();
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
                    .contains("code", "static/data", "stack", "heap", "io");
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

            button(pane, "启动").fire();

            assertThat(viewModel.debugStartedProperty().get()).isTrue();
            assertThat(viewModel.sessionStartedProperty().get()).isFalse();
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
