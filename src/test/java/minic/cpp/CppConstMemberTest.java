package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.CallInstruction.IrIndirectCallInstruction;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.MethodMember;
import minic.compiler.parser.node.Expression.ThisExpr;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** F03c1: const methods qualify the object, not the pointees of its pointer fields. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppConstMemberTest {
    @TempDir Path temporary;

    private static final String SOURCE = """
            #include <stdio.h>
            namespace N {
                struct Box {
                    int value;
                    int read() const { return (this)->value; }
                    const Box *self() const { return this; }
                    void set(int value) { this->value = value; }
                };
            }
            int main() {
                N::Box value = {1};
                value.set(5);
                const N::Box *pointer = value.self();
                printf("%d %d %d\\n", value.read(), pointer->read(), (int)sizeof(*pointer));
                return 0;
            }
            """;

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("mutable-and-const-receivers-and-this-return", SOURCE, "5 5 4\n"),
                Arguments.of("nested-values-arrays-and-mutable-pointees", """
                        #include <stdio.h>
                        struct Leaf {
                            int value;
                            int read() const { return value; }
                            int bump() { return ++value; }
                        };
                        struct Outer {
                            Leaf inner;
                            Leaf values[2];
                            Leaf *pointer;
                            int nested() const { return inner.read() + this->values[1].read(); }
                            int mutatePointee() const { return pointer->bump(); }
                            Leaf *mutablePointer() const { return pointer; }
                            const Leaf *nestedPointer() const { return &inner; }
                        };
                        int main() {
                            Leaf leaf = {5};
                            Outer value = {{1}, {{2}, {3}}, &leaf};
                            const Outer *pointer = &value;
                            int sum = pointer->nested();
                            int bump = pointer->mutatePointee();
                            int inner = pointer->nestedPointer()->read();
                            int again = pointer->mutablePointer()->bump();
                            printf("%d %d %d %d %d\\n", sum, bump, inner, again, leaf.value);
                            return 0;
                        }
                        """, "4 6 1 7 7\n"),
                Arguments.of("private-const-methods-and-another-instance", """
                        #include <stdio.h>
                        class Vault {
                            int value;
                            int read() const { return value; }
                        public:
                            void set(int amount) { value = amount; }
                            int sum(const Vault *other) const { return read() + other->read() + this->value; }
                        };
                        int main() {
                            Vault first = {};
                            Vault second = {};
                            first.set(2);
                            second.set(3);
                            const Vault *pointer = &first;
                            printf("%d\\n", pointer->sum(&second));
                            return 0;
                        }
                        """, "7\n"),
                Arguments.of("const-prototype-query-and-aggregate-return-abi", """
                        #include <stdio.h>
                        struct Result { long long total; int tag; };
                        struct Source {
                            int seed;
                            int prototype(int) const;
                            Result collect(int a, int b, int c, int d, int e) const {
                                Result result = {seed + a + b + c + d + e, seed};
                                return result;
                            }
                        };
                        int main() {
                            const Source source = {10};
                            Result result = source.collect(1, 2, 3, 4, 5);
                            printf("%lld %d %d\\n", result.total, result.tag, (int)sizeof(source.prototype(3)));
                            return 0;
                        }
                        """, "25 10 4\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPrograms")
    void constMemberProgramsAgreeAcrossAllBackends(String name, String source, String expected) throws Exception {
        var report = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()),
                new CppDifferentialHarness.Limits(Duration.ofSeconds(30), Duration.ofSeconds(10), 50_000, 1_048_576),
                LanguageMode.CPP17_ALGORITHM).run(name, source, "");
        assertEquals(CppDifferentialHarness.Status.OK, report.outcomes().get(Backend.GXX).status(), report::describe);
        assertEquals(expected, normalize(report.outcomes().get(Backend.GXX).stdout()), report::describe);
        assertTrue(report.passed(), report::describe);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"value = 9;", "this->value++;", "values[0] = 9;", "++this->values[1];",
            "inner.value = 9;", "inner.bump();", "this->items[0].bump();", "bump();", "this->bump();",
            "pointer = (Leaf *)0;", "&(this);", "++(this);"})
    void constBodiesCannotModifySubobjectsOrCallMutableMethods(String operation) throws Exception {
        String source = """
                struct Leaf { int value; int bump() { return ++value; } int read() const { return value; } };
                struct Box {
                    int value;
                    int values[2];
                    Leaf inner;
                    Leaf items[2];
                    Leaf *pointer;
                    int bump() { return ++value; }
                    int bad() const {
                        %s // bad
                        return 0;
                    }
                };
                int main() { return 0; }
                """.formatted(operation);
        assertRejected("const-body", source, false);
    }

    @Test void constThisCannotBeReturnedAsAMutableClassPointer() throws Exception {
        assertRejected("mutable-this-return", """
                struct Box {
                    int value;
                    Box *bad() const {
                        return this; // bad
                    }
                };
                int main() { return 0; }
                """, false);
    }

    @ParameterizedTest @ValueSource(strings = {"volatile", "const volatile"})
    void constMethodsStillRejectVolatileReceivers(String qualifier) throws Exception {
        String source = "struct Box { int value; int read() const { return value; } };\n"
                + "int main() {\n    " + qualifier + " Box value = {3};\n"
                + "    return value.read(); // bad\n}\n";
        assertRejected("volatile-receiver", source, false);
    }

    @Test void constAndNonConstOverloadsSelectFromTheReceiverType() throws Exception {
        CppReferenceTest.agree(temporary, "const-overload", """
                #include <stdio.h>
                struct Box {
                    int read() { return 1; }
                    int read() const { return 2; }
                };
                int main() { Box first={};const Box second={};printf("%d %d\\n",first.read(),second.read());return 0; }
                """, "1 2\n");
    }

    @Test void sourceThisTypesAndCoreHiddenParametersRetainConstWithoutRuntimeDispatch() {
        var api = compiler(SOURCE);
        var parser = stage(api, Parser.class);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var result = semantic.semanticResult();
        var methods = nodes(parser.result().program()).stream().filter(MethodMember.class::isInstance)
                .map(MethodMember.class::cast).toList();
        assertEquals(3, methods.size());
        for (MethodMember member : methods) {
            boolean expectedConst = !member.method().name().equals("set");
            assertEquals(expectedConst, member.constQualified());
            FunctionDecl core = assertInstanceOf(FunctionDecl.class, result.sourceToCore().get(member.method()));
            assertEquals(member.range(), core.range());
            assertTrue(core.parameters().getFirst().type().isPointer());
            assertEquals(expectedConst, core.parameters().getFirst().type().pointee().isConstQualified());
            for (AstNode node : nodes(member.method())) if (node instanceof ThisExpr self) {
                var type = result.typeOf(self).orElseThrow();
                assertTrue(type.isPointer());
                assertEquals(expectedConst, type.pointee().isConstQualified());
                assertEquals(self.range(), result.sourceToCore().get(self).range());
            }
        }
        var ir = api.runToIr();
        assertEquals(Set.of("printf"), ir.externalFunctionNames());
        assertTrue(ir.functions().stream().flatMap(function -> function.blocks().stream())
                .flatMap(block -> block.instructions().stream()).noneMatch(IrIndirectCallInstruction.class::isInstance));
        assertTrue(ir.globalData().isEmpty());
        assertTrue(ir.displayNames().containsValue("N::Box::read"));
    }

    @Test void constFlagsSurviveDeferredBodiesAndPrototypesWhileOldAstConstructorsDefaultToMutable() {
        var api = compiler("struct Box { int read() const { return 1; } int declared(int) const; int mutableRead(); }; int main(){return 0;}");
        var parser = stage(api, Parser.class);
        api.runThrough(parser);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        var members = parser.result().program().structs().getFirst().cppInfo().members().stream()
                .filter(MethodMember.class::isInstance).map(MethodMember.class::cast).toList();
        assertEquals(List.of(true, true, false), members.stream().map(MethodMember::constQualified).toList());
        assertNotNull(members.getFirst().method().body());
        assertNull(members.get(1).method().body());
        var original = members.getFirst();
        var legacy = new MethodMember(original.method(), original.nameRange());
        assertFalse(legacy.constQualified());
        assertSame(original.method(), legacy.method());
        assertSame(original.nameRange(), legacy.nameRange());
    }

    @Test void constCallsPreserveDebugNamesAndAllocateNoHeapObjects() {
        var debug = new DebugApi(new SourceFile("const-members.cpp", SOURCE), "", LanguageMode.CPP17_ALGORITHM);
        var history = new ArrayList<Debugger.Context>();
        history.add(debug.current());
        for (int count = 0; debug.canNext() && count < 1000; count++) history.add(debug.next());
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals("5 5 4\n", normalize(debug.current().runtime().stdout()));
        assertTrue(history.stream().allMatch(context -> context.runtime().heap().isEmpty()));
        assertTrue(history.stream().anyMatch(context -> context.runtime().stack().stream()
                .anyMatch(frame -> frame.function().equals("N::Box::read") && frame.parameters().containsKey("this"))));
        for (int index = history.size() - 2; index >= 0; index--) assertSame(history.get(index), debug.previous());
        for (int index = 1; index < history.size(); index++) assertSame(history.get(index), debug.next());
        assertEquals("5 5 4\n", normalize(debug.current().runtime().stdout()));
    }

    private void assertRejected(String name, String source, boolean validReference) throws Exception {
        Path file = temporary.resolve(name + ".cpp");
        Files.writeString(file, source);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-pedantic-errors", "-fsyntax-only", file.toString()),
                temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        if (validReference) assertEquals(0, reference.exitCode(), reference::stderr);
        else assertNotEquals(0, reference.exitCode(), "G++ must reject this invalid const operation");
        var api = compiler(source);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(stage(api, Parser.class).succeeded(), () -> stage(api, Parser.class).errors().toString());
        assertFalse(semantic.succeeded());
        int markedLine = (int) source.substring(0, source.indexOf("// bad")).chars().filter(c -> c == '\n').count() + 1;
        assertTrue(semantic.errors().stream().anyMatch(error -> error.range().startLine() == markedLine
                && (!validReference || error.code().equals("CPP005"))), () -> semantic.errors().toString());
    }

    private static CompilerApi compiler(String source) {
        return new CompilerApi(new SourceFile("const-members.cpp", source), LanguageMode.CPP17_ALGORITHM);
    }

    private static <T> T stage(CompilerApi api, Class<T> type) {
        return api.stages().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static List<AstNode> nodes(AstNode root) {
        var result = new ArrayList<AstNode>();
        collect(root, Collections.newSetFromMap(new IdentityHashMap<>()), result);
        return result;
    }

    private static void collect(AstNode node, Set<AstNode> visited, List<AstNode> result) {
        if (!visited.add(node)) return;
        result.add(node);
        AstChildren.of(node).forEach(child -> collect(child, visited, result));
    }

    private static String normalize(String output) { return output.replace("\r\n", "\n"); }
}
