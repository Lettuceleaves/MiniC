package minic.cpp;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppDifferentialHarness.Backend;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** F02c: namespace binding must survive semantic analysis, IR, native code and debug history. */
@Tag("cpp-differential")
@Timeout(60)
final class CppNamespaceEndToEndTest {
    @TempDir Path temporary;

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("qualified-reopened", "13 9 9 6\n"),
                Arguments.of("global-local-shadow", "9 11 5 23\n"),
                Arguments.of("function-address", "2 11 101 2\n"),
                Arguments.of("using-same-entity-cycles", "15 30\n"),
                Arguments.of("declaration-order-recursion", "3 8 120 1 0\n"),
                Arguments.of("using-order-reopen", "10 20 3\n"),
                Arguments.of("nearest-common-ancestor", "9 100 7 40\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPrograms")
    void namespaceProgramsAgreeWithCpp17WithoutSourceRewriting(String name, String expected) throws Exception {
        var harness = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(),
                LanguageMode.CPP17_ALGORITHM);
        var report = harness.run(name, resource(name), "");
        // Check the primary reference first, so a mistaken fixture is not blamed on MiniC.
        assertEquals(CppDifferentialHarness.Status.OK, report.outcomes().get(Backend.GXX).status(), report::describe);
        assertEquals(expected, normalize(report.outcomes().get(Backend.GXX).stdout()), report::describe);
        assertTrue(report.passed(), report::describe);
        for (var outcome : report.outcomes().values()) assertEquals(expected, normalize(outcome.stdout()), report::describe);
    }

    static Stream<Arguments> invalidPrograms() {
        return Stream.of(
                Arguments.of("missing-qualified-member", "namespace A { int value = 1; }\nint main() { return A::missing; }", "missing"),
                Arguments.of("missing-namespace", "int main() { return Unknown::value; }", "Unknown"),
                Arguments.of("missing-using-target", "namespace A { int value = 1; }\nusing A::missing;\nint main() { return 0; }", "missing"),
                Arguments.of("global-and-directive-ambiguity", "int value = 1;\nnamespace A { int value = 2; }\nusing namespace A;\nint main() { return value; }", "value"),
                Arguments.of("two-directives-ambiguity", "namespace A { int value = 1; }\nnamespace B { int value = 2; }\nusing namespace A; using namespace B;\nint main() { return value; }", "value"),
                Arguments.of("function-not-yet-declared", "namespace A {\nint call() { return later(); }\nint later() { return 1; }\n}\nint main() { return A::call(); }", "later"),
                Arguments.of("qualified-member-not-yet-declared", "namespace A {\nint before() { return A::value; }\nint value = 7;\n}\nint main() { return A::before(); }", "value"),
                Arguments.of("block-using-does-not-leak", "namespace A { int value = 1; }\nint main() { { using A::value; value = 2; } return value; }", "value"),
                Arguments.of("namespace-not-yet-declared", "using namespace Future;\nnamespace Future { int value = 1; }\nint main() { return 0; }", "Future"),
                Arguments.of("using-conflicts-with-own-declaration", "namespace A { int value = 1; }\nnamespace B { using A::value; int value = 2; }\nint main() { return 0; }", "value"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPrograms")
    void invalidNamespaceLookupIsRejectedByBothSemanticAnalysisAndCpp17(String name, String content,
                                                                       String offendingName) throws Exception {
        Path referenceSource = temporary.resolve(name + ".cpp");
        Files.writeString(referenceSource, content);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-fsyntax-only", referenceSource.toString()), temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        assertNotEquals(0, reference.exitCode(), "The reference must reject this semantic error");
        assertFalse(reference.stderr().isBlank(), "Expected a reference compiler diagnostic");

        var source = new SourceFile(name + ".cpp", content);
        var api = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM);
        var parser = stage(api, Parser.class);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> "This fixture requires a semantic rejection: " + parser.errors());
        assertFalse(semantic.succeeded(), name);
        assertFalse(semantic.errors().isEmpty(), name);
        assertTrue(semantic.errors().stream().noneMatch(error -> error.code().equals("CPP002")),
                () -> "A blanket unimplemented-namespace rejection is not a lookup diagnostic: " + semantic.errors());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.range() != null
                        && source.text(error.range()).contains(offendingName)),
                () -> "The diagnostic must retain the offending source name: " + semantic.errors());
    }

    @Test
    void namespacedDebugExecutionPreservesOriginalLocationsAndEveryHistoryContext() throws Exception {
        var source = new SourceFile("namespace-debug.cpp", resource("debug-history"));
        var debug = new DebugApi(source, "", LanguageMode.CPP17_ALGORITHM);
        var history = new ArrayList<Debugger.Context>();
        history.add(debug.current());
        for (int steps = 0; debug.canNext() && steps < 200; steps++) history.add(debug.next());
        assertFalse(debug.canNext(), "The bounded fixture must finish");
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(8, debug.current().runtime().termination().status());
        assertTrue(history.stream().anyMatch(context -> new SourceRange(10, 17, 10, 32).equals(context.stop().range())
                && context.stop().function().equals("main")
                && source.text(context.stop().range()).equals("Counter::add(3)")), "Qualified call source location was lost");
        // The LINE trap inherits the first load's operand range, while the write retains
        // the whole expression. Both ranges must still refer to the untouched source.
        var paused = history.stream().filter(context -> new SourceRange(4, 8, 4, 13).equals(context.stop().range())
                && context.stop().function().equals("Counter::add"))
                .findFirst().orElseThrow(() -> new AssertionError("Namespace function source location was lost"));
        assertEquals("value", source.text(paused.stop().range()));
        assertTrue(paused.program().line(4).stream().anyMatch(location -> location.function().equals("Counter::add")
                && location.instruction() instanceof IrStorePointerInstruction
                && location.instruction().range().equals(new SourceRange(4, 8, 4, 23))
                && source.text(location.instruction().range()).equals("value += amount")));
        var frame = paused.runtime().stack().stream().filter(f -> f.function().equals("Counter::add")).findFirst().orElseThrow();
        assertEquals(3, frame.parameters().get("amount").integer());
        assertTrue(paused.runtime().globalMemory().stream().anyMatch(block -> block.label().equals("Counter::value")));
        for (int index = history.size() - 2; index >= 0; index--) assertSame(history.get(index), debug.previous());
        assertFalse(debug.canPrevious());
        for (int index = 1; index < history.size(); index++) assertSame(history.get(index), debug.next());
        assertEquals(8, debug.current().runtime().termination().status());
    }

    private static String resource(String name) throws IOException {
        try (var stream = CppNamespaceEndToEndTest.class.getResourceAsStream("/cpp/namespaces/" + name + ".cpp")) {
            if (stream == null) throw new IOException("Missing namespace fixture: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String output) { return output.replace("\r\n", "\n"); }

    private static <T> T stage(CompilerApi api, Class<T> type) {
        return api.stages().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
}
