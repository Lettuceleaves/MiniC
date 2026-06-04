package minic.ui;

import javafx.application.Platform;
import minic.runtime.debug.visual.VisualKind;
import minic.uiapi.ExplanationTemplates;
import minic.uiapi.UiAstNodeVisualDto;
import minic.uiapi.UiIrLineVisualDto;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiSourceSpanDto;
import org.junit.jupiter.api.Test;

import java.util.Map;
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
                .contains("Parser 的输入")
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

    @Test
    void rendersRuntimeValuesInsideTokenRoleTemplate() throws Exception {
        String source = "int answer = 42;\n";
        String explanation = explainWithSource("explainToken", new UiLexerTokenVisualDto(
                "IDENTIFIER",
                "answer",
                new UiSourceSpanDto("main.mc", 4, 10, 1, 5, 1, 11),
                4,
                10,
                1,
                5,
                1,
                11,
                true
        ), source);

        assertThat(explanation)
                .contains("当前这个名字写作 `answer`")
                .contains("源码片段 `answer`")
                .doesNotContain("${text}", "${source}");
    }

    @Test
    void rendersRuntimeValuesInsideAstRoleTemplate() throws Exception {
        String source = """
                int main() {
                    return 0;
                }
                """;
        String explanation = explainWithSource("explainAstNode", new UiAstNodeVisualDto(
                "ast-1",
                "FunctionDecl main",
                "FunctionDecl",
                new UiSourceSpanDto("main.mc", 0, source.length(), 1, 1, 3, 2),
                true,
                List.of()
        ), source);

        assertThat(explanation)
                .contains("界面标签是 `FunctionDecl main`")
                .contains("当前源码片段")
                .contains("int main()")
                .doesNotContain("${label}", "${source}");
    }

    @Test
    void rendersRuntimeValuesInsideIrRoleTemplate() throws Exception {
        String source = "x = add(x, 2);\n";
        String explanation = explainWithSource("explainIrLine", new UiIrLineVisualDto(
                3,
                "%1 = call add(x, 2)",
                new UiSourceSpanDto("main.mc", 0, 14, 1, 1, 1, 15),
                true
        ), source);

        assertThat(explanation)
                .contains("这行 IR 文本是 `%1 = call add(x, 2)`")
                .contains("对应的 C 源码片段是 `x = add(x, 2);`")
                .doesNotContain("${text}", "${source}");
    }

    @Test
    void rendersRuntimeValuesInsideAssemblyRoleTemplate() throws Exception {
        String source = "x = add(x, 2);\n";
        String explanation = explainWithSource("explainAssemblyLine", new MiniCAssemblyTextLine(
                7,
                "mov rcx, rax",
                ".text",
                "",
                "INSTRUCTION",
                new UiSourceSpanDto("main.mc", 0, 14, 1, 1, 1, 15),
                true
        ), source);

        assertThat(explanation)
                .contains("当前汇编文本是 `mov rcx, rax`")
                .contains("对应的 C 源码片段是 `x = add(x, 2);`")
                .doesNotContain("${text}", "${source}");
    }

    @Test
    void rendersBeginnerDebugVisualTemplatesForEveryVisualKind() {
        Map<String, String> variables = Map.ofEntries(
                Map.entry("root", "points"),
                Map.entry("typeName", "struct Point[2]"),
                Map.entry("cExpression", "points[1].x"),
                Map.entry("lvaluePath", "points[1].x"),
                Map.entry("oldValue", "3"),
                Map.entry("newValue", "7"),
                Map.entry("address", "stack:0x40"),
                Map.entry("pointerTarget", "stack:0x24"),
                Map.entry("fieldName", "x"),
                Map.entry("indexPath", "[1]"),
                Map.entry("row", "1"),
                Map.entry("column", "0")
        );

        for (VisualKind kind : VisualKind.values()) {
            String explanation = ExplanationTemplates.render("debug-visual", kind.name(), variables);

            assertThat(explanation)
                    .as(kind.name())
                    .contains("points")
                    .contains("struct Point[2]")
                    .contains("C 代码")
                    .contains("例子")
                    .contains("类比")
                    .doesNotContain("{{root}}", "{{typeName}}", "{{cExpression}}", "{{lvaluePath}}",
                            "{{oldValue}}", "{{newValue}}", "{{address}}", "{{pointerTarget}}",
                            "{{fieldName}}", "{{indexPath}}", "${root}", "${typeName}");
        }
    }

    private String explain(String methodName, Object argument) throws Exception {
        return explainWithSource(methodName, argument, "");
    }

    private String explainWithSource(String methodName, Object argument, String source) throws Exception {
        startJavafx();
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        if (!source.isBlank()) {
            viewModel.loadSource("main.mc", source);
        }
        MiniCVisualPane pane = new MiniCVisualPane(viewModel);
        Method method = MiniCVisualPane.class.getDeclaredMethod(methodName, argument.getClass());
        method.setAccessible(true);
        return (String) method.invoke(pane, argument);
    }
}
