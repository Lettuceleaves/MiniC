package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Expression.NameExpr;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
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

/** F04a: lvalue reference binding; temporaries and additional value categories have a separate suite. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppReferenceTest {
    @TempDir Path temporary;

    static final String DEBUG_SOURCE = """
            #include <stdio.h>
            namespace N {
                int &bump(int &target) {
                    target += 3;
                    return target;
                }
            }
            int main() {
                int value = 4;
                int &alias = value;
                int &again = N::bump(alias);
                again += 2;
                printf("%d %d %d\\n", value, alias, (int)sizeof(alias));
                return 0;
            }
            """;

    static Stream<Arguments> lvaluePrograms() {
        return Stream.of(
                Arguments.of("local-alias-assignment-does-not-rebind", """
                        #include <stdio.h>
                        int main() {
                            int first = 2;
                            int second = 7;
                            int &alias = first;
                            const int &view = alias;
                            alias = second;
                            second = 9;
                            alias += 3;
                            printf("%d %d %d %d %d\\n", first, second, view, &alias == &first, &view == &first);
                            return 0;
                        }
                        """, "10 9 10 1 1\n"),
                Arguments.of("reference-parameters-and-return", DEBUG_SOURCE, "9 9 4\n"),
                Arguments.of("reference-call-initializes-an-unread-object", """
                        #include <stdio.h>
                        void initialize(int &target) { target = 4; }
                        int main() {
                            int value;
                            initialize(value);
                            printf("%d\\n", value);
                            return 0;
                        }
                        """, "4\n"),
                Arguments.of("function-pointer-retains-reference-parameter-and-result-abi", """
                        #include <stdio.h>
                        int &bump(int &target) { target += 3; return target; }
                        int main() {
                            int value = 2;
                            int &(*operation)(int &) = bump;
                            int &alias = operation(value);
                            alias += 4;
                            printf("%d %d\\n", value, &alias == &value);
                            return 0;
                        }
                        """, "9 1\n"),
                Arguments.of("class-reference-members-and-const-receiver", """
                        #include <stdio.h>
                        namespace Model {
                            struct Box {
                                int value;
                                int &edit() { return value; }
                                const int &read() const { return this->value; }
                            };
                            void copy(Box &target, const Box &source) { target.value = source.read(); }
                        }
                        int main() {
                            Model::Box first = {2};
                            const Model::Box second = {7};
                            Model::Box &alias = first;
                            Model::copy(alias, second);
                            int &member = alias.edit();
                            member += 3;
                            printf("%d %d %d\\n", first.value, second.read(), &member == &first.value);
                            return 0;
                        }
                        """, "10 7 1\n"),
                Arguments.of("pointer-reference-preserves-pointer-and-pointee-qualifiers", """
                        #include <stdio.h>
                        void redirect(int *&slot, int *replacement) { slot = replacement; }
                        int main() {
                            int first = 2;
                            int second = 5;
                            int *pointer = &first;
                            int *&alias = pointer;
                            int * const &view = pointer;
                            redirect(alias, &second);
                            *view = 8;
                            printf("%d %d %d\\n", first, second, pointer == &second);
                            return 0;
                        }
                        """, "2 8 1\n"),
                Arguments.of("volatile-and-const-volatile-aliases", """
                        #include <stdio.h>
                        int main() {
                            volatile int value = 2;
                            volatile int &alias = value;
                            const volatile int &view = alias;
                            alias += 5;
                            printf("%d %d\\n", view, &alias == &value);
                            return 0;
                        }
                        """, "7 1\n"),
                Arguments.of("typedef-reference-collapsing-and-top-level-cv", """
                        #include <stdio.h>
                        namespace Types { typedef int &Ref; }
                        int main() {
                            int value = 1;
                            Types::Ref alias = value;
                            Types::Ref &again = alias;
                            const Types::Ref cvAlias = again;
                            cvAlias = 6;
                            printf("%d %d %d\\n", value, &again == &value, (int)sizeof(Types::Ref));
                            return 0;
                        }
                        """, "6 1 4\n"),
                Arguments.of("binding-evaluates-address-once-without-reading-object", """
                        #include <stdio.h>
                        int main() {
                            int uninitialized;
                            int &writeOnly = uninitialized;
                            writeOnly = 4;
                            int values[2] = {3, 8};
                            int index = 0;
                            int &element = values[index++];
                            element += uninitialized;
                            printf("%d %d %d\\n", values[0], values[1], index);
                            return 0;
                        }
                        """, "7 8 1\n"),
                Arguments.of("reference-direct-and-list-initialization", """
                        #include <stdio.h>
                        int main() {
                            int value = 3;
                            int &first(value);
                            int &second{first};
                            const int &view = {second};
                            second = 8;
                            printf("%d %d %d\\n", value, view, &first == &second);
                            return 0;
                        }
                        """, "8 8 1\n"));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("lvaluePrograms")
    void basicReferencesAgreeAcrossAllBackends(String name, String source, String expected) throws Exception {
        agree(temporary, name, source, expected);
    }

    static Stream<Arguments> invalidBindings() {
        return Stream.of(
                Arguments.of("missing-initializer", "int main() { int &alias; // bad\n return 0; }"),
                Arguments.of("discard-const", "int main() { const int value=1; int &alias=value; // bad\n return 0; }"),
                Arguments.of("discard-volatile", "int main() { volatile int value=1; int &alias=value; // bad\n return 0; }"),
                Arguments.of("write-through-const", "int main() { int value=1; const int &alias=value;\n alias=3; // bad\n return 0; }"),
                Arguments.of("different-scalar-type", "int main() { short value=1; int &alias=value; // bad\n return 0; }"),
                Arguments.of("different-array-bound", "int main() { int values[2]={1,2}; int (&alias)[3]=values; // bad\n return 0; }"),
                Arguments.of("unsafe-pointer-reference-cv", "int main() { int value=1; int *pointer=&value; const int *&alias=pointer; // bad\n return 0; }"),
                Arguments.of("incompatible-function-reference", "int operation(int value){return value;}\n int main(){long long (&alias)(int)=operation; // bad\n return 0;}"),
                Arguments.of("const-return-cannot-escape-as-mutable", "int &bad(const int &value){return value; // bad\n } int main(){return 0;}"),
                Arguments.of("mutable-method-via-const-reference", "struct Box{int value; void set(){value=2;}};\n int main(){Box value={1}; const Box &alias=value; alias.set(); // bad\n return 0;}"));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("invalidBindings")
    void illFormedReferenceBindingsHaveSourceDiagnostics(String name, String source) throws Exception {
        reject(temporary, name, source);
    }

    @Test void referenceSourceTypesAndExpressionMappingsAreSeparateFromPointerAbi() {
        var api = compiler(DEBUG_SOURCE);
        var parser = stage(api, Parser.class);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var sourceFunction = nodes(parser.result().program()).stream().filter(FunctionDecl.class::isInstance)
                .map(FunctionDecl.class::cast).filter(function -> function.name().equals("bump")).findFirst().orElseThrow();
        assertTrue(sourceFunction.returnType().toString().contains("&"));
        assertTrue(sourceFunction.parameters().getFirst().type().toString().contains("&"));
        var result = semantic.semanticResult();
        var core = assertInstanceOf(FunctionDecl.class, result.sourceToCore().get(sourceFunction));
        assertTrue(core.returnType().isPointer());
        assertEquals(MiniType.INT, core.returnType().pointee());
        assertTrue(core.parameters().getFirst().type().isPointer());
        assertEquals(sourceFunction.range(), core.range());
        var names = nodes(sourceFunction).stream().filter(NameExpr.class::isInstance).map(NameExpr.class::cast).toList();
        assertFalse(names.isEmpty());
        for (var name : names) {
            assertEquals(MiniType.INT, result.typeOf(name).orElseThrow().unqualified());
            assertNotNull(result.sourceToCore().get(name));
            assertEquals(name.range(), result.sourceToCore().get(name).range());
        }
        var ir = api.runToIr();
        assertTrue(ir.displayNames().containsValue("N::bump"));
        assertEquals(Set.of("printf"), ir.externalFunctionNames());
    }

    @Test void referenceAliasesKeepDebugHistoryAndUseNoHeap() {
        assertDebugHistory(DEBUG_SOURCE, "9 9 4\n", "N::bump", "target");
    }

    @Test void functionPointerFieldsKeepTheirSourceReferenceSignature() throws Exception {
        agree(temporary, "reference-callback-field", """
                #include <stdio.h>
                int &bump(int &target) { target += 2; return target; }
                struct Callback { int &(*operation)(int &); };
                int main() {
                    int value = 3;
                    Callback callback = {bump};
                    int &alias = callback.operation(value);
                    alias += 4;
                    printf("%d %d\\n", value, &alias == &value);
                    return 0;
                }
                """, "9 1\n");
    }

    static Stream<Arguments> callbackReferencePrograms() {
        return Stream.of(
                Arguments.of("callback-alias", "Callback &alias = callback;"),
                Arguments.of("callback-reference-return", "Callback &alias = choose(callback);"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("callbackReferencePrograms")
    void referencesToCallbacksRetainNestedReferenceSignatures(String name, String declaration) throws Exception {
        agree(temporary, name, """
                #include <stdio.h>
                typedef int (*Callback)(int &);
                int bump(int &target) { return ++target; }
                Callback &choose(Callback &callback) { return callback; }
                int main() {
                    int value = 1;
                    Callback callback = bump;
                """ + declaration + """
                    int result = alias(value);
                    printf("%d %d %d\\n", value, result, &alias == &callback);
                    return 0;
                }
                """, "2 2 1\n");
    }

    static Stream<Arguments> callbackExpressionPrograms() {
        return Stream.of(
                Arguments.of("callback-value-return", "identity(callback)(value)"),
                Arguments.of("callback-cast", "((Callback)callback)(value)"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("callbackExpressionPrograms")
    void callableExpressionTypesRetainNestedReferenceSignatures(String name, String invocation) throws Exception {
        agree(temporary, name, """
                #include <stdio.h>
                typedef int (*Callback)(int &);
                int bump(int &target) { return ++target; }
                Callback identity(Callback callback) { return callback; }
                int main() {
                    int value = 1;
                    Callback callback = bump;
                    int result =
                """ + invocation + ";" + """
                    printf("%d %d\\n", value, result);
                    return 0;
                }
                """, "2 2\n");
    }

    @Test void constReferenceBindingDoesNotReadTheObjectUntilItsValueIsUsed() {
        // Executing an uninitialized read is not a C++ reference oracle. Check the
        // debugger's diagnostic while proving that merely binding the alias is safe.
        var debug = new DebugApi(new SourceFile("uninitialized-reference.cpp", """
                #include <stdio.h>
                int main() {
                    int value;
                    const int &view = value;
                    printf("bound\\n");
                    return view;
                }
                """), "", LanguageMode.CPP17_ALGORITHM);
        for (int count = 0; debug.canNext() && count < 1000; count++) debug.next();
        assertFalse(debug.canNext(), "debugger exhausted the step budget");
        assertEquals(Debugger.Status.FAILED, debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"), debug.current().stop()::error);
        assertEquals(6, debug.current().stop().range().startLine());
        assertEquals("bound\n", normalize(debug.current().runtime().stdout()));
    }

    @Test void sourceReferenceSignaturesAreNotMergedWithPointerSignatures() throws Exception {
        String source = "int operation(int &); int operation(int *); int main(){return 0;}";
        Path file = temporary.resolve("distinct-signatures.cpp");
        Files.writeString(file, source);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-pedantic-errors", "-fsyntax-only", file.toString()),
                temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertEquals(0, reference.exitCode(), reference::stderr);
        var api = compiler(source);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertFalse(semantic.succeeded(), "The pointer ABI must not merge distinct C++ overload signatures");
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP005")),
                () -> semantic.errors().toString());
    }

    static void assertDebugHistory(String source, String expected, String function, String parameter) {
        var debug = new DebugApi(new SourceFile("references.cpp", source), "", LanguageMode.CPP17_ALGORITHM);
        var history = new ArrayList<Debugger.Context>();
        history.add(debug.current());
        for (int count = 0; debug.canNext() && count < 2000; count++) history.add(debug.next());
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(expected, normalize(debug.current().runtime().stdout()));
        assertTrue(history.stream().allMatch(context -> context.runtime().heap().isEmpty()));
        assertTrue(history.stream().anyMatch(context -> context.runtime().stack().stream()
                .anyMatch(frame -> frame.function().equals(function) && frame.parameters().containsKey(parameter))));
        for (int index = history.size() - 2; index >= 0; index--) assertSame(history.get(index), debug.previous());
        for (int index = 1; index < history.size(); index++) assertSame(history.get(index), debug.next());
        assertEquals(expected, normalize(debug.current().runtime().stdout()));
    }

    static void agree(Path temporary, String name, String source, String expected) throws Exception {
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                new CppDifferentialHarness.Limits(Duration.ofSeconds(30), Duration.ofSeconds(10), 50_000, 1_048_576),
                LanguageMode.CPP17_ALGORITHM).run(name, source, "");
        assertEquals(CppDifferentialHarness.Status.OK, report.outcomes().get(Backend.GXX).status(), report::describe);
        assertEquals(expected, normalize(report.outcomes().get(Backend.GXX).stdout()), report::describe);
        assertTrue(report.passed(), report::describe);
    }

    static void reject(Path temporary, String name, String source) throws Exception {
        Path file = temporary.resolve(name + ".cpp");
        Files.writeString(file, source);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-pedantic-errors", "-fsyntax-only", file.toString()),
                temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        assertNotEquals(0, reference.exitCode(), "G++ must reject this invalid reference operation");
        var api = compiler(source);
        var parser = stage(api, Parser.class);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertFalse(semantic.succeeded());
        int markedLine = (int) source.substring(0, source.indexOf("// bad")).chars().filter(c -> c == '\n').count() + 1;
        assertTrue(semantic.errors().stream().anyMatch(error -> error.range().startLine() == markedLine),
                () -> semantic.errors().toString());
    }

    static CompilerApi compiler(String source) {
        return new CompilerApi(new SourceFile("references.cpp", source), LanguageMode.CPP17_ALGORITHM);
    }

    static <T> T stage(CompilerApi api, Class<T> type) {
        return api.stages().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    static List<AstNode> nodes(AstNode root) {
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
