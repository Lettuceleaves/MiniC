package minic.compiler.preprocess;

import minic.compiler.Diagnostic;
import minic.compiler.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PreprocessorTest {
    @Test
    void expandsObjectFunctionAndVariadicMacrosWithStringifyAndPaste() {
        String source = """
                #define BASE 3
                #define ADD(a, b) ((a) + (b))
                #define APPLY(fn, ...) fn(__VA_ARGS__)
                #define WRAP(...) APPLY(ADD, __VA_ARGS__)
                #define STR(x) #x
                #define XSTR(x) STR(x)
                #define CAT(a, b) a ## b
                int value = WRAP(BASE, 4);
                char *raw = STR(BASE);
                char *expanded = XSTR(BASE);
                int CAT(item, 7) = 9;
                """;

        Output result = preprocess("macros.mc", source);
        assertTrue(result.errors().isEmpty(), result.errors()::toString);
        assertTrue(result.text().contains("int value = ((3) + (4));"), result.text());
        assertTrue(result.text().contains("char *raw = \"BASE\";"), result.text());
        assertTrue(result.text().contains("char *expanded = \"3\";"), result.text());
        assertTrue(result.text().contains("int item7 = 9;"), result.text());
        assertEquals(source.indexOf("STR(BASE)"), result.sourceMap()[result.text().indexOf("\"BASE\"")]);
    }

    @Test
    void evaluatesConditionalsAndSplicesContinuedLines() {
        String source = """
                #define VERSION 3
                #define FEATURE 1
                #if defined(FEATURE) && VERSION >= 4
                int selected = 1;
                #elif defined FEATURE && ((VERSION << 1) == 6)
                int selected = 2;
                #else
                int selected = 3;
                #endif
                #if 0 && (1 / 0)
                int unreachable = 1;
                #endif
                int continued = 3 + \\
                4;
                char *line = __FILE__;
                """;

        Output result = preprocess("dir/conditional.mc", source);
        assertTrue(result.errors().isEmpty(), result.errors()::toString);
        assertTrue(result.text().contains("int selected = 2;"), result.text());
        assertFalse(result.text().contains("int selected = 1;"), result.text());
        assertFalse(result.text().contains("unreachable"), result.text());
        assertTrue(result.text().contains("int continued = 3 + 4;"), result.text());
        assertTrue(result.text().contains("char *line = \"dir/conditional.mc\";"), result.text());
        assertEquals(source.indexOf("4;"), result.sourceMap()[result.text().indexOf("4;")]);
    }

    @Test
    void reportsMalformedMacrosAndConditions() {
        Output result = preprocess("errors.mc", """
                #define PAIR(a, b) ((a) + (b))
                #define BAD_LEFT(x) ## x
                #define __LINE__ 8
                int first = PAIR(1);
                #if (1 && )
                #endif
                #if 1
                #else
                #elif 1
                #endif
                """);

        String messages = result.errors().toString();
        for (String expected : List.of("参数数量", "## 不能位于替换列表边界", "不能重新定义预定义宏 __LINE__",
                "条件编译表达式非法", "#else 后不能出现 #elif")) {
            assertTrue(messages.contains(expected), messages);
        }
    }

    @Test
    void mapsAngleBracketHeadersToTheBundledLibrary() {
        Output result = preprocess("header.mc", "#include <stdio.h>\nint main() { return printf(\"ok\"); }\n");
        assertTrue(result.errors().isEmpty(), result.errors()::toString);
        assertTrue(result.text().contains("extern int printf(const char *format, ...);"), result.text());
    }

    private static Output preprocess(String name, String source) {
        Preprocessor preprocessor = new Preprocessor();
        PreprocessResult result = preprocessor.preprocess(new SourceFile(name, source));
        return new Output(result.sourceFile().content(), result.sourceMap(), preprocessor.errors());
    }

    private record Output(String text, int[] sourceMap, List<Diagnostic> errors) {
    }
}
