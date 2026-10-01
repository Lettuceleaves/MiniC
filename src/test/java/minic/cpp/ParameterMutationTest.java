package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential")
@Timeout(60)
final class ParameterMutationTest {
    @TempDir Path temporary;

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("programs")
    void mutableParametersUseCalleeStorage(String name, LanguageMode mode, String content) throws Exception {
        var harness = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(), mode);
        var report = harness.run(name, content, "");
        assertTrue(report.passed(), report::describe);
    }

    static Stream<Arguments> programs() {
        String shadow = """
                int value = 90;
                int change(int value) { int before = value; int *p = &value; *p += 2; return before + value; }
                int main() { int value = 3; return change(value) == 8 && value == 3 && ::value == 90 ? 0 : 1; }
                """;
        String cursor = """
                int sum(int *p, int count) {
                    int result = 0;
                    while (count) { result += *p; p = p + 1; --count; }
                    return result;
                }
                int main() { int items[3] = {2, 4, 8}; int *p = items; return sum(p, 3) == 14 && p == items ? 0 : 1; }
                """;
        return Stream.of(
                Arguments.of("scalar-value-copy", LanguageMode.C, """
                        int change(int x) { x = x + 3; return x; }
                        int main() { int x = 4; int result = change(x); return result == 7 && x == 4 ? 0 : 1; }
                        """),
                Arguments.of("pointer-cursor", LanguageMode.C, cursor),
                Arguments.of("pointer-cursor", LanguageMode.CPP17_ALGORITHM, cursor),
                Arguments.of("address-and-direct-access", LanguageMode.C, """
                        int change(int n) { int before = n; int *p = &n; *p = 12; n += 3; return before + *p; }
                        int main() { int n = 4; return change(n) == 19 && n == 4 ? 0 : 1; }
                        """),
                Arguments.of("pointer-parameter-address", LanguageMode.C, """
                        int read(int *p) { int **q = &p; *q = p + 1; return *p; }
                        int main() { int items[2] = {2, 9}; int *p = items; return read(p) == 9 && p == items ? 0 : 1; }
                        """),
                Arguments.of("stack-parameters-prefix-compound", LanguageMode.C, """
                        int change(int a, int b, int c, int d, int e) {
                            ++a; --e; b += a; c *= b; int *fifth = &e; *fifth += 2; d -= e;
                            return a + b + c + d + e;
                        }
                        int main() { return change(1, 2, 3, 4, 5) == 22 ? 0 : 1; }
                        """),
                Arguments.of("floating-parameter", LanguageMode.C, """
                        double change(double x) { double *p = &x; *p += 0.5; x *= 2.0; return x; }
                        int main() { return change(1.5) == 4.0 ? 0 : 1; }
                        """),
                Arguments.of("const-pointee-mutable-pointer", LanguageMode.C, """
                        int read(const int *p) { ++p; return *p; }
                        int main() { int items[2] = {2, 9}; return read(items) == 9 ? 0 : 1; }
                        """),
                Arguments.of("recursive-address-isolation", LanguageMode.C, """
                        int sum(int n) {
                            if (n == 0) return 0;
                            int *address = &n; int child = sum(n - 1); ++n;
                            return *address + child;
                        }
                        int main() { return sum(3) == 9 ? 0 : 1; }
                        """),
                Arguments.of("parameter-hides-global", LanguageMode.C,
                        shadow.replace(" && ::value == 90", "")),
                Arguments.of("parameter-hides-global", LanguageMode.CPP17_ALGORITHM, shadow));
    }

    @ParameterizedTest
    @MethodSource("constantParameters")
    void constantParametersRemainRejected(String content) throws Exception {
        Path source = temporary.resolve("constant-parameter.cpp");
        Files.writeString(source, content);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-fsyntax-only", source.toString()), temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut());
        assertNotEquals(0, reference.exitCode());
        for (var mode : List.of(LanguageMode.C, LanguageMode.CPP17_ALGORITHM)) {
            var compiler = new CompilerApi(new SourceFile(source.toString(), content), mode);
            var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                    .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
            compiler.runThrough(semantic);
            assertFalse(semantic.succeeded(), "const parameter mutation must remain a semantic error in " + mode);
        }
    }

    static Stream<String> constantParameters() {
        return Stream.of(
                "int change(const int x) { x = 2; return x; } int main() { return 0; }",
                "int change(int * const p) { ++p; return *p; } int main() { return 0; }");
    }

    @Test
    void parameterMutationDoesNotCreateDuplicateSourceLocals() {
        var ir = new CompilerApi(new SourceFile("parameter-storage.mc",
                "int change(int x) { x = 8; return x; } int main() { return change(1); }")).runToIr();
        assertTrue(ir.findFunction("change").orElseThrow().blocks().stream()
                .flatMap(block -> block.instructions().stream()).noneMatch(IrDeclareLocalInstruction.class::isInstance));
    }

    @Test
    void debuggerSnapshotsObserveParameterWritesAndRetainHistoryAfterFrameReturn() {
        var debug = new DebugApi(new SourceFile("parameter-history.mc", """
                int change(int x) {
                    int *pointer = &x;
                    *pointer = 9;
                    return x;
                }
                int main() { return change(3) == 9 ? 0 : 1; }
                """));
        var history = new ArrayList<Debugger.Context>();
        history.add(debug.current());
        for (int steps = 0; debug.canNext() && steps < 200; steps++) history.add(debug.next());
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(0, debug.current().runtime().termination().status());
        assertTrue(debug.current().runtime().stackMemory().isEmpty(), "Returned parameter slots must be released");
        var observed = history.stream().flatMap(context -> context.runtime().stack().stream())
                .filter(frame -> frame.function().equals("change")).map(frame -> frame.parameters().get("x").integer()).toList();
        assertTrue(observed.contains(3L));
        assertTrue(observed.contains(9L));
        for (int index = history.size() - 2; index >= 0; index--) assertSame(history.get(index), debug.previous());
        for (int index = 1; index < history.size(); index++) assertSame(history.get(index), debug.next());
        assertEquals(0, debug.current().runtime().termination().status());
    }
}
