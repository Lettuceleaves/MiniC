package minic.ui;

import javafx.application.Platform;
import minic.uiapi.UiAstNodeVisualDto;
import minic.uiapi.UiIrLineVisualDto;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiSourceSpanDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCVisualPaneExplanationTest {
    private static boolean javafxStarted;
    private final UiSourceSpanDto range = new UiSourceSpanDto("main.mc", 0, 3, 1, 1, 1, 4);

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
    void explainsTokenRoleWithMetadataAndCompilerPurpose() throws Exception {
        String explanation = explain("explainToken", new UiLexerTokenVisualDto(
                "INT",
                "int",
                range,
                0,
                3,
                1,
                1,
                1,
                4,
                true
        ));

        assertThat(explanation)
                .contains("当前 token 是 INT")
                .contains("类型关键字")
                .contains("解释:")
                .contains("用途:")
                .contains("parser 的输入")
                .contains("1:1 到 1:4")
                .doesNotContain("功能:", "为什么有用:");
    }

    @Test
    void explainsAstNodeRoleAndWhyItMatters() throws Exception {
        String explanation = explain("explainAstNode", new UiAstNodeVisualDto(
                "ast-1",
                "FunctionDecl main",
                "FunctionDecl",
                range,
                true,
                List.of()
        ));

        assertThat(explanation)
                .contains("当前节点是 FunctionDecl")
                .contains("返回类型、函数名、参数列表")
                .contains("语义分析和 IR lowering");
    }

    @Test
    void explainsIrLineOperation() throws Exception {
        String explanation = explain("explainIrLine", new UiIrLineVisualDto(
                3,
                "%1 = call printf(format, value)",
                range,
                true
        ));

        assertThat(explanation)
                .contains("第 3 行")
                .contains("call 表示函数调用")
                .contains("生成汇编");
    }

    @Test
    void explainsAssemblyLineInstruction() throws Exception {
        String explanation = explain("explainAssemblyLine", new MiniCAssemblyTextLine(
                7,
                "mov rcx, OFFSET FLAT:$str0",
                ".text",
                "",
                "INSTRUCTION",
                range,
                true
        ));

        assertThat(explanation)
                .contains("第 7 行")
                .contains("mov 负责复制数据")
                .contains("寄存器移动");
    }

    private String explain(String methodName, Object argument) throws Exception {
        startJavafx();
        MiniCVisualPane pane = new MiniCVisualPane(new MiniCWorkbenchViewModel());
        Method method = MiniCVisualPane.class.getDeclaredMethod(methodName, argument.getClass());
        method.setAccessible(true);
        return (String) method.invoke(pane, argument);
    }
}
