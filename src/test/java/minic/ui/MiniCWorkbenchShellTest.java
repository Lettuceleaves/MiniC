package minic.ui;

import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Parent;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWorkbenchShellTest {
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
    void keepsShellAsUiLayerType() {
        assertThat(MiniCWorkbenchShell.class.getPackageName()).isEqualTo("minic.ui");
        assertThat(MiniCWorkbenchApp.class.getResource("/minic/ui/workbench.css")).isNotNull();
    }

    @Test
    void switchesMainContentBetweenSourceAndVisualPipelineModes() {
        startJavafx();
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        MiniCWorkbenchShell shell = new MiniCWorkbenchShell(viewModel);
        Parent root = shell.createRoot();
        StackPane mainContent = lookup(root, StackPane.class, "split");
        assertThat(labels(root).stream().map(Label::getText))
                .contains("C  untitled-1.mc")
                .doesNotContain("workbench.visual")
                .doesNotContain("Problems    Output    Debug Console    Terminal")
                .doesNotContain("Problems / Output / Terminal");
        assertThat(button(root, "打开")).isNotNull();
        assertThat(button(root, "保存")).isNotNull();
        assertThat(button(root, "到执行")).isNotNull();
        assertInspectorButton(root, "下一步");
        assertInspectorButton(root, "下一阶段");
        assertInspectorButton(root, "到执行");
        assertInspectorButton(root, "播放");
        assertInspectorButton(root, "2x");
        assertInspectorButton(root, "暂停");
        assertThat(button(root, "+")).isNotNull();

        assertThat(visibleChildren(mainContent)).singleElement()
                .satisfies(node -> assertThat(containsNode(node, MiniCSourceLoaderView.class)).isTrue());

        viewModel.loadSource("main.mc", "int main() { return 0; }");
        viewModel.startSession();

        assertThat(visibleChildren(mainContent)).singleElement()
                .satisfies(node -> assertThat(containsNode(node, MiniCSourceLoaderView.class)).isTrue());

        viewModel.next();

        assertThat(visibleChildren(mainContent)).singleElement()
                .isInstanceOf(MiniCVisualPane.class);

        viewModel.selectVisualStage("source");

        assertThat(visibleChildren(mainContent)).singleElement()
                .satisfies(node -> assertThat(containsNode(node, MiniCSourceLoaderView.class)).isTrue());
    }

    @Test
    void createsIndependentDocumentTabs() {
        startJavafx();
        MiniCWorkbenchViewModel first = new MiniCWorkbenchViewModel();
        MiniCWorkbenchShell shell = new MiniCWorkbenchShell(first);
        Parent root = shell.createRoot();

        button(root, "+").fire();

        assertThat(labels(root).stream().map(Label::getText))
                .contains("C  untitled-1.mc", "C  untitled-2.mc");
        first.loadSource("first.mc", "int main() { return 1; }");
        first.startSession();

        assertThat(first.sessionStartedProperty().get()).isTrue();
    }

    @Test
    void closesDocumentTabsAndKeepsOneUntitledTab() {
        startJavafx();
        MiniCWorkbenchShell shell = new MiniCWorkbenchShell(new MiniCWorkbenchViewModel());
        Parent root = shell.createRoot();

        button(root, "+").fire();
        assertThat(labels(root).stream().map(Label::getText))
                .contains("C  untitled-1.mc", "C  untitled-2.mc");

        closeButtonForTab(root, "C  untitled-2.mc").fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY,
                1,
                false, false, false, false,
                true, false, false, true,
                false, false, null
        ));
        assertThat(labels(root).stream().map(Label::getText))
                .contains("C  untitled-1.mc")
                .doesNotContain("C  untitled-2.mc");

        closeButtonForTab(root, "C  untitled-1.mc").fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY,
                1,
                false, false, false, false,
                true, false, false, true,
                false, false, null
        ));
        assertThat(labels(root).stream().map(Label::getText))
                .contains("C  untitled-3.mc")
                .doesNotContain("C  untitled-1.mc", "C  untitled-2.mc");
        assertThat(button(root, "打开")).isNotNull();
    }

    @Test
    void reordersDocumentTabs() {
        startJavafx();
        MiniCWorkbenchShell shell = new MiniCWorkbenchShell(new MiniCWorkbenchViewModel());
        Parent root = shell.createRoot();

        button(root, "+").fire();
        button(root, "+").fire();

        assertThat(tabTitles(root))
                .containsExactly("C  untitled-1.mc", "C  untitled-2.mc", "C  untitled-3.mc");

        shell.reorderDocumentTabsForTesting(0, 2);

        assertThat(tabTitles(root))
                .containsExactly("C  untitled-2.mc", "C  untitled-3.mc", "C  untitled-1.mc");
    }

    @Test
    void renamesDocumentTabOnDoubleClick() {
        startJavafx();
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        MiniCWorkbenchShell shell = new MiniCWorkbenchShell(viewModel);
        Parent root = shell.createRoot();

        tabForTitle(root, "C  untitled-1.mc").fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY,
                2,
                false, false, false, false,
                true, false, false, true,
                false, false, null
        ));

        TextField rename = lookup(root, TextField.class, "tab-rename");
        assertThat(rename).isNotNull();
        rename.setText("renamed.mc");
        rename.fireEvent(new javafx.event.ActionEvent());

        assertThat(tabTitles(root)).containsExactly("C  renamed.mc");
        assertThat(viewModel.sourceNameProperty().get()).isEqualTo("renamed.mc");
    }

    @Test
    void switchesActivitySectionsFromLeftMenu() {
        startJavafx();
        MiniCWorkbenchShell shell = new MiniCWorkbenchShell(new MiniCWorkbenchViewModel());
        Parent root = shell.createRoot();

        assertThat(activityItems(root).stream().map(Label::getAccessibleText))
                .containsExactly("代码区", "调试", "设置", "信息");
        assertThat(button(root, "打开")).isNotNull();

        activityItem(root, "调试").fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY,
                1,
                false, false, false, false,
                true, false, false, true,
                false, false, null
        ));
        assertThat(labels(root).stream().map(Label::getText))
                .doesNotContain("调试视图将在后续实现。");
        assertThat(button(root, "启动")).isNotNull();
        assertThat(button(root, "运行到断点")).isNotNull();
        assertThat(button(root, "单退")).isNotNull();
        assertThat(button(root, "拆分")).isNotNull();
        assertThat(button(root, "确认输入")).isNull();
        assertThat(labels(root).stream().map(Label::getText)).doesNotContain("无输入");
        assertThat(button(root, "开始")).isNull();
        assertThat(button(root, "打开")).isNull();
        assertThat(button(root, "保存")).isNull();

        activityItem(root, "设置").fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY,
                1,
                false, false, false, false,
                true, false, false, true,
                false, false, null
        ));
        assertThat(labels(root).stream().map(Label::getText))
                .contains("设置", "修改 config/theme.json 后点击下方按钮刷新主题。");

        activityItem(root, "信息").fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY,
                1,
                false, false, false, false,
                true, false, false, true,
                false, false, null
        ));
        assertThat(labels(root).stream().map(Label::getText))
                .contains("信息", "信息视图将在后续实现。");

        activityItem(root, "代码区").fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY,
                1,
                false, false, false, false,
                true, false, false, true,
                false, false, null
        ));
        assertThat(button(root, "打开")).isNotNull();
        assertThat(labels(root).stream().map(Label::getText))
                .contains("C  untitled-1.mc");
    }

    private static java.util.List<javafx.scene.Node> visibleChildren(StackPane pane) {
        return pane.getChildren().stream()
                .filter(child -> child.isVisible() && child.isManaged())
                .toList();
    }

    private static <T extends javafx.scene.Node> T lookup(javafx.scene.Node node, Class<T> type, String styleClass) {
        if (type.isInstance(node) && node.getStyleClass().contains(styleClass)) {
            return type.cast(node);
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                T found = lookup(child, type, styleClass);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean containsNode(javafx.scene.Node node, Class<? extends javafx.scene.Node> type) {
        if (type.isInstance(node)) {
            return true;
        }
        if (node instanceof SplitPane splitPane) {
            return splitPane.getItems().stream().anyMatch(child -> containsNode(child, type));
        }
        if (node instanceof Parent parent) {
            return parent.getChildrenUnmodifiable().stream().anyMatch(child -> containsNode(child, type));
        }
        return false;
    }

    private static java.util.List<Label> labels(javafx.scene.Node node) {
        java.util.ArrayList<Label> labels = new java.util.ArrayList<>();
        collectLabels(node, labels);
        return labels;
    }

    private static java.util.List<Label> activityItems(javafx.scene.Node node) {
        return labels(node).stream()
                .filter(label -> label.getStyleClass().contains("activity-item"))
                .toList();
    }

    private static Label activityItem(javafx.scene.Node node, String accessibleText) {
        return activityItems(node).stream()
                .filter(label -> accessibleText.equals(label.getAccessibleText()))
                .findFirst()
                .orElseThrow();
    }

    private static Label closeButtonForTab(javafx.scene.Node node, String title) {
        if (node instanceof Parent parent && node.getStyleClass().contains("tab")) {
            java.util.List<Label> childLabels = labels(parent);
            boolean titleMatches = childLabels.stream().anyMatch(label -> title.equals(label.getText()));
            if (titleMatches) {
                return childLabels.stream()
                        .filter(label -> label.getStyleClass().contains("tab-close"))
                        .findFirst()
                        .orElseThrow();
            }
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Label found = closeButtonForTab(child, title);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static javafx.scene.Node tabForTitle(javafx.scene.Node node, String title) {
        if (node instanceof Parent parent && node.getStyleClass().contains("tab")) {
            boolean titleMatches = labels(parent).stream().anyMatch(label -> title.equals(label.getText()));
            if (titleMatches) {
                return node;
            }
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                javafx.scene.Node found = tabForTitle(child, title);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static java.util.List<String> tabTitles(javafx.scene.Node node) {
        java.util.ArrayList<String> titles = new java.util.ArrayList<>();
        collectTabTitles(node, titles);
        return titles;
    }

    private static void collectTabTitles(javafx.scene.Node node, java.util.ArrayList<String> titles) {
        if (node instanceof Label label && label.getStyleClass().contains("tab-title")) {
            titles.add(label.getText());
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectTabTitles(child, titles));
        }
    }

    private static void collectLabels(javafx.scene.Node node, java.util.ArrayList<Label> labels) {
        if (node instanceof Label label) {
            labels.add(label);
        }
        if (node instanceof SplitPane splitPane) {
            splitPane.getItems().forEach(child -> collectLabels(child, labels));
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectLabels(child, labels));
        }
    }

    private static Button button(javafx.scene.Node node, String text) {
        if (node instanceof Button button && button.getText().equals(text)) {
            return button;
        }
        if (node instanceof SplitPane splitPane) {
            for (javafx.scene.Node child : splitPane.getItems()) {
                Button found = button(child, text);
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

    private static void assertInspectorButton(javafx.scene.Node node, String text) {
        Button button = button(node, text);
        assertThat(button).isNotNull();
        assertThat(button.getStyleClass()).contains("inspector-control-button");
        assertThat(button.getPrefWidth()).isEqualTo(78);
        assertThat(button.getMinWidth()).isEqualTo(78);
        assertThat(button.getMaxWidth()).isEqualTo(78);
        assertThat(button.getPrefHeight()).isEqualTo(28);
    }
}
