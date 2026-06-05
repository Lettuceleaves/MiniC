package minic.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCInfoViewRegressionTest {
    @Test
    void guideMarkdownExpandsRuntimeSystemArchitecturePlaceholders() throws Exception {
        Path guide = Files.createTempFile("minic-guide-", ".md");
        Files.writeString(guide, """
                # Test Guide

                - System: {{system.os.name}} {{system.os.version}}
                - Architecture: {{system.os.arch}}
                - Java: {{java.version}}
                """, StandardCharsets.UTF_8);

        String markdown = MiniCGuideDocument.load(guide);

        assertThat(markdown).contains(System.getProperty("os.arch"));
        assertThat(markdown).contains(System.getProperty("os.name"));
        assertThat(markdown).contains(System.getProperty("java.version"));
        assertThat(markdown).doesNotContain("{{");
    }

    @Test
    void informationActivityRendersGuideMarkdownInScrollableView() throws Exception {
        ensureFxStarted();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<ScrollPane> infoScroll = new AtomicReference<>();
        try {
            runFx(() -> {
                MiniCWorkbenchShell shell = new MiniCWorkbenchShell(new MiniCWorkbenchViewModel());
                Parent root = shell.createRoot();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 920, 560));
                stage.show();
                stageRef.set(stage);

                Label infoButton = root.lookupAll(".activity-item").stream()
                        .filter(Label.class::isInstance)
                        .map(Label.class::cast)
                        .filter(label -> "信息".equals(label.getAccessibleText()))
                        .findFirst()
                        .orElseThrow();
                infoButton.fireEvent(primaryClick());

                ScrollPane center = (ScrollPane) ((BorderPane) root).getCenter();
                infoScroll.set(center);
            });

            assertThat(infoScroll.get()).isInstanceOf(MiniCInfoView.class);
            assertThat(infoScroll.get().getStyleClass()).contains("info-scroll");
            assertThat(infoScroll.get().getContent().lookupAll(".info-heading-1")).isNotEmpty();
            assertThat(infoScroll.get().getContent().lookupAll(".info-code-block")).isNotEmpty();
        } finally {
            if (stageRef.get() != null) {
                runFx(() -> stageRef.get().close());
            }
        }
    }

    @Test
    void markdownCodeFenceReusesMiniCSourceSyntaxHighlighting() throws Exception {
        ensureFxStarted();
        AtomicReference<VBox> contentRef = new AtomicReference<>();
        runFx(() -> contentRef.set(new MiniCMarkdownRenderer().render("""
                # Guide

                ```c
                int main() {
                    return 42;
                }
                ```
                """)));

        TextFlow codeBlock = (TextFlow) contentRef.get().lookup(".info-code-block");

        assertThat(codeBlock).isNotNull();
        assertThat(codeBlock.getChildren())
                .anySatisfy(node -> assertThat(node.getStyleClass()).contains("mc-text-code-keyword", "token-keyword"))
                .anySatisfy(node -> assertThat(node.getStyleClass()).contains("mc-text-code-literal", "token-literal"));
    }

    private static MouseEvent primaryClick() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null
        );
    }

    private static void ensureFxStarted() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
            Platform.runLater(latch::countDown);
        }
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private static void runFx(Runnable runnable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
