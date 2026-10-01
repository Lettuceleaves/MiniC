package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrIndirectCallInstruction;
import minic.compiler.ir.model.IrType;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.*;
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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** F03b2 acceptance: ordinary member methods normalize to the existing core call ABI. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppMemberMethodTest {
    @TempDir Path temporary;

    private static final String DEBUG_SOURCE = """
            #include <stdio.h>
            namespace Counter {
                struct Value {
                    void set(int value) { this->value = value; }
                    int add(int amount) {
                        int before = value;
                        this->value += amount;
                        return value;
                    }
                    int twice(int amount) {
                        add(amount);
                        return (this)->add(amount);
                    }
                    int value;
                };
            }
            int main() {
                Counter::Value counter = {1};
                counter.set(1);
                int total = counter.twice(3);
                printf("%d\\n", total);
                return 0;
            }
            """;

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("implicit-explicit-this-and-later-field", DEBUG_SOURCE, "7\n"),
                Arguments.of("mutual-and-self-recursion", """
                        #include <stdio.h>
                        struct Recursion {
                            int odd(int n) { if (n == 0) return 0; return even(n - 1); }
                            int even(int n) { if (n == 0) return 1; return this->odd(n - 1); }
                            int factorial(int n) { if (n <= 1) return 1; return n * factorial(n - 1); }
                        };
                        int main() {
                            Recursion value = {};
                            printf("%d %d %d\\n", value.odd(7), (&value)->even(8), value.factorial(5));
                            return 0;
                        }
                        """, "1 1 120\n"),
                Arguments.of("local-parameter-member-namespace-shadowing", """
                        #include <stdio.h>
                        int value = 11;
                        namespace N {
                            int outside() { return 2; }
                            struct Box {
                                int value;
                                int pick(int value) {
                                    int result = value + this->value + ::value;
                                    { int value = 30; result += value; }
                                    return result + outside();
                                }
                            };
                        }
                        int main() { N::Box box = {5}; printf("%d\\n", box.pick(3)); return 0; }
                        """, "51\n"),
                Arguments.of("same-class-private-other-instance", """
                        #include <stdio.h>
                        class Vault {
                            int value;
                            int bump(int amount) { value += amount; return value; }
                        public:
                            void set(int amount) { value = amount; }
                            int read() { return value; }
                            int merge(Vault *other) { other->value += value; return other->bump(0); }
                        };
                        int main() {
                            Vault first = {};
                            Vault second = {};
                            first.set(3);
                            second.set(7);
                            int result = first.merge(&second);
                            printf("%d %d %d\\n", result, first.read(), second.read());
                            return 0;
                        }
                        """, "10 3 10\n"),
                Arguments.of("namespace-type-identities", """
                        #include <stdio.h>
                        namespace A { struct Box { int value; int update(int n) { value += n; return value; } }; }
                        namespace B { struct Box { long long value; int update(int n) { value += n; return (int)value; } }; }
                        using A::Box;
                        int main() {
                            Box a = {4};
                            B::Box b = {10};
                            int first = a.update(2);
                            int second = b.update(3);
                            printf("%d %d %d %d\\n", first, second, (int)sizeof(a), (int)sizeof(b));
                            return 0;
                        }
                        """, "6 13 4 8\n"),
                Arguments.of("class-pointer-parameters-and-return-chains", """
                        #include <stdio.h>
                        struct Link {
                            int value;
                            Link *choose(Link *other) { return other; }
                            Link *self() { return this; }
                            int read() { return value; }
                            void copy(Link *other) { value = other->value; }
                        };
                        int main() {
                            Link first = {1};
                            Link second = {9};
                            int read = first.choose(&second)->self()->read();
                            first.copy(second.self());
                            printf("%d %d\\n", read, (first.read)());
                            return 0;
                        }
                        """, "9 9\n"),
                Arguments.of("function-pointer-field-is-not-a-method", """
                        #include <stdio.h>
                        int doubled(int value) { return value * 2; }
                        struct Callback {
                            int (*run)(int);
                            int dispatch(int value) { return run(value) + (this->run)(value + 1); }
                        };
                        int main() {
                            Callback callback;
                            callback.run = doubled;
                            printf("%d %d\\n", (callback.run)(4), callback.dispatch(3));
                            return 0;
                        }
                        """, "8 14\n"),
                Arguments.of("method-hides-early-enum-substitution", """
                        #include <stdio.h>
                        enum E { run = 1 };
                        struct Box {
                            int run() { return 7; }
                            int use() { return run(); }
                        };
                        int main() { Box value = {}; printf("%d\\n", value.use()); return 0; }
                        """, "7\n"),
                Arguments.of("prototype-method-in-unevaluated-query", """
                        #include <stdio.h>
                        struct Box {
                            int value;
                            int missing(int);
                            int read() { return value; }
                        };
                        int main() {
                            Box value = {2};
                            printf("%d %d\\n", (int)sizeof(value.missing(3)), value.read());
                            return 0;
                        }
                        """, "4 2\n"),
                Arguments.of("receiver-once-before-arguments-and-parameter-snapshot", """
                        #include <stdio.h>
                        int trace = 0;
                        int calls = 0;
                        int index = 0;
                        struct Meter {
                            int value;
                            int add(int amount) { trace = trace * 10 + 3; value += amount; return value; }
                        };
                        Meter objects[2] = {{1}, {10}};
                        Meter *receiver() { calls++; trace = trace * 10 + 1; return &objects[0]; }
                        int argument() { trace = trace * 10 + 2; return index + 4; }
                        int change(Meter **slot, Meter *other) { *slot = other; return 5; }
                        int invoke(Meter *pointer, Meter *other) { return pointer->add(change(&pointer, other)); }
                        int main() {
                            int first = receiver()->add(argument());
                            printf("%d %d %d %d\\n", first, trace, calls, index);
                            trace = 0;
                            int second = objects[index++].add(argument());
                            printf("%d %d %d %d\\n", second, trace, calls, index);
                            trace = 0;
                            Meter *pointer = &objects[1];
                            int third = (pointer++)->add(argument());
                            printf("%d %d %d %d\\n", third, trace, calls, (int)(pointer - objects));
                            Meter left = {2};
                            Meter right = {20};
                            int fourth = invoke(&left, &right);
                            printf("%d %d %d\\n", fourth, left.value, right.value);
                            return 0;
                        }
                        """, "5 123 1 0\n10 23 1 1\n15 23 1 2\n7 7 20\n")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPrograms")
    void memberProgramsAgreeAcrossNativeDebugAndCpp17(String name, String source, String expected) throws Exception {
        // Harness backend calls are deliberately sequential, as are these JUnit invocations.
        var limits = new CppDifferentialHarness.Limits(Duration.ofSeconds(30), Duration.ofSeconds(10), 50_000, 1_048_576);
        var report = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), limits, LanguageMode.CPP17_ALGORITHM)
                .run(name, source, "");
        var reference = report.outcomes().get(Backend.GXX);
        assertEquals(CppDifferentialHarness.Status.OK, reference.status(), report::describe);
        assertEquals(expected, normalize(reference.stdout()), report::describe);
        assertTrue(report.passed(), report::describe);
        for (var outcome : report.outcomes().values()) assertEquals(expected, normalize(outcome.stdout()), report::describe);
    }

    static Stream<Arguments> invalidAccessAndLookup() {
        return Stream.of(
                Arguments.of("other-class-private-field", """
                        class Vault { int value; };
                        struct Intruder {
                            int inspect(Vault *other) {
                                return other->value; // bad
                            }
                        };
                        int main() { return 0; }
                        """),
                Arguments.of("private-method-from-outside", """
                        class Vault {
                            int hidden() { return 1; }
                        public:
                            int open() { return hidden(); }
                        };
                        int main() {
                            Vault value = {};
                            return value.hidden(); // bad
                        }
                        """),
                Arguments.of("method-name-does-not-leak", """
                        struct Box { int run() { return 1; } };
                        int main() {
                            return run(); // bad
                        }
                        """),
                Arguments.of("later-namespace-value-is-not-a-later-member", """
                        struct Box {
                            int read() {
                                return later; // bad
                            }
                        };
                        int later = 3;
                        int main() { return 0; }
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAccessAndLookup")
    void accessAndLookupFailuresLocateTheActualUse(String name, String source) throws Exception {
        assertRejected(name, source, false);
    }

    static Stream<Arguments> qualifiedReceivers() {
        return Stream.of(
                Arguments.of("const-pointer", "Box object = {1}; const Box *pointer = &object;", "pointer->touch()"),
                Arguments.of("volatile-object", "volatile Box object = {1};", "object.touch()"),
                Arguments.of("const-nested-pointer-field", "Holder object = {{1}}; const Holder *pointer = &object;", "(pointer->inner).touch()"),
                Arguments.of("volatile-nested-field", "volatile Holder object = {{1}};", "object.inner.touch()"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("qualifiedReceivers")
    void nonConstNonVolatileMethodsRejectQualifiedReceivers(String name, String setup, String call) throws Exception {
        String source = "struct Box { int value; int touch() { return ++value; } };\n"
                + "struct Holder { Box inner; };\nint main() {\n    " + setup + "\n"
                + "    return " + call + "; // bad\n}\n";
        assertRejected(name, source, false);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"this = (Box *)0", "++(this)", "&(this)", "&this"})
    void thisRemainsAnUnassignableNonAddressablePointerValueEvenInUnusedMethods(String expression) throws Exception {
        String source = "struct Box {\n    int bad() {\n        " + expression + "; // bad\n"
                + "        return 0;\n    }\n};\nint main() { return 0; }\n";
        // The existing parser already diagnoses assignment to the dedicated ThisExpr.
        // Either frontend stage may reject it, provided it locates the invalid use.
        assertRejected("this-value", source, false, true);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"value.read;", "int (*function)() = value.read;"})
    void aBoundMethodCannotEscapeWithoutBeingCalled(String expression) throws Exception {
        String source = "struct Box { int value; int read() { return value; } };\n"
                + "int main() {\n    Box value = {1};\n    " + expression + " // bad\n    return 0;\n}\n";
        assertRejected("bound-method-value", source, false);
    }

    @Test void temporaryObjectReceiverIsMaterializedOnceForTheCall() throws Exception {
        String source = """
                #include <stdio.h>
                int count=0;
                struct Box { int value; int read() { return ++value; } };
                Box make() { ++count; Box value = {7}; return value; }
                int main() {
                    int result=make().read();
                    printf("%d %d\\n",result,count);
                    return 0;
                }
                """;
        CppReferenceTest.agree(temporary, "temporary-object-receiver", source, "8 1\n");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conflictingMembers")
    void conflictingMethodsCannotOverwriteFieldsOrEachOther(String members, boolean validReference) throws Exception {
        assertRejected("method-conflict", "struct Box {\n" + members + "\n};\nint main(){return 0;}\n", validReference);
    }

    private static Stream<Arguments> conflictingMembers() {
        return Stream.of(
                Arguments.of("int conflict;\nint conflict() { return 0; } // bad", false),
                Arguments.of("union { int conflict; };\nint conflict() { return 0; } // bad", false),
                Arguments.of("int conflict() { return 0; }\nint conflict() { return 1; } // bad", false));
    }

    @Test void distinctMemberParameterListsSelectTheirOwnDefinitions() throws Exception {
        CppReferenceTest.agree(temporary, "member-parameter-overloads", """
                #include <stdio.h>
                struct Box {int conflict(){return 0;}int conflict(int n){return n;}};
                int main(){Box value={};printf("%d %d\\n",value.conflict(),value.conflict(7));return 0;}
                """, "0 7\n");
    }

    @Test void evaluatedPrototypeMethodNeedsADefinitionAtTheCallSite() throws Exception {
        String content = """
                struct Box { int missing(int); };
                int main() {
                    Box value = {};
                    return value.missing(3); // bad
                }
                """;
        Path referenceSource = temporary.resolve("missing-method.cpp");
        Files.writeString(referenceSource, content);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", referenceSource.toString(), "-o", temporary.resolve("missing-method.exe").toString()),
                temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        assertNotEquals(0, reference.exitCode(), "The reference must fail to link an evaluated undefined member");
        var api = compiler(content);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(stage(api, Parser.class).succeeded());
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.range().startLine() == lineOf(content, "// bad")
                        && error.message().contains("missing")), () -> semantic.errors().toString());
    }

    @Test void normalizedMethodsRetainSourceIdentityTypesAndDisplayNames() {
        var api = compiler(DEBUG_SOURCE);
        var parser = stage(api, Parser.class);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var result = semantic.semanticResult();
        var source = parser.result().program();
        assertSame(source, result.sourceProgram());
        assertEquals(LanguageMode.C, result.program().languageMode());
        assertNull(AstChildren.firstCppSyntax(result.program()), "Every executable C++ node must be normalized");
        var methods = nodes(source).stream().filter(MethodMember.class::isInstance).map(MethodMember.class::cast).toList();
        assertEquals(3, methods.size());
        for (MethodMember member : methods) {
            FunctionDecl original = member.method();
            FunctionDecl core = assertInstanceOf(FunctionDecl.class, result.sourceToCore().get(original));
            assertEquals(original.range(), core.range());
            assertEquals(original.parameters().size() + 1, core.parameters().size());
            assertTrue(core.parameters().getFirst().type().isPointer());
            assertTrue(core.parameters().getFirst().type().pointee().isStruct());
            assertEquals("Counter::Value::" + original.name(), result.displayNames().get(core.name()));
            for (int index = 0; index < original.parameters().size(); index++) {
                assertSame(core.parameters().get(index + 1), result.sourceToCore().get(original.parameters().get(index)));
            }
            assertNotSame(core, result.sourceToCore().get(member),
                    "Mapping the wrapper to the same core function would overwrite the original in coreToSource");
            assertSame(core.body(), result.sourceToCore().get(original.body()));
        }
        var selfExpressions = nodes(source).stream().filter(ThisExpr.class::isInstance).map(ThisExpr.class::cast).toList();
        assertEquals(3, selfExpressions.size());
        for (ThisExpr original : selfExpressions) {
            Expression core = assertInstanceOf(Expression.class, result.sourceToCore().get(original));
            assertEquals(original.range(), core.range());
            assertTrue(result.typeOf(original).orElseThrow().isPointer());
        }
        for (var original : nodes(source).stream().filter(CallExpr.class::isInstance).map(CallExpr.class::cast).toList()) {
            assertInstanceOf(CallExpr.class, result.sourceToCore().get(original));
            assertEquals(original.range(), result.sourceToCore().get(original).range());
            assertTrue(result.typeOf(original).isPresent(), "Type query lost for source method call " + original.range());
        }
        var lowerer = new IrLowerer(source, result);
        Set<AstNode> observed = Collections.newSetFromMap(new IdentityHashMap<>());
        while (lowerer.canNext()) {
            lowerer.step();
            if (lowerer.currentResult().currentAstNode() instanceof AstNode node) observed.add(node);
        }
        assertTrue(methods.stream().anyMatch(member -> observed.contains(member.method())),
                "IR stage context must expose original method declarations");
    }

    @Test void methodsUseDirectCallsAndAnObjectPointerWithoutHeapAllocationOrDispatchTables() {
        var api = compiler(DEBUG_SOURCE);
        var ir = api.runToIr();
        var methods = ir.functions().stream().filter(function -> ir.displayName(function.name()).startsWith("Counter::Value::")).toList();
        assertEquals(3, methods.size());
        for (var method : methods) {
            assertEquals(IrType.POINTER, method.parameters().getFirst().type());
            assertEquals("this", ir.displayName(method.parameters().getFirst().name()));
        }
        var instructions = ir.functions().stream().flatMap(function -> function.blocks().stream())
                .flatMap(block -> block.instructions().stream()).toList();
        assertTrue(instructions.stream().noneMatch(IrIndirectCallInstruction.class::isInstance));
        var methodNames = methods.stream().map(method -> method.name()).collect(Collectors.toSet());
        var methodCalls = instructions.stream().filter(IrCallInstruction.class::isInstance).map(IrCallInstruction.class::cast)
                .filter(call -> methodNames.contains(call.calleeName())).toList();
        assertEquals(4, methodCalls.size(), "set/twice in main, add/this->add in twice");
        for (var call : methodCalls) {
            assertEquals(2, call.arguments().size());
            assertEquals(IrType.POINTER, call.arguments().getFirst().type());
        }
        assertEquals(Set.of("printf"), ir.externalFunctionNames(), "No allocator or runtime member lookup may be introduced");
        assertTrue(ir.globalData().isEmpty(), "These non-virtual methods require no dispatch table or hidden global object");
        assertEquals(4, ir.structLayouts().values().stream().filter(layout -> ir.displayName(layout.name()).equals("Counter::Value"))
                .findFirst().orElseThrow().size(), "Methods do not add per-object storage");
    }

    @Test void debugFramesLocationsAndHistoryRetainOriginalMemberNames() {
        SourceFile source = new SourceFile("member-methods.cpp", DEBUG_SOURCE);
        var debug = new DebugApi(source, "", LanguageMode.CPP17_ALGORITHM);
        List<Debugger.Context> history = new ArrayList<>();
        history.add(debug.current());
        for (int steps = 0; debug.canNext() && steps < 1000; steps++) history.add(debug.next());
        assertFalse(debug.canNext(), "The fixture exceeded its bounded step budget");
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(0, debug.current().runtime().termination().status());
        assertEquals("7\n", normalize(debug.current().runtime().stdout()));
        assertTrue(history.stream().allMatch(context -> context.runtime().heap().isEmpty()), "A member call must not allocate an object");
        var paused = history.stream().filter(context -> context.runtime().stack().stream()
                        .anyMatch(frame -> frame.function().equals("Counter::Value::add"))
                        && context.runtime().stack().stream().anyMatch(frame -> frame.function().equals("Counter::Value::twice")))
                .findFirst().orElseThrow(() -> new AssertionError("Nested method frames lost their original names"));
        var frame = paused.runtime().stack().stream().filter(item -> item.function().equals("Counter::Value::add"))
                .findFirst().orElseThrow();
        assertEquals(3, frame.parameters().get("amount").integer());
        assertNotNull(frame.parameters().get("this"), "The object pointer needs a source-facing name");
        assertTrue(frame.parameters().get("this").integer() != 0);
        assertTrue(history.stream().anyMatch(context -> context.stop().function().equals("Counter::Value::add")
                && context.stop().range() != null && source.text(context.stop().range()).contains("this")),
                "Method stops must retain the source this expression range");
        int assignmentLine = lineOf(DEBUG_SOURCE, "this->value += amount;");
        assertTrue(paused.program().line(assignmentLine).stream()
                .anyMatch(location -> location.function().equals("Counter::Value::add")
                        && source.text(location.instruction().range()).equals("this->value += amount")),
                "Member write instruction location must retain the original expression");
        for (int index = history.size() - 2; index >= 0; index--) assertSame(history.get(index), debug.previous());
        assertFalse(debug.canPrevious());
        for (int index = 1; index < history.size(); index++) assertSame(history.get(index), debug.next());
        assertEquals("7\n", normalize(debug.current().runtime().stdout()), "History replay must not execute methods or output twice");
    }

    private void assertRejected(String name, String content, boolean validReference) throws Exception {
        assertRejected(name, content, validReference, false);
    }

    private void assertRejected(String name, String content, boolean validReference, boolean allowParserDiagnostic) throws Exception {
        Path referenceSource = temporary.resolve(name + ".cpp");
        Files.writeString(referenceSource, content);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-pedantic-errors", "-fsyntax-only", referenceSource.toString()),
                temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        if (validReference) assertEquals(0, reference.exitCode(), reference::stderr);
        else assertNotEquals(0, reference.exitCode(), "G++17 must reject this invalid member use");
        var api = compiler(content);
        var parser = stage(api, Parser.class);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        if (!allowParserDiagnostic) {
            assertTrue(parser.succeeded(), () -> "The fixture requires a semantic diagnostic: " + parser.errors());
        }
        assertFalse(semantic.succeeded(), "The invalid or unsupported member operation must be diagnosed");
        var errors = api.stages().stream().flatMap(stage -> stage.errors().stream()).toList();
        int badLine = lineOf(content, "// bad");
        assertTrue(errors.stream().anyMatch(error -> error.range() != null
                        && error.range().startLine() == badLine
                        && (!validReference || error.code().equals("CPP005"))),
                () -> "The diagnostic must locate the operation on line " + badLine
                        + ", rather than blanket-reject the class or method declaration: " + errors);
    }

    private static CompilerApi compiler(String text) {
        return new CompilerApi(new SourceFile("member-methods.cpp", text), LanguageMode.CPP17_ALGORITHM);
    }

    private static <T> T stage(CompilerApi api, Class<T> type) {
        return api.stages().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static List<AstNode> nodes(AstNode root) {
        List<AstNode> result = new ArrayList<>();
        collect(root, Collections.newSetFromMap(new IdentityHashMap<>()), result);
        return result;
    }

    private static void collect(AstNode node, Set<AstNode> visited, List<AstNode> result) {
        if (!visited.add(node)) return;
        result.add(node);
        AstChildren.of(node).forEach(child -> collect(child, visited, result));
    }

    private static int lineOf(String text, String marker) {
        int offset = text.indexOf(marker);
        if (offset < 0) throw new IllegalArgumentException("Missing source marker: " + marker);
        return (int) text.substring(0, offset).chars().filter(value -> value == '\n').count() + 1;
    }

    private static String normalize(String text) { return text.replace("\r\n", "\n"); }
}
