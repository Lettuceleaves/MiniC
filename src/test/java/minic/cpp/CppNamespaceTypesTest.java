package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppDifferentialHarness.Backend;
import org.junit.jupiter.api.Tag;
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
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** F02d: namespace type names reuse existing aggregate layout, calls, and value semantics. */
@Tag("cpp-differential")
@Timeout(60)
final class CppNamespaceTypesTest {
    @TempDir Path temporary;

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("qualified-slots", "11 5 8 4\n"),
                Arguments.of("global-type-queries", "12 4 24\n"),
                Arguments.of("using-reopen", "5 13 8\n"),
                Arguments.of("isolated-layout", "6 9 4 8\n"),
                Arguments.of("nested-qualified", "13 8\n"),
                Arguments.of("local-value-shadow", "15 4 8 1 4\n"),
                Arguments.of("declaration-point-scopes", "4 4 40\n"),
                Arguments.of("forward-self-pointer", "3 16\n"),
                Arguments.of("same-type-paths", "6 8\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPrograms")
    void namespacedTypesAgreeAcrossNativeDebugAndCpp17(String name, String expected) throws Exception {
        var harness = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(),
                LanguageMode.CPP17_ALGORITHM);
        var report = harness.run(name, resource(name), "");
        assertEquals(CppDifferentialHarness.Status.OK, report.outcomes().get(Backend.GXX).status(), report::describe);
        assertEquals(expected, normalize(report.outcomes().get(Backend.GXX).stdout()), report::describe);
        assertTrue(report.passed(), report::describe);
        for (var outcome : report.outcomes().values()) assertEquals(expected, normalize(outcome.stdout()), report::describe);
    }

    static Stream<Arguments> invalidPrograms() {
        return Stream.of(
                Arguments.of("missing-qualified-type", "namespace A { struct Known { int value; }; }\nA::Missing item;\nint main() { return 0; }", 2),
                Arguments.of("qualified-value-is-not-type", "namespace A { int Count = 1; }\nA::Count item;\nint main() { return 0; }", 2),
                Arguments.of("missing-using-type", "namespace A { typedef int Known; }\nusing A::Missing;\nint main() { return 0; }", 2),
                Arguments.of("using-namespace-is-not-type", "namespace A { namespace Inner {} }\nusing A::Inner;\nint main() { return 0; }", 2),
                Arguments.of("ambiguous-struct-types", "namespace A { struct Item { int value; }; }\nnamespace B { struct Item { int value; }; }\nusing namespace A; using namespace B;\nItem item = {1};\nint main() { return 0; }", 4),
                Arguments.of("ambiguous-alias-types", "namespace A { typedef int Count; }\nnamespace B { typedef long long Count; }\nusing namespace A; using namespace B;\nCount item = 1;\nint main() { return 0; }", 4),
                Arguments.of("local-value-hides-type", "namespace A { typedef int Item; }\nusing A::Item;\nint main() {\nint Item = 3;\nItem value = 4;\nreturn value;\n}", 5),
                Arguments.of("block-type-using-does-not-leak", "namespace A { typedef int Number; }\nint main() {\n{ using A::Number; Number local = 1; }\nNumber outside = 2;\nreturn outside;\n}", 4),
                Arguments.of("parameter-value-hides-following-type", "namespace A { typedef int Number; }\nusing A::Number;\nint f(int Number, Number other) { return other; }\nint main() { return 0; }", 3));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPrograms")
    void invalidTypeLookupHasSourceDiagnosticAndIsRejectedByCpp17(String name, String content,
                                                                 int offendingLine) throws Exception {
        Path referenceSource = temporary.resolve(name + ".cpp");
        Files.writeString(referenceSource, content);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-fsyntax-only", referenceSource.toString()), temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        assertNotEquals(0, reference.exitCode(), "The reference must reject this invalid type lookup");
        assertFalse(reference.stderr().isBlank());

        var source = new SourceFile(name + ".cpp", content);
        var api = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM);
        var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        var errors = api.stages().stream().flatMap(stage -> stage.errors().stream()).toList();
        assertFalse(errors.isEmpty(), "Invalid type lookup must be rejected before lowering");
        assertTrue(errors.stream().noneMatch(error -> error.code().equals("CPP001") || error.code().equals("CPP002")),
                () -> "A blanket unsupported-namespace/type rejection is not a lookup diagnostic: " + errors);
        // A hidden typedef can make a declaration syntactically invalid. Either parser or
        // semantic rejection is valid, but it must point to the actual invalid use.
        assertTrue(errors.stream().anyMatch(error -> error.range() != null
                        && error.range().startLine() == offendingLine
                        && !source.text(error.range()).isBlank()),
                () -> "The diagnostic must locate the invalid type use on line " + offendingLine + ": " + errors);
    }

    private static String resource(String name) throws IOException {
        try (var stream = CppNamespaceTypesTest.class.getResourceAsStream("/cpp/namespace-types/" + name + ".cpp")) {
            if (stream == null) throw new IOException("Missing namespace type fixture: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String output) { return output.replace("\r\n", "\n"); }
}
