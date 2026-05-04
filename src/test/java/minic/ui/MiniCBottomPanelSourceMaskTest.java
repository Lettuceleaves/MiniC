package minic.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import minic.uiapi.UiSourceSpanDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCBottomPanelSourceMaskTest {
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
    void masksOnlyExactSourceCharacters() {
        startJavafx();
        MiniCHoverInspector inspector = new MiniCHoverInspector();
        MiniCBottomPanel panel = new MiniCBottomPanel(inspector);

        inspector.show(new MiniCHoverInspectorContent(
                "AST node NameExpr",
                List.of("kind: NameExpr"),
                "int i = 0;",
                new UiSourceSpanDto("test.mc", 4, 5, 1, 5, 1, 6),
                "single identifier"
        ));

        List<Label> masked = labels(panel).stream()
                .filter(label -> label.getStyleClass().contains("masked"))
                .toList();
        assertThat(masked).singleElement().extracting(Label::getText).isEqualTo("i");
    }

    private static List<Label> labels(javafx.scene.Node node) {
        ArrayList<Label> labels = new ArrayList<>();
        collectLabels(node, labels);
        return labels;
    }

    private static void collectLabels(javafx.scene.Node node, ArrayList<Label> labels) {
        if (node instanceof Label label) {
            labels.add(label);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectLabels(child, labels));
        }
        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            collectLabels(scrollPane.getContent(), labels);
        }
    }
}
