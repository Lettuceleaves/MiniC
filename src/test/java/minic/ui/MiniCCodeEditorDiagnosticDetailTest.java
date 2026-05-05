package minic.ui;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import minic.uiapi.UiDiagnosticDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCCodeEditorDiagnosticDetailTest {
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
    void diagnosticDetailIncludesLineByteOffsetAndChineseReason() throws Exception {
        startJavafx();
        MiniCCodeEditor editor = new MiniCCodeEditor();
        Method method = MiniCCodeEditor.class.getDeclaredMethod(
                "diagnosticDetail",
                String.class,
                UiDiagnosticDto.class
        );
        method.setAccessible(true);

        String source = "int main() {\n    return ;\n}";
        Label label = (Label) method.invoke(
                editor,
                source,
                new UiDiagnosticDto("PAR001", "ERROR", "期望表达式", "live.mc", 20, 21)
        );

        assertThat(label.getText())
                .contains("错误位置: 第 2 行")
                .contains("第 8 个字节")
                .contains("offset 20..21")
                .contains("原因: 期望表达式")
                .contains("请检查该位置附近");
    }

    @Test
    void completionListIsPlacedAboveDiagnosticDetailsWhenBothAreVisible() throws Exception {
        startJavafx();
        MiniCCodeEditor editor = new MiniCCodeEditor();
        editor.resize(600, 400);

        @SuppressWarnings("unchecked")
        ListView<String> completionList = (ListView<String>) field(editor, "completionList");
        VBox diagnosticDetails = (VBox) field(editor, "diagnosticDetails");
        diagnosticDetails.getChildren().setAll(new Label("错误位置: 第 1 行，第 1 个字节。原因: 期望表达式。"));
        diagnosticDetails.setVisible(true);
        completionList.getItems().setAll(List.of("return", "runtime", "result"));
        completionList.setVisible(true);

        invoke(editor, "layoutDiagnosticDetails");

        assertThat(completionList.getLayoutY() + completionList.getHeight())
                .isLessThanOrEqualTo(diagnosticDetails.getLayoutY());
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = MiniCCodeEditor.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = MiniCCodeEditor.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
}
