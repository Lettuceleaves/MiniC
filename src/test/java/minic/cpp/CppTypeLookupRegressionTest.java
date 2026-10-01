package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
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

/** Integration checks for parser classification and semantic canonical type identities. */
@Tag("cpp-differential")
@Timeout(60)
final class CppTypeLookupRegressionTest {
    @TempDir Path temporary;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPrograms")
    void invalidTypeShadowingCannotReachLowering(String name, String content) throws Exception {
        Path file = temporary.resolve(name + ".cpp");
        Files.writeString(file, content);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-fsyntax-only", file.toString()), temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        assertNotEquals(0, reference.exitCode(), "The reference must reject this invalid type lookup");
        assertFalse(reference.stderr().isBlank());

        var compiler = new CompilerApi(new SourceFile(file.toString(), content), LanguageMode.CPP17_ALGORITHM);
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        var errors = compiler.stages().stream().flatMap(stage -> stage.errors().stream()).toList();
        assertFalse(errors.isEmpty(), "Invalid type lookup must not be silently accepted");
        assertTrue(errors.stream().anyMatch(error -> error.range() != null), errors::toString);
    }

    static Stream<Arguments> invalidPrograms() {
        return Stream.of(
                Arguments.of("anonymous-union-member-hides-outer-typedef", """
                        typedef int Type;
                        struct Record { union { int Type; }; Type item; };
                        int main() { return 0; }
                        """),
                Arguments.of("nested-anonymous-union-member-hides-outer-typedef", """
                        typedef int Type;
                        struct Record { union { union { int Type; }; }; Type item; };
                        int main() { return 0; }
                        """),
                Arguments.of("inline-anonymous-union-member-hides-outer-typedef", """
                        typedef int Type;
                        struct { union { int Type; }; Type item; } value;
                        int main() { return 0; }
                        """),
                Arguments.of("field-hides-outer-typedef", """
                        typedef int Type;
                        struct Record { int Type; Type item; };
                        int main() { Record value = {1, 2}; return value.item == 2 ? 0 : 1; }
                        """),
                Arguments.of("field-hides-injected-class-name", """
                        struct Node { int Node; Node *next; };
                        int main() { return sizeof(Node) > 0 ? 0 : 1; }
                        """),
                Arguments.of("inline-field-hides-outer-typedef", """
                        typedef int Type;
                        struct { int Type; Type item; } value = {1, 2};
                        int main() { return value.item == 2 ? 0 : 1; }
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPrograms")
    void validTypeIdentitiesAgreeAcrossAllBackends(String name, String content) throws Exception {
        var harness = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(),
                LanguageMode.CPP17_ALGORITHM);
        var report = harness.run(name, content, "");
        assertTrue(report.passed(), report::describe);
    }

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("anonymous-union-keeps-promoted-value-and-qualified-type", """
                        typedef int Type;
                        struct Record { union { int Type; }; ::Type item; };
                        int main() {
                            Record value = {{4}, 3};
                            value.Type += value.item;
                            Type outside = value.Type;
                            return outside == 7 ? 0 : 1;
                        }
                        """),
                Arguments.of("nested-anonymous-union-keeps-promoted-value-and-qualified-type", """
                        typedef int Type;
                        struct Record { union { union { int Type; }; }; ::Type item; };
                        int main() {
                            Record value = {{{4}}, 3};
                            value.Type += value.item;
                            Type outside = value.Type;
                            return outside == 7 ? 0 : 1;
                        }
                        """),
                Arguments.of("inline-anonymous-union-keeps-promoted-value-and-qualified-type", """
                        typedef int Type;
                        struct { union { int Type; }; ::Type item; } value = {{4}, 3};
                        int main() {
                            value.Type += value.item;
                            Type outside = value.Type;
                            return outside == 7 ? 0 : 1;
                        }
                        """),
                Arguments.of("local-typedef-uses-outer-class-in-own-declarator", """
                        struct Packet { int value; };
                        int main() {
                            typedef Packet *Packet;
                            struct ::Packet object = {9};
                            Packet pointer = &object;
                            return pointer->value == 9 ? 0 : 1;
                        }
                        """),
                Arguments.of("using-completes-original-forward-type", """
                        namespace A { struct Packet; }
                        using A::Packet;
                        struct Packet { int value; };
                        int main() {
                            A::Packet object = {9}; Packet *pointer = &object;
                            return pointer->value == 9 && sizeof(A::Packet) == sizeof(Packet) ? 0 : 1;
                        }
                        """),
                Arguments.of("equivalent-alias-paths-do-not-create-distinct-types", """
                        namespace A { typedef int Number; struct Packet { int value; }; }
                        namespace B { typedef int Number; using A::Packet; }
                        using namespace A; using namespace B;
                        int main() { Number n = 4; Packet object = {n}; return object.value == 4 ? 0 : 1; }
                        """),
                Arguments.of("local-value-does-not-hide-namespace-type-qualifier", """
                        namespace A { typedef int Type; }
                        int main() { int A = 3; A::Type value = 7; return A + value == 10 ? 0 : 1; }
                        """),
                Arguments.of("scalar-alias-does-not-hide-namespace-type-qualifier", """
                        namespace A { typedef int Type; }
                        int main() { typedef double A; A::Type value = 7; return value == 7 && sizeof(A) == 8 ? 0 : 1; }
                        """),
                Arguments.of("scalar-alias-does-not-hide-namespace-value-qualifier", """
                        namespace A { int value = 7; }
                        int main() { typedef double A; return A::value == 7 && sizeof(A) == 8 ? 0 : 1; }
                        """),
                Arguments.of("field-type-before-later-member-name", """
                        typedef int Type;
                        struct Record { Type item; int Type; };
                        int main() { Record value = {1, 2}; return value.item == 1 ? 0 : 1; }
                        """),
                Arguments.of("injected-class-name-hides-outer-value", """
                        int Node = 3;
                        struct Node { Node *next; int value; };
                        int main() {
                            struct Node object; object.next = &object; object.value = 9;
                            return object.next->value == 9 && Node == 3 ? 0 : 1;
                        }
                        """));
    }
}
