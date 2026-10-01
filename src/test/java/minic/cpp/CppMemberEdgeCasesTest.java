package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppDifferentialHarness.Backend;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Member lookup, receiver qualification, type identity and hidden-parameter ABI regressions. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppMemberEdgeCasesTest {
    @TempDir Path temporary;

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("function-pointer-shadowing-and-member-priority", """
                        #include <stdio.h>
                        int choose(int value) { return 1000 + value; }
                        int callback(int value) { return 200 + value; }
                        struct Dispatch {
                            int choose(int value) { return 10 + value; }
                            int member_first() { return choose(1); }
                            int local_callback() { int (*choose)(int) = callback; return choose(2); }
                            int parameter_callback(int (*choose)(int)) { return choose(3); }
                        };
                        int main() {
                            Dispatch value = {};
                            int member = value.member_first();
                            int local = value.local_callback();
                            int parameter = value.parameter_callback(callback);
                            printf("%d %d %d\\n", member, local, parameter);
                            return 0;
                        }
                        """, "11 202 203\n"),
                Arguments.of("const-pointer-member-keeps-mutable-pointee", """
                        #include <stdio.h>
                        class Inner {
                        public:
                            int value;
                            int bump() { return ++value; }
                        };
                        struct Outer { Inner nested[2]; Inner *pointer; };
                        int main() {
                            Inner object = {4};
                            Outer outer = {{{1}, {2}}, &object};
                            const Outer *readonly = &outer;
                            int result = readonly->pointer->bump();
                            printf("%d %d\\n", result, object.value);
                            return 0;
                        }
                        """, "5 5\n"),
                Arguments.of("aggregate-result-this-and-five-explicit-arguments", """
                        #include <stdio.h>
                        struct Result { int sum; int tag; int before; };
                        struct Accumulator {
                            int seed;
                            Result gather(int a, int b, int c, int d, int e) {
                                Result result = {seed + a + b + c + d + e,
                                                 a * 10000 + b * 1000 + c * 100 + d * 10 + e, seed};
                                seed += 1;
                                return result;
                            }
                        };
                        int main() {
                            Accumulator state = {10};
                            Result result = state.gather(1, 2, 3, 4, 5);
                            printf("%d %d %d %d\\n", result.sum, result.tag, result.before, state.seed);
                            return 0;
                        }
                        """, "25 12345 10 11\n"),
                Arguments.of("local-typedef-does-not-change-receiver-class-identity", """
                        #include <stdio.h>
                        namespace A {
                            class Node {
                            public:
                                int value;
                                int get() {
                                    Node *before = this;
                                    typedef int Node;
                                    Node delta = 3;
                                    A::Node *after = this;
                                    return before->value + delta + (int)sizeof(*after);
                                }
                            };
                        }
                        namespace B {
                            struct Node {
                                long long value;
                                int get() {
                                    typedef A::Node Node;
                                    Node other = {2};
                                    B::Node *self = this;
                                    return (int)self->value + other.value + (int)sizeof(*this);
                                }
                            };
                        }
                        int main() {
                            A::Node a = {5};
                            B::Node b = {7};
                            int first = a.get();
                            int second = b.get();
                            printf("%d %d %d %d\\n", first, second, (int)sizeof(A::Node), (int)sizeof(B::Node));
                            return 0;
                        }
                        """, "12 17 4 8\n")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPrograms")
    void memberEdgeCasesAgreeAcrossNativeDebugAndCpp17(String name, String source, String expected) throws Exception {
        var limits = new CppDifferentialHarness.Limits(Duration.ofSeconds(30), Duration.ofSeconds(10), 50_000, 1_048_576);
        var report = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), limits, LanguageMode.CPP17_ALGORITHM)
                .run(name, source, "");
        assertEquals(CppDifferentialHarness.Status.OK, report.outcomes().get(Backend.GXX).status(), report::describe);
        assertTrue(report.passed(), report::describe);
        for (var outcome : report.outcomes().values()) {
            assertEquals(expected, outcome.stdout().replace("\r\n", "\n"), report::describe);
        }
    }

    @Test
    void constOuterArrayElementCannotCallNonConstMethod() throws Exception {
        String content = """
                class Inner {
                public:
                    int value;
                    int bump() { return ++value; }
                };
                struct Outer { Inner nested[2]; Inner *pointer; };
                int read(const Outer *outer) {
                    return outer->nested[0].bump();
                }
                int main() { return 0; }
                """;
        Path referenceSource = temporary.resolve("const-array-receiver.cpp");
        Files.writeString(referenceSource, content);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-pedantic-errors", "-fsyntax-only", referenceSource.toString()),
                temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        assertNotEquals(0, reference.exitCode(), "G++17 must reject the const array-element receiver");

        var source = new SourceFile("const-array-receiver.cpp", content);
        var compiler = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM);
        var parser = compiler.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> "The fixture needs a semantic rejection: " + parser.errors());
        assertFalse(semantic.succeeded(), "Calling the non-const method must be rejected");
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP004")
                        && error.range() != null && error.range().startLine() == 8
                        && source.text(error.range()).contains("outer->nested[0].bump")),
                () -> "The diagnostic must locate the qualified receiver call: " + semantic.errors());
    }
}
