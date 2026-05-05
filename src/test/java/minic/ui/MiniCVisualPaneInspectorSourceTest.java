package minic.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiSourceSpanDto;
import minic.uiapi.UiStageVisualDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCVisualPaneInspectorSourceTest {
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
    void inspectorUsesPreprocessedSourceWhenRangeBelongsToVisualSource() throws Exception {
        startJavafx();
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("main.mc", "#define VALUE 123\nreturn VALUE;");
        MiniCVisualPane pane = new MiniCVisualPane(viewModel);

        String preprocessedSource = "return 123;";
        UiSourceSpanDto range = new UiSourceSpanDto("main.mc", 7, 10, 1, 8, 1, 11);
        UiStageVisualDto visual = new UiStageVisualDto(
                "lexer",
                "lexer",
                preprocessedSource,
                List.of(),
                List.of(new UiLexerTokenVisualDto("INTEGER_LITERAL", "123", range, true)),
                null,
                null,
                false,
                List.of(),
                List.of()
        );

        MiniCHoverInspectorContent content = inspectorContent(pane, range, visual);

        assertThat(content.source()).isEqualTo(preprocessedSource);
        MiniCHoverInspector inspector = new MiniCHoverInspector();
        MiniCBottomPanel panel = new MiniCBottomPanel(inspector);
        inspector.show(content);

        String masked = labels(panel).stream()
                .filter(label -> label.getStyleClass().contains("masked"))
                .map(Label::getText)
                .reduce("", String::concat);
        assertThat(masked).isEqualTo("123");
    }

    private static MiniCHoverInspectorContent inspectorContent(
            MiniCVisualPane pane,
            UiSourceSpanDto range,
            UiStageVisualDto visual
    ) throws Exception {
        Method method = MiniCVisualPane.class.getDeclaredMethod(
                "inspectorContent",
                String.class,
                List.class,
                UiSourceSpanDto.class,
                String.class,
                UiStageVisualDto.class
        );
        method.setAccessible(true);
        return (MiniCHoverInspectorContent) method.invoke(
                pane,
                "Token INTEGER_LITERAL",
                List.of("kind: INTEGER_LITERAL"),
                range,
                "literal",
                visual
        );
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
