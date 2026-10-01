package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Expression.ThisExpr;
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

/** F03c2 acceptance: bind qualified definitions to previously declared member entities. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppOutOfLineMemberTest {
    @TempDir Path temporary;

    private static final String DEBUG_SOURCE = """
            #include <stdio.h>
            namespace Counter {
                class Value {
                    int value;
                    int increment(int declarationAmount);
                public:
                    int add(int amount);
                };
            }
            int main() {
                Counter::Value counter = {};
                printf("%d\\n", counter.add(3));
                return 0;
            }
            int Counter::Value::add(int amount) {
                int before = value;
                return this->increment(amount) + before;
            }
            int Counter::Value::increment(int amount) {
                this->value += amount;
                return value;
            }
            """;

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("qualified-private-this-call-before-definition", DEBUG_SOURCE, "3\n"),
                Arguments.of("namespace-relative-owner-and-definition-point-lookup", """
                        #include <stdio.h>
                        namespace N { struct Box { int value; int read(); }; }
                        namespace N {
                            int extra = 6;
                            int Box::read() { return value + extra; }
                        }
                        int main() { N::Box value = {4}; printf("%d\\n", value.read()); return 0; }
                        """, "10\n"),
                Arguments.of("return-type-before-owner-parameter-and-body-after-owner", """
                        #include <stdio.h>
                        typedef long long Number;
                        namespace N {
                            typedef int Number;
                            struct Box { int value; ::Number add(Number); Box *self(); };
                            int offset = 2;
                        }
                        Number N::Box::add(Number amount) {
                            value += amount;
                            return (int)sizeof(Number) + value + offset;
                        }
                        N::Box *N::Box::self() { return this; }
                        int main() {
                            N::Box box = {1};
                            printf("%lld %d\\n", box.self()->add(3), (int)sizeof(Number));
                            return 0;
                        }
                        """, "10 8\n"),
                Arguments.of("private-peer-access-prototype-parameter-names-and-local-scopes", """
                        #include <stdio.h>
                        int value = 100;
                        class Box {
                            int value;
                            int bump(int prototypeOnly);
                        public:
                            int merge(Box *other, int value);
                        };
                        int Box::merge(Box *other, int value) {
                            int result = other->bump(value);
                            { int value = 20; result += value; }
                            this->value += 2;
                            return result + this->value + other->value + ::value;
                        }
                        int Box::bump(int amount) { value += amount; return value; }
                        int main() {
                            Box first = {};
                            Box second = {};
                            printf("%d\\n", first.merge(&second, 3));
                            return 0;
                        }
                        """, "128\n"),
                Arguments.of("complete-class-self-type-recursion-and-later-method-definition", """
                        #include <stdio.h>
                        struct Link { int total(int); int read(); Link *next; int value; };
                        int Link::total(int count) {
                            if (count == 0 || next == (Link *)0) return read();
                            return value + next->total(count - 1);
                        }
                        int Link::read() { return value; }
                        int main() {
                            Link tail = {(Link *)0, 5};
                            Link head = {&tail, 3};
                            printf("%d\\n", head.total(1));
                            return 0;
                        }
                        """, "8\n"),
                Arguments.of("alias-signatures-top-level-parameter-const-and-array-adjustment", """
                        #include <stdio.h>
                        namespace N {
                            typedef int Number;
                            typedef Number *Pointer;
                            struct Box { Number apply(Number, Pointer); int sum(int values[2]); };
                        }
                        int N::Box::apply(const int amount, int *values) { return amount + values[0]; }
                        int N::Box::sum(int *values) { return values[0] + values[1]; }
                        int main() {
                            N::Box box = {};
                            int values[2] = {4, 7};
                            printf("%d %d\\n", box.apply(3, values), box.sum(values));
                            return 0;
                        }
                        """, "7 11\n"),
                Arguments.of("member-type-shadow-parameter-scope-and-scope-restoration", """
                        #include <stdio.h>
                        typedef long long Width;
                        typedef int Count;
                        struct Box { char Width; int read(int); int size(); };
                        int Box::read(int Count) { return Count + (int)sizeof(Width) + (int)sizeof(::Width); }
                        int Box::size() { Count local = 2; return local; }
                        Width after = 9;
                        int main() {
                            Box box = {'a'};
                            printf("%d %d %d\\n", box.read(3), box.size(), (int)sizeof(after));
                            return 0;
                        }
                        """, "12 2 8\n"),
                Arguments.of("same-spelling-owner-identity-and-namespace-using", """
                        #include <stdio.h>
                        namespace A { struct Box { int value; int read(); }; }
                        namespace B { struct Box { int value; int read(); }; }
                        int A::Box::read() { return value + 1; }
                        namespace B { int Box::read() { return value + 10; } }
                        using A::Box;
                        int main() {
                            Box first = {2};
                            B::Box second = {3};
                            printf("%d %d\\n", first.read(), second.read());
                            return 0;
                        }
                        """, "3 13\n"),
                Arguments.of("matching-const-method-and-nonconst-method-definitions", """
                        #include <stdio.h>
                        class Box { int value; public: int read() const; void set(int); };
                        int Box::read() const { return this->value; }
                        void Box::set(int amount) { value = amount; }
                        int main() {
                            Box box = {};
                            box.set(7);
                            const Box *pointer = &box;
                            printf("%d %d\\n", pointer->read(), box.read());
                            return 0;
                        }
                        """, "7 7\n"),
                Arguments.of("matching-scalar-return-cv-pointer-return-cv-and-referent-cv", """
                        #include <stdio.h>
                        struct Box { int value; const int read(); int *const address(); const int *view(); };
                        const int Box::read() { return value; }
                        int *const Box::address() { return &value; }
                        const int *Box::view() { return &value; }
                        int main() {
                            Box box = {2};
                            *box.address() = 9;
                            printf("%d %d\\n", box.read(), *box.view());
                            return 0;
                        }
                        """, "9 9\n")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPrograms")
    void qualifiedDefinitionsAgreeAcrossNativeDebugAndCpp17(String name, String source, String expected) throws Exception {
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

    static Stream<Arguments> invalidDefinitions() {
        return Stream.of(
                Arguments.of("undeclared-method", "struct Box { int value; };\nint Box::missing() { return 1; } // bad\n"),
                Arguments.of("duplicate-outside-definition", "struct Box { int read(); };\nint Box::read(){return 1;}\nint Box::read(){return 2;} // bad\n"),
                Arguments.of("inline-and-outside-duplicate", "struct Box { int read(){return 1;} };\nint Box::read(){return 2;} // bad\n"),
                Arguments.of("return-type-mismatch", "struct Box { int read(); };\nlong long Box::read(){return 1;} // bad\n"),
                Arguments.of("parameter-type-mismatch", "struct Box { int read(int); };\nint Box::read(long long value){return (int)value;} // bad\n"),
                Arguments.of("parameter-count-mismatch", "struct Box { int read(int); };\nint Box::read(){return 1;} // bad\n"),
                Arguments.of("pointee-const-mismatch", "struct Box { int read(const int *); };\nint Box::read(int *value){return *value;} // bad\n"),
                Arguments.of("variadic-signature-mismatch", "struct Box { int read(int,...); };\nint Box::read(int value){return value;} // bad\n"),
                Arguments.of("unrelated-definition-namespace", "namespace A {struct Box {int read();};}\nnamespace B {\nint A::Box::read(){return 1;} // bad\n}\n"),
                Arguments.of("later-namespace-value-remains-invisible", "namespace N {struct Box {int read();};}\nint N::Box::read(){\nreturn later; // bad\n}\nnamespace N {int later=1;}\n"),
                Arguments.of("prototype-parameter-name-does-not-leak", "struct Box {int read(int prototypeOnly);};\nint Box::read(int amount){\nreturn prototypeOnly; // bad\n}\n"),
                Arguments.of("outside-definition-does-not-open-access", "class Box {int hidden();};\nint Box::hidden(){return 1;}\nint main(){Box value={};\nreturn value.hidden(); // bad\n}\n"),
                Arguments.of("missing-receiver-const", "struct Box {int read() const;};\nint Box::read(){return 1;} // bad\n"),
                Arguments.of("extra-receiver-const", "struct Box {int read();};\nint Box::read() const {return 1;} // bad\n"),
                Arguments.of("const-definition-keeps-const-this", "struct Box {int value; int read() const;};\nint Box::read() const {\nreturn ++value; // bad\n}\n"),
                Arguments.of("missing-scalar-return-const", "struct Box {const int read();};\nint Box::read(){return 1;} // bad\n"),
                Arguments.of("extra-scalar-return-const", "struct Box {int read();};\nconst int Box::read(){return 1;} // bad\n"),
                Arguments.of("missing-scalar-return-volatile", "struct Box {volatile int read();};\nint Box::read(){return 1;} // bad\n"),
                Arguments.of("missing-pointer-return-const", "struct Box {int *const read();};\nint *Box::read(){return (int *)0;} // bad\n"),
                Arguments.of("pointer-return-const-is-not-referent-const", "struct Box {int *const read();};\nconst int *Box::read(){return (int *)0;} // bad\n"),
                Arguments.of("definition-keeps-its-const-value-parameter", "struct Box {int read(int);};\nint Box::read(const int value){\nreturn ++value; // bad\n}\n")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDefinitions")
    void mismatchesAndLookupFailuresAreSemanticErrorsAtTheirSource(String name, String body) throws Exception {
        String source = body.contains("int main(") ? body : body + "int main(){return 0;}\n";
        Path file = temporary.resolve(name + ".cpp");
        Files.writeString(file, source);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-pedantic-errors", "-fsyntax-only", file.toString()),
                temporary, "", Duration.ofSeconds(20), 65_536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        assertNotEquals(0, reference.exitCode(), "G++17 must reject the invalid definition or use");
        var api = compiler(source);
        var parser = stage(api, Parser.class);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> "Qualified function syntax must parse before semantic validation: " + parser.errors());
        assertFalse(semantic.succeeded(), "Invalid definitions must not overwrite a declared member entity");
        int badLine = lineOf(source, "// bad");
        assertTrue(semantic.errors().stream().anyMatch(error -> error.range() != null && error.range().startLine() == badLine),
                () -> "Expected semantic diagnostic at line " + badLine + ": " + semantic.errors());
    }

    @Test void prototypeAndDefinitionShareOneCoreSymbolButRetainDistinctSourceOrigins() {
        SourceFile file = new SourceFile("out-of-line.cpp", DEBUG_SOURCE);
        var api = new CompilerApi(file, LanguageMode.CPP17_ALGORITHM);
        var parser = stage(api, Parser.class);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var result = semantic.semanticResult();
        assertSame(parser.result().program(), result.sourceProgram());
        assertNull(AstChildren.firstCppSyntax(result.program()), "Qualified owner metadata must be fully normalized");
        var methods = nodes(result.sourceProgram()).stream().filter(FunctionDecl.class::isInstance)
                .map(FunctionDecl.class::cast).filter(method -> result.sourceToCore().get(method) instanceof FunctionDecl core
                        && result.displayNames().getOrDefault(core.name(), "").startsWith("Counter::Value::")).toList();
        assertEquals(4, methods.size(), "Both in-class declarations and out-of-class definitions remain visible to tools");
        for (String name : List.of("add", "increment")) {
            var matching = methods.stream().filter(method -> {
                var core = (FunctionDecl) result.sourceToCore().get(method);
                return result.displayNames().get(core.name()).equals("Counter::Value::" + name);
            }).toList();
            assertEquals(2, matching.size());
            FunctionDecl prototype = matching.stream().filter(method -> !method.hasBody()).findFirst().orElseThrow();
            FunctionDecl definition = matching.stream().filter(FunctionDecl::hasBody).findFirst().orElseThrow();
            FunctionDecl corePrototype = (FunctionDecl) result.sourceToCore().get(prototype);
            FunctionDecl coreDefinition = (FunctionDecl) result.sourceToCore().get(definition);
            assertEquals(corePrototype.name(), coreDefinition.name());
            assertNotSame(corePrototype, coreDefinition, "Reverse source origins must remain one-to-one");
            assertEquals(prototype.range(), corePrototype.range());
            assertEquals(definition.range(), coreDefinition.range());
            assertEquals(definition.parameters().size() + 1, coreDefinition.parameters().size());
            assertEquals("this", result.displayNames().get(coreDefinition.parameters().getFirst().name()));
            assertSame(coreDefinition.body(), result.sourceToCore().get(definition.body()));
        }
        var selves = nodes(result.sourceProgram()).stream().filter(ThisExpr.class::isInstance).map(ThisExpr.class::cast).toList();
        assertEquals(2, selves.size());
        for (ThisExpr self : selves) {
            assertEquals(self.range(), result.sourceToCore().get(self).range());
            assertTrue(result.typeOf(self).orElseThrow().isPointer());
        }
    }

    @Test void debugDefinitionLocationsThisFramesAndHistoryReferToTheOriginalSource() {
        SourceFile file = new SourceFile("out-of-line.cpp", DEBUG_SOURCE);
        var debug = new DebugApi(file, "", LanguageMode.CPP17_ALGORITHM);
        List<Debugger.Context> history = new ArrayList<>();
        history.add(debug.current());
        for (int steps = 0; debug.canNext() && steps < 1_000; steps++) history.add(debug.next());
        assertFalse(debug.canNext(), "The fixture exceeded its bounded step budget");
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals("3\n", normalize(debug.current().runtime().stdout()));
        var nested = history.stream().filter(context -> context.runtime().stack().stream()
                        .anyMatch(frame -> frame.function().equals("Counter::Value::increment"))
                        && context.runtime().stack().stream().anyMatch(frame -> frame.function().equals("Counter::Value::add")))
                .findFirst().orElseThrow(() -> new AssertionError("Qualified definitions lost their source-facing method frames"));
        var frame = nested.runtime().stack().stream().filter(item -> item.function().equals("Counter::Value::increment"))
                .findFirst().orElseThrow();
        assertEquals(3, frame.parameters().get("amount").integer());
        assertNotNull(frame.parameters().get("this"));
        assertFalse(frame.parameters().containsKey("declarationAmount"), "A declaration's parameter name is not the definition's local name");
        int line = lineOf(DEBUG_SOURCE, "this->value += amount;");
        assertTrue(nested.program().line(line).stream().anyMatch(location -> location.function().equals("Counter::Value::increment")
                && file.text(location.instruction().range()).equals("this->value += amount")));
        for (int index = history.size() - 2; index >= 0; index--) assertSame(history.get(index), debug.previous());
        assertFalse(debug.canPrevious());
        for (int index = 1; index < history.size(); index++) assertSame(history.get(index), debug.next());
        assertEquals("3\n", normalize(debug.current().runtime().stdout()));
    }

    @Test void malformedSignatureAndBodyRestoreTheDefinitionNamespaceBeforeLaterDeclarations() {
        for (String definition : List.of("int N::Box::read(int n { return n; }",
                "int N::Box::read(int n) { int broken = ; return n; }")) {
            String source = "typedef long long Width; namespace N {typedef char Width; struct Box {int read(int);};}\n"
                    + definition + "\nWidth after;\nint main(){return 0;}\n";
            var api = compiler(source);
            var parser = stage(api, Parser.class);
            api.runThrough(parser);
            assertFalse(parser.succeeded(), "The malformed member must produce a syntax diagnostic");
            var after = parser.result().program().globals().stream().filter(global -> global.name().equals("after"))
                    .findFirst().orElseThrow(() -> new AssertionError("Recovery consumed the later global: " + parser.errors()));
            assertEquals(MiniType.LONG_LONG, after.type(), "The class owner's namespace leaked after a failed parse");
            assertTrue(parser.result().program().functions().stream().anyMatch(function -> function.name().equals("main")));
        }
    }

    @Test void qualifiedNamespaceFreeFunctionRemainsAnExplicitUnsupportedBoundary() throws Exception {
        String source = "namespace N {int function();}\nint N::function(){return 1;}\nint main(){return N::function();}\n";
        Path file = temporary.resolve("qualified-free-function.cpp");
        Files.writeString(file, source);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-pedantic-errors", "-fsyntax-only", file.toString()),
                temporary, "", Duration.ofSeconds(20), 65_536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        assertEquals(0, reference.exitCode(), reference::stderr);
        var api = compiler(source);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(stage(api, Parser.class).succeeded());
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP005") && error.range().startLine() == 2),
                () -> semantic.errors().toString());
    }

    private static CompilerApi compiler(String text) {
        return new CompilerApi(new SourceFile("out-of-line.cpp", text), LanguageMode.CPP17_ALGORITHM);
    }

    private static <T> T stage(CompilerApi api, Class<T> type) {
        return api.stages().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static List<AstNode> nodes(AstNode root) {
        List<AstNode> result = new ArrayList<>();
        Set<AstNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        result.add(root);
        seen.add(root);
        for (int index = 0; index < result.size(); index++) {
            for (AstNode child : AstChildren.of(result.get(index))) if (seen.add(child)) result.add(child);
        }
        return result;
    }

    private static int lineOf(String text, String marker) {
        int offset = text.indexOf(marker);
        if (offset < 0) throw new IllegalArgumentException("Missing source marker: " + marker);
        return (int) text.substring(0, offset).chars().filter(value -> value == '\n').count() + 1;
    }

    private static String normalize(String text) { return text.replace("\r\n", "\n"); }
}
