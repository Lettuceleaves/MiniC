package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The later definition must not retroactively make an earlier expression legal. */
@Timeout(60)
class CppIncompleteTypeExpressionTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"p->x", "sizeof(*p)", "alignof(*p)", "sizeof((*p))", "p[0].x",
            "sizeof(p[0])", "(1 ? p : p)->x", "sizeof(1 ? *p : *p)", "sizeof((p, *p))",
            "((struct S *)p)->x", "sizeof(*((struct S *)p))", "sizeof((p = p, *p))",
            "sizeof(p + 1)", "sizeof(p - p)", "sizeof(++p)", "sizeof(p++)", "sizeof(p += 1)"})
    void expressionRequiresCompleteTypeAtItsDeclarationPoint(String expression) throws Exception {
        String source = "struct S;\n"
                + "int read(struct S *p) { return (int)(" + expression + "); }\n"
                + "struct S { int x; };\n"
                + "int main() { struct S value = {7}; return read(&value); }\n";
        assertIncomplete(source, 2);
    }

    @ParameterizedTest
    @MethodSource("indirectSources")
    void indirectTypePathsCannotBypassDeclarationPointCompleteness(String source) throws Exception {
        int line = (int) source.substring(0, source.indexOf("int read")).chars().filter(c -> c == '\n').count() + 1;
        assertIncomplete(source, line);
    }

    private static Stream<String> indirectSources() {
        return Stream.of(
                "struct S *get();\nint read() { return get()->x; }",
                "struct Holder { struct S *next; };\nint read(struct Holder *h) { return h->next->x; }",
                "int read(struct S **p) { return p[0]->x; }",
                "typedef struct S (*Factory)();\nint read(Factory make) { return (int)sizeof((make)()); }",
                "typedef struct S (*Factory)();\nint read(Factory make) { return (int)sizeof((*make)()); }",
                "typedef struct S *(*Factory)();\nint read(Factory make) { return (make)()->x; }",
                "struct Holder { struct S (*make)(); };\nint read(struct Holder *h) { return (int)sizeof(h->make()); }",
                "int read(struct S *p) { *p = *p; return 0; }"
        ).map(body -> "struct S;\n" + body + "\nstruct S { int x; };\nint main(){return 0;}\n");
    }

    @Test void globalQueryAlsoUsesCompletenessAtTheInitializerLocation() throws Exception {
        assertIncomplete("struct S;\nstruct S *pointer;\nint value=sizeof(*pointer);\n"
                + "struct S { int x; };\nint main(){return 0;}\n", 3);
    }

    @Test void unaryCastOperandRetainsItsIncompleteAggregateIdentity() throws Exception {
        assertIncomplete("struct S;\nint read(struct S *p) { return sizeof(*(struct S *)p); }\n"
                + "struct S { int x; };\nint main(){return 0;}\n", 2);
    }

    @Test void pointersStayUsableBeforeCompletionAndObjectAccessWorksAfterCompletion() throws Exception {
        String source = """
                #include <stdio.h>
                struct S;
                struct S *identity(struct S *p) { return &*p; }
                int pointerQueries(struct S *p) { return sizeof(p) + alignof(p) + sizeof(&*p); }
                struct S { int x; };
                struct Holder { struct S *next; struct S (*make)(); };
                struct S make() { struct S value = {7}; return value; }
                int read(struct S *p, struct Holder *h) {
                    return p[0].x + (p + 0)->x + (1 ? p : p)->x + ((struct S *)p)->x + identity(p)->x
                           + h->next->x + (h->make)().x + sizeof(1 ? *p : *p) + alignof(*p);
                }
                int main() {
                    struct S value = {7};
                    struct Holder holder;
                    holder.next = &value;
                    holder.make = make;
                    printf("%d %d\\n", pointerQueries(identity(&value)), read(&value, &holder));
                    return 0;
                }
                """;
        var report = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(),
                LanguageMode.CPP17_ALGORITHM).run("incomplete-pointer-then-definition", source, "");
        assertTrue(report.passed(), report::describe);
        report.outcomes().values().forEach(outcome -> assertEquals("24 57\n", outcome.stdout().replace("\r\n", "\n"), report::describe));
    }

    private void assertIncomplete(String source, int line) throws Exception {
        Path referenceSource = temporary.resolve("incomplete.cpp");
        Files.writeString(referenceSource, source);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-fsyntax-only", referenceSource.toString()), temporary, "", Duration.ofSeconds(15), 64_000);
        assertFalse(reference.timedOut());
        assertFalse(reference.outputExceeded());
        assertNotEquals(0, reference.exitCode(), "G++ must reject the incomplete object use before testing MiniC");
        var compiler = new CompilerApi(new SourceFile("incomplete-expression.cpp", source), LanguageMode.CPP17_ALGORITHM);
        var parser = compiler.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertFalse(semantic.succeeded(), "The class is incomplete where the function body is defined");
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP005")
                        && d.message().contains("不完整") && d.range().startLine() == line),
                () -> semantic.errors().toString());
    }
}
