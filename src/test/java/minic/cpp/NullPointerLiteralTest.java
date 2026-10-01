package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement.VarDeclStmt;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppDifferentialHarness.Backend;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential")
@Timeout(60)
final class NullPointerLiteralTest {
    @TempDir Path temporary;

    private static final String PROGRAM = """
            #include <stdio.h>
            int *global_zero = 0;
            int *global_long_zero = (0L);
            struct Holder { int *first; int *second; };
            struct Holder global_pair = {0, (0L)};
            int (*global_function)(int *) = 0;
            int *returned_zero() { return (0L); }
            int accepts(int *pointer) { return pointer == 0; }
            int main() {
                int value = 7;
                int *local = 0;
                int *other = 0L;
                int *hex = 0x0u;
                int *wide = 0ULL;
                struct Holder pair = {0, (0L)};
                int *array[3] = {0, 0L, (0)};
                local = &value;
                local = (0L);
                other = 1 ? &value : (0);
                int *chosen = 0 ? (0L) : &value;
                int *empty = 1 ? (0) : &value;
                int (*call)(int *) = accepts;
                int (*missing)(int *) = 0;
                missing = (0L);
                void *untyped = 0;
                const int *readonly = 0;
                int score = accepts(0) + accepts(0L) + call((0)) + accepts(returned_zero());
                int all_zero = global_zero == 0 && 0L == global_long_zero
                    && global_pair.first == 0 && global_pair.second == 0
                    && global_function == 0 && local == 0 && 0 == hex && wide == 0
                    && pair.first == 0 && pair.second == 0
                    && array[0] == 0 && array[1] == 0 && array[2] == 0
                    && empty == 0 && missing == 0 && untyped == 0 && readonly == 0
                    && 0L != other;
                printf("%d %d %d %d %d\\n", score, all_zero, *other + *chosen,
                       (int)(sizeof(0) + sizeof(0L)), (0 + 7) * 2);
                return 0;
            }
            """;

    @ParameterizedTest
    @EnumSource(LanguageMode.class)
    void literalZeroConvertsOnlyWhenPointerContextRequiresIt(LanguageMode mode) throws Exception {
        var harness = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(), mode);
        var report = harness.run("null-literal-" + mode, PROGRAM, "");
        assertEquals(CppDifferentialHarness.Status.OK, report.outcomes().get(Backend.GXX).status(), report::describe);
        assertEquals("4 1 14 8 14\n", report.outcomes().get(Backend.GXX).stdout().replace("\r\n", "\n"), report::describe);
        assertTrue(report.passed(), report::describe);
    }

    @ParameterizedTest
    @EnumSource(LanguageMode.class)
    void contextualConversionPreservesLiteralTypesAndProducesPointerConditionalType(LanguageMode mode) {
        var api = new CompilerApi(new SourceFile("null-types.cpp", """
                int main() {
                    int number = 0;
                    int *pointer = (0L);
                    int *choice = 1 ? pointer : (0);
                    int arithmetic = 0 + 1;
                    return arithmetic;
                }
                """), mode);
        var semantic = semantic(api);
        api.runThrough(semantic);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var parser = api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        var statements = parser.result().program().functions().getFirst().body().statements();
        assertEquals(MiniType.INT, semantic.semanticResult().typeOf(((VarDeclStmt) statements.get(0)).initializer()).orElseThrow());
        var groupedZero = ((VarDeclStmt) statements.get(1)).initializer();
        assertEquals(MiniType.LONG, semantic.semanticResult().typeOf(groupedZero).orElseThrow());
        assertEquals(MiniType.LONG, semantic.semanticResult().typeOf(((Expression.GroupingExpr) groupedZero).expression()).orElseThrow());
        assertEquals(MiniType.INT.pointerTo(), semantic.semanticResult().typeOf(((VarDeclStmt) statements.get(2)).initializer()).orElseThrow());
        assertEquals(MiniType.INT, semantic.semanticResult().typeOf(((VarDeclStmt) statements.get(3)).initializer()).orElseThrow());
    }

    static Stream<Arguments> invalidPrograms() {
        String prefix = "enum Zero { enum_zero = 0 }; int main() { int variable = 0; int *pointer = ";
        return Stream.concat(Stream.of("1", "variable", "enum_zero", "false", "'\\0'", "0 + 0", "-0", "0.0")
                        .map(expression -> Arguments.of("initializer-" + expression, prefix + expression + "; return 0; }")),
                Stream.of(
                        Arguments.of("comparison-variable", "int main(){int variable=0; int *pointer=0; return pointer==variable;}"),
                        Arguments.of("conditional-variable", "int main(){int variable=0; int *pointer=0; int *other=1?pointer:variable;return 0;}"),
                        Arguments.of("nonzero-argument", "int accepts(int *pointer){return 0;} int main(){return accepts(1);}"),
                        Arguments.of("variable-return", "int *bad(){int variable=0;return variable;} int main(){return 0;}")));
    }

    @Test
    void zeroCastIsNotAnIntegerLiteralNullPointerConstant() {
        // The installed GCC 8 accepts this old constant-expression rule even in C++17;
        // keep the specified literal-only boundary independent of that reference behavior.
        var api = new CompilerApi(new SourceFile("cast-zero.cpp", "int main(){int *pointer=(int)0;return 0;}"),
                LanguageMode.CPP17_ALGORITHM);
        var semantic = semantic(api);
        api.runThrough(semantic);
        assertFalse(semantic.errors().isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPrograms")
    void cppRejectsValuesThatAreNotNullPointerLiterals(String name, String content) throws Exception {
        var referenceSource = temporary.resolve("invalid-null.cpp");
        Files.writeString(referenceSource, content);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-fsyntax-only", referenceSource.toString()), temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut());
        assertFalse(reference.outputExceeded());
        assertNotEquals(0, reference.exitCode(), () -> name + ": " + reference.stderr());
        var api = new CompilerApi(new SourceFile("invalid-null.cpp", content), LanguageMode.CPP17_ALGORITHM);
        var semantic = semantic(api);
        api.runThrough(semantic);
        assertFalse(semantic.errors().isEmpty(), name);
    }

    private static SemanticAnalyzer semantic(CompilerApi api) {
        return api.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
    }
}
