package minic.ui;

import javafx.application.Platform;
import javafx.scene.control.Label;
import minic.uiapi.UiDiagnosticDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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
}
