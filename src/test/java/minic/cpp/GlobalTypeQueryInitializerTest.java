package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class GlobalTypeQueryInitializerTest {
    @TempDir Path temporary;

    private static final String COMMON = """
            struct Pair { char small; long long large; };
            int word = sizeof(word);
            int array[3];
            struct Pair pairs[2];
            int arraySize = sizeof(array);
            int aggregateArraySize = sizeof(pairs);
            int aggregateAlignment = alignof(struct Pair);
            int expressionAlignment = alignof(pairs);
            int counter = 0;
            int helper();
            int helperSize = sizeof(helper());
            int helperAlignment = alignof(helper());
            int helper() { counter += 1; return 7; }
            long long wide() { counter += 10; return 9; }
            int wideSize = sizeof(wide());
            long long declaredOnly(int value);
            int declarationSize = sizeof(declaredOnly(1));
            int declarationAlignment = alignof(declaredOnly(1));
            int declarationAddressSize = sizeof(&declaredOnly);
            int groupedDeclarationSize = sizeof((declaredOnly)(1));
            int assignmentSize = sizeof(counter = 99);
            int assignmentAlignment = alignof(counter = 42);
            int divisionSize = sizeof(1 / 0);
            int nestedSize = sizeof(sizeof(counter = 8));
            int combined = sizeof(array) + alignof(struct Pair) * 2;
            int table[2] = {sizeof(array), alignof(struct Pair)};
            """;

    @ParameterizedTest
    @EnumSource(LanguageMode.class)
    void globalQueriesUseSemanticTypesAndLayoutsInBothLanguageModes(LanguageMode mode) {
        var ir = new CompilerApi(new SourceFile("global-queries.cpp", COMMON + "int main(){return counter;}"), mode).runToIr();
        assertEquals(4, integer(ir, "word"));
        assertEquals(12, integer(ir, "arraySize"));
        assertEquals(32, integer(ir, "aggregateArraySize"));
        assertEquals(8, integer(ir, "aggregateAlignment"));
        assertEquals(8, integer(ir, "expressionAlignment"));
        assertEquals(4, integer(ir, "assignmentSize"));
        assertEquals(4, integer(ir, "assignmentAlignment"));
        assertEquals(4, integer(ir, "divisionSize"));
        assertEquals(8, integer(ir, "nestedSize"));
        assertEquals(28, integer(ir, "combined"));
        assertEquals(0, integer(ir, "counter"));
        assertEquals(4, integer(ir, "helperSize"));
        assertEquals(4, integer(ir, "helperAlignment"));
        assertEquals(8, integer(ir, "wideSize"));
        assertEquals(8, integer(ir, "declarationSize"));
        assertEquals(8, integer(ir, "declarationAlignment"));
        assertEquals(8, integer(ir, "declarationAddressSize"));
        assertEquals(8, integer(ir, "groupedDeclarationSize"));
        assertFalse(ir.externalFunctionNames().contains("declaredOnly"));
        var table = bytes(ir, "table");
        assertEquals(12, table.getInt());
        assertEquals(8, table.getInt());
    }

    @Test void qualifiedTypesAndNamespacedOwnGlobalQueryUseNormalizedNodeIdentities() {
        String source = """
                namespace A {
                    struct Item { int values[3]; };
                    int own = sizeof(own);
                    int size = sizeof(struct Item);
                    int alignment = alignof(struct Item);
                    int arraySize = sizeof(struct Item[2]);
                }
                int qualifiedValueSize = sizeof(A::own);
                int main(){return 0;}
                """;
        var ir = new CompilerApi(new SourceFile("qualified-query.cpp", source), LanguageMode.CPP17_ALGORITHM).runToIr();
        assertEquals(4, integer(ir, "A::own"));
        assertEquals(12, integer(ir, "A::size"));
        assertEquals(4, integer(ir, "A::alignment"));
        assertEquals(24, integer(ir, "A::arraySize"));
        assertEquals(4, integer(ir, "qualifiedValueSize"));
    }

    @Test void globalQueryOperandsAreNeverExecutedInNativeOrDebugRuntime() throws Exception {
        String source = "#include <stdio.h>\n" + COMMON + """
                int main(){
                    printf("%d %d %d %d %d %d %d %d %d %d %d %d %d %d %d\\n", counter, word, arraySize, aggregateArraySize,
                           aggregateAlignment, assignmentSize, divisionSize, combined, helperSize, helperAlignment, wideSize,
                           declarationSize, declarationAlignment, declarationAddressSize, groupedDeclarationSize);
                    return 0;
                }
                """;
        var harness = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(),
                LanguageMode.CPP17_ALGORITHM);
        var report = harness.run("global-type-queries", source, "");
        assertTrue(report.passed(), report::describe);
        report.outcomes().values().forEach(outcome -> assertEquals("0 4 12 32 8 4 4 28 4 4 8 8 8 8 8\n",
                outcome.stdout().replace("\r\n", "\n"), report::describe));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "int helper(int value); int result = sizeof(helper()); int main(){return 0;}",
            "int helper(int *value); int result = alignof(helper(1)); int main(){return 0;}",
            "int helper(int value); int result = sizeof(sizeof(helper(1))); int main(){return helper(1);}",
            "int helper(int value); int result = alignof(sizeof(helper(1))); int main(){int (*p)(int)=helper; return 0;}"
    })
    void unevaluatedContextStillChecksSignatureAndDoesNotEscapeTheQuery(String source) {
        var compiler = new CompilerApi(new SourceFile("query-errors.cpp", source), LanguageMode.CPP17_ALGORITHM);
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("SEM001")
                        && (d.message().contains("实参数量") || d.message().contains("实参类型") || d.message().contains("未定义函数"))
                        || d.code().equals("CPP004") && d.message().contains("实参不能按 C++ 标准转换")),
                () -> semantic.errors().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sizeof(helper)", "sizeof((helper))", "alignof(helper)", "sizeof(*pointer)"})
    void functionItselfHasNoObjectLayout(String query) {
        var compiler = new CompilerApi(new SourceFile("function-layout.cpp", """
                int helper(int value) { return value; }
                int (*pointer)(int);
                int result = QUERY;
                int main() { return 0; }
                """.replace("QUERY", query)), LanguageMode.CPP17_ALGORITHM);
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        assertFalse(semantic.succeeded(), "A function is not an object with pointer-sized layout");
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("SEM001") && d.message().contains("布局")),
                () -> semantic.errors().toString());
    }

    @Test void cppGlobalQueryCannotSeeFunctionDeclaredAfterTheQuery() {
        var compiler = new CompilerApi(new SourceFile("later-query.cpp", """
                int value = sizeof(later());
                int later() { return 7; }
                int main() { return value; }
                """), LanguageMode.CPP17_ALGORITHM);
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP003") && d.range().startLine() == 1),
                () -> semantic.errors().toString());
    }

    private static int integer(IrResult ir, String name) { return bytes(ir, name).getInt(); }
    private static ByteBuffer bytes(IrResult ir, String name) {
        var global = ir.globalData().stream().filter(g -> ir.displayName(g.label()).equals(name)).findFirst().orElseThrow();
        return ByteBuffer.wrap(global.bytes()).order(ByteOrder.LITTLE_ENDIAN);
    }
}
