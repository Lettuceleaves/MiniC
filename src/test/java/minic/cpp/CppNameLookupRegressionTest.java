package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Reference-verified lookup errors that must not silently bind an outer value. */
@Tag("cpp-frontend")
@Timeout(20)
final class CppNameLookupRegressionTest {
    @TempDir Path temporary;

    @ParameterizedTest(name = "{0}")
    @MethodSource("namespaceNameConflicts")
    void namespaceNamesParticipateInOrdinaryLookup(String name, String content) throws Exception {
        Path path = temporary.resolve(name + ".cpp");
        Files.writeString(path, content);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-fsyntax-only", path.toString()), temporary, "", Duration.ofSeconds(10), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertNotEquals(0, reference.exitCode(), "The reference must reject this program");
        assertFalse(reference.stderr().isBlank());

        var compiler = new CompilerApi(new SourceFile(path.toString(), content), LanguageMode.CPP17_ALGORITHM);
        var parser = compiler.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertFalse(semantic.succeeded(), "A namespace name must prevent silently selecting the competing value");
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP003")),
                () -> semantic.errors().toString());
    }

    static Stream<Arguments> namespaceNameConflicts() {
        return Stream.of(
                Arguments.of("namespace-hides-outer-value", """
                        int value = 7;
                        namespace Outer {
                            namespace value {}
                            int read() { return value; }
                        }
                        int main() { return Outer::read(); }
                        """),
                Arguments.of("directive-namespace-competes-with-value", """
                        namespace Source { namespace value {} }
                        using namespace Source;
                        int value = 7;
                        int main() { return value; }
                        """),
                Arguments.of("directive-value-competes-with-namespace", """
                        namespace Source { int value = 7; }
                        using namespace Source;
                        namespace value {}
                        int main() { return value; }
                        """),
                Arguments.of("qualified-namespace-hides-imported-value", """
                        namespace Source { int value = 7; }
                        namespace Outer { using namespace Source; namespace value {} }
                        int main() { return Outer::value; }
                        """)
        );
    }
}
