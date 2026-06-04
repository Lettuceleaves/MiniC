package minic.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import minic.uiapi.UiAssemblyLineVisualDto;
import minic.uiapi.UiIrLineVisualDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCIrAssemblyHighlightRenderingTest {
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
    void visualPaneIrRowsRenderStyledTextSegments() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCVisualPane pane = new MiniCVisualPane(new MiniCWorkbenchViewModel());
            HBox row = invokeVisualIrRow(pane, new UiIrLineVisualDto(
                    3,
                    "%1 = call printf(format, 42)",
                    null,
                    true
            ));

            TextFlow flow = textFlow(row);

            assertThat(flow.getStyleClass()).contains("assembly-text", "active");
            assertThat(textWithValue(flow, "call").getStyleClass()).contains("mc-text-code-keyword");
            assertThat(textWithValue(flow, "%1").getStyleClass()).contains("mc-text-code-identifier");
            assertThat(textWithValue(flow, "42").getStyleClass()).contains("mc-text-code-literal");
        });
    }

    @Test
    void visualPaneAssemblyRowsRenderStyledTextSegments() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCVisualPane pane = new MiniCVisualPane(new MiniCWorkbenchViewModel());
            HBox row = invokeVisualAssemblyRow(pane, new MiniCAssemblyTextLine(
                    7,
                    "mov rcx, OFFSET FLAT:$str0",
                    ".text",
                    "",
                    "INSTRUCTION",
                    null,
                    false
            ));

            TextFlow flow = textFlow(row);

            assertThat(flow.getStyleClass()).contains("assembly-text");
            assertThat(textWithValue(flow, "mov").getStyleClass()).contains("mc-text-code-keyword");
            assertThat(textWithValue(flow, "rcx").getStyleClass()).contains("mc-text-code-identifier");
            assertThat(textWithValue(flow, "$str0").getStyleClass()).contains("mc-text-code-type");
        });
    }

    @Test
    void debugIrAndAssemblyRowsRenderStyledTextSegments() {
        startJavafx();
        runOnFxThread(() -> {
            MiniCDebugPane pane = new MiniCDebugPane(new MiniCWorkbenchViewModel());
            HBox irRow = invokeDebugIrRow(pane, new UiIrLineVisualDto(
                    2,
                    "return %1",
                    null,
                    true
            ));
            HBox asmRow = invokeDebugAsmRow(pane, new UiAssemblyLineVisualDto(
                    9,
                    "ret 0",
                    "INSTRUCTION",
                    ".text",
                    "",
                    true
            ));

            assertThat(textFlow(irRow).getStyleClass()).contains("debug-code-text");
            assertThat(textWithValue(textFlow(irRow), "return").getStyleClass()).contains("mc-text-code-keyword");
            assertThat(textFlow(asmRow).getStyleClass()).contains("debug-code-text");
            assertThat(textWithValue(textFlow(asmRow), "ret").getStyleClass()).contains("mc-text-code-keyword");
        });
    }

    private static HBox invokeVisualIrRow(MiniCVisualPane pane, UiIrLineVisualDto line) throws Exception {
        Method method = MiniCVisualPane.class.getDeclaredMethod("irRow", UiIrLineVisualDto.class, minic.uiapi.UiStageVisualDto.class);
        method.setAccessible(true);
        return (HBox) method.invoke(pane, line, null);
    }

    private static HBox invokeVisualAssemblyRow(MiniCVisualPane pane, MiniCAssemblyTextLine line) throws Exception {
        Method method = MiniCVisualPane.class.getDeclaredMethod("assemblyRow", MiniCAssemblyTextLine.class, minic.uiapi.UiStageVisualDto.class);
        method.setAccessible(true);
        return (HBox) method.invoke(pane, line, null);
    }

    private static HBox invokeDebugIrRow(MiniCDebugPane pane, UiIrLineVisualDto line) throws Exception {
        Method method = MiniCDebugPane.class.getDeclaredMethod("irLineRow", UiIrLineVisualDto.class);
        method.setAccessible(true);
        return (HBox) method.invoke(pane, line);
    }

    private static HBox invokeDebugAsmRow(MiniCDebugPane pane, UiAssemblyLineVisualDto line) throws Exception {
        Method method = MiniCDebugPane.class.getDeclaredMethod("asmLineRow", UiAssemblyLineVisualDto.class);
        method.setAccessible(true);
        return (HBox) method.invoke(pane, line);
    }

    private static TextFlow textFlow(HBox row) {
        return row.getChildren().stream()
                .filter(TextFlow.class::isInstance)
                .map(TextFlow.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static Text textWithValue(TextFlow flow, String text) {
        return textNodes(flow).stream()
                .filter(node -> text.equals(node.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static List<Text> textNodes(TextFlow flow) {
        return flow.getChildren().stream()
                .filter(Text.class::isInstance)
                .map(Text.class::cast)
                .toList();
    }

    private static void runOnFxThread(ThrowingRunnable action) {
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

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
