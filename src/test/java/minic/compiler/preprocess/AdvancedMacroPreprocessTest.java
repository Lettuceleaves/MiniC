package minic.compiler.preprocess;

import minic.compiler.SourceFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdvancedMacroPreprocessTest {
    @Test
    void expandsObjectFunctionAndVariadicMacrosRecursively() {
        String source = """
                #define BASE 3
                #define ADD(a, b) ((a) + (b))
                #define APPLY(fn, ...) fn(__VA_ARGS__)
                #define WRAP(...) APPLY(ADD, __VA_ARGS__)
                int value = WRAP(BASE, 4);
                #define EMPTY_CALL(...) sink(__VA_ARGS__)
                EMPTY_CALL()
                """;

        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("variadic.mc", source));
        String output = result.sourceFile().content();
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertTrue(output.contains("int value = ((3) + (4));"), output);
        assertTrue(output.contains("sink()"), output);
    }

    @Test
    void stringifiesRawArgumentsAndPastesTokensBeforeRescanning() {
        String source = """
                #define VALUE 42
                #define STR(x) #x
                #define XSTR(x) STR(x)
                #define CAT_RAW(a, b) a ## b
                #define CAT(a, b) CAT_RAW(a, b)
                #define PREFIX item
                char *raw = STR(VALUE);
                char *expanded = XSTR(VALUE);
                int CAT(PREFIX, 7) = 9;
                char *escaped = STR(a   +   \"b\\\\c\");
                """;

        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("operators.mc", source));
        String output = result.sourceFile().content();
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertTrue(output.contains("char *raw = \"VALUE\";"), output);
        assertTrue(output.contains("char *expanded = \"42\";"), output);
        assertTrue(output.contains("int item7 = 9;"), output);
        assertTrue(output.contains("\"a + \\\"b\\\\\\\\c\\\"\""), output);

        int generatedQuote = output.indexOf("\"VALUE\"");
        assertEquals(source.indexOf("STR(VALUE)"), result.sourceMap()[generatedQuote]);
    }

    @Test
    void expandsFileAndLineAtTheInvocationSite() {
        String source = """
                #define CURRENT_LINE() __LINE__
                #define FORWARD_LINE() CURRENT_LINE()
                int direct = __LINE__;
                int nested = FORWARD_LINE();
                char *file = __FILE__;
                """;

        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("dir/demo.mc", source));
        String output = result.sourceFile().content();
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertTrue(output.contains("int direct = 3;"), output);
        assertTrue(output.contains("int nested = 4;"), output);
        assertTrue(output.contains("char *file = \"dir/demo.mc\";"), output);

        int nestedValue = output.indexOf("4;", output.indexOf("int nested"));
        assertEquals(source.lastIndexOf("FORWARD_LINE()"), result.sourceMap()[nestedValue]);
    }

    @Test
    void diagnosesArityUnterminatedInvocationAndInvalidOperators() {
        String source = """
                #define PAIR(a, b) ((a) + (b))
                #define BAD_STRING(x) # nope
                #define BAD_LEFT(x) ## x
                #define PASTE(a, b) a ## b
                #define NEED(a, b, ...) a
                #define __LINE__ 8
                #undef __FILE__
                int first = PAIR(1);
                int second = PAIR(1, 2;
                int third = PASTE(+, *);
                int fourth = NEED(1);
                """;

        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("macro-errors.mc", source));
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("参数数量")),
                () -> result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("缺少 ')'")),
                () -> result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("# 后必须是宏参数")),
                () -> result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("## 不能位于替换列表边界")),
                () -> result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("拼接结果不是单个预处理记号")),
                () -> result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("不能重新定义预定义宏 __LINE__")),
                () -> result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("不能取消预定义宏 __FILE__")),
                () -> result.diagnostics().toString());
    }
}
