package minic.ui;

import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Parent;
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
        assertThat(button(root, "Open")).isNotNull();
        assertThat(button(root, "Save")).isNotNull();
        assertThat(button(root, "+")).isNotNull();

        assertThat(visibleChildren(mainContent)).singleElement()
                .satisfies(node -> assertThat(containsNode(node, MiniCSourceLoaderView.class)).isTrue());

        viewModel.loadSource("main.mc", "int main() { return 0; }");
        viewModel.startSession();

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

    private static void collectLabels(javafx.scene.Node node, java.util.ArrayList<Label> labels) {
        if (node instanceof Label label) {
            labels.add(label);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectLabels(child, labels));
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
}
