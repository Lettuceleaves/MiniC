package minic.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
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

            assertThat(button(pane, "拆分")).isNotNull();
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

            assertThat(labelsWithStyle(pane, "debug-process-title"))
                    .contains("code", "static/data", "stack", "heap", "io");
            assertThat(labelsWithStyle(pane, "debug-section-title"))
                    .contains("visual structures", "warnings");
        });
    }

    private static void runOnFxThread(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
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
    }

    private static Button button(javafx.scene.Node node, String text) {
        if (node instanceof Button button && button.getText().equals(text)) {
            return button;
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
        if (node instanceof TabPane tabPane) {
            for (javafx.scene.control.Tab tab : tabPane.getTabs()) {
                Label found = label(tab.getContent(), text);
                if (found != null) {
                    return found;
                }
            }
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

    private static void collectSectionTitles(javafx.scene.Node node, List<String> titles) {
        if (node == null) {
            return;
        }
        if (node instanceof Label label && label.getStyleClass().contains("debug-section-title")) {
            titles.add(label.getText());
        }
        if (node instanceof TabPane tabPane) {
            tabPane.getTabs().forEach(tab -> collectSectionTitles(tab.getContent(), titles));
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
        if (node instanceof TabPane tabPane) {
            tabPane.getTabs().forEach(tab -> collectLabelsWithStyle(tab.getContent(), styleClass, labels));
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
}
