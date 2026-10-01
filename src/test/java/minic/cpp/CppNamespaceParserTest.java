package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.parser.node.Declaration.NamespaceDecl;
import minic.compiler.parser.node.Declaration.UsingDecl;
import minic.compiler.parser.node.Declaration.StructDecl;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-frontend")
@Timeout(10)
final class CppNamespaceParserTest {
    @Test void retainsMixedTopLevelSourceOrderAndLegacyProgramConstructors() {
        var parser = parse("int first; typedef int Number; struct Box { int x; }; int main() { return 0; }");
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        Program program = parser.result().program();
        assertEquals(List.of("GlobalVarDecl", "TypedefDecl", "StructDecl", "FunctionDecl"),
                program.declarations().stream().map(n -> n.getClass().getSimpleName()).toList());
        Program legacy = new Program(program.structs(), program.enums(), program.typedefs(),
                program.globals(), program.functions(), program.range());
        assertEquals(program.declarations(), legacy.declarations());
    }

    @Test void namespacesRemainNestedAndReopenedInsteadOfFlattened() {
        var parser = parse("namespace A { int x; namespace B { int f() { return 1; } } } "
                + "namespace A { int y; } namespace C::D { int z; } int main() { return 0; }");
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        Program program = parser.result().program();
        var ordered = program.declarations();
        assertEquals(4, ordered.size());
        var first = assertInstanceOf(NamespaceDecl.class, ordered.getFirst());
        assertEquals(List.of("A"), first.name().segments());
        assertEquals(List.of("C", "D"), assertInstanceOf(NamespaceDecl.class, ordered.get(2)).name().segments());
        var members = first.declarations();
        assertEquals(List.of("GlobalVarDecl", "NamespaceDecl"), members.stream().map(n -> n.getClass().getSimpleName()).toList());
        assertTrue(program.globals().isEmpty(), "Namespace members must not leak into global declarations");
        assertEquals(List.of("main"), program.functions().stream().map(f -> f.name()).toList());
    }

    @Test void retainsUsingFormsAtNamespaceAndBlockScope() {
        var parser = parse("namespace A { int x; } using namespace A; using A::x; "
                + "int main() { using namespace ::A; using ::A::x; return x; }");
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        var ordered = parser.result().program().declarations();
        assertTrue(assertInstanceOf(UsingDecl.class, ordered.get(1)).namespaceDirective());
        assertFalse(assertInstanceOf(UsingDecl.class, ordered.get(2)).namespaceDirective());
        var statements = parser.result().program().functions().getFirst().body().statements();
        assertTrue(assertInstanceOf(UsingDecl.class, statements.getFirst()).target().global());
    }

    @Test void qualifiedExpressionsKeepTheirSpellingAndExactSourceRange() {
        String content = "// 中文\nint main() { return ::A :: B::value; }";
        var source = new SourceFile("names.cpp", content);
        var parser = parse(content);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        var returned = (ReturnStmt) parser.result().program().functions().getFirst().body().statements().getFirst();
        var expression = assertInstanceOf(Expression.QualifiedNameExpr.class, returned.expression());
        assertEquals(List.of("A", "B", "value"), expression.name().segments());
        assertTrue(expression.name().global());
        assertEquals("::A :: B::value", source.text(expression.range()));
        assertEquals(2, expression.range().startLine());
    }

    @Test void namespaceTypeScopesAndAnonymousAggregatesDoNotLeakIntoGlobalScope() {
        var parser = parse("namespace A { typedef int Local; Local value; struct { int x; } item; } int main(){ return 0; }");
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertTrue(parser.result().program().structs().isEmpty());
        var namespace = assertInstanceOf(NamespaceDecl.class, parser.result().program().declarations().getFirst());
        assertTrue(namespace.declarations().stream().anyMatch(StructDecl.class::isInstance));
        var leak = parse("namespace A { typedef int Local; } Local leaked;");
        assertFalse(leak.succeeded(), "A namespace typedef must not become a global type");
    }

    @Test void unresolvedNamesAreRejectedInsideEveryCompositeExpressionAndStatement() {
        for (String body : List.of("return 1 ? ::x : 0;", "return (int)::x;", "return sizeof(::x);",
                "return (0, ::x);", "int a[1] = {::x}; return 0;", "do { return ::x; } while(0); return 0;",
                "switch(1) { case 1: return ::x; default: return 0; }", "return ::f();",
                "for (;;) { return ::x; }", "::x++; return 0;")) {
            var api = new CompilerApi(new SourceFile("nested.cpp", "int main(){ " + body + " }"), LanguageMode.CPP17_ALGORITHM);
            assertThrows(IllegalStateException.class, api::runToIr, body);
            var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                    .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
            assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP003")),
                    () -> body + ": " + semantic.errors());
        }
    }

    @Test void independentSemanticAnalyzerAlsoRejectsUnresolvedAst() {
        var parser = parse("int main() { return ::missing; }");
        assertTrue(parser.succeeded());
        var semantic = new SemanticAnalyzer(parser.result().program());
        semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP003")));
    }

    @Test void malformedNamespaceAndNamesAreRejectedWithoutHanging() {
        for (String source : List.of("namespace A { int x;", "namespace A:: { }", "using namespace ;",
                "using A::;", "using x;", "int main() { return A::; }", "namespace A { namespace ; }")) {
            Parser parser = parse(source);
            assertFalse(parser.succeeded(), source);
            assertFalse(parser.errors().isEmpty(), source);
        }
    }

    @Test void unsupportedAliasAndQualifiedTypeLookupHaveExplicitDiagnostics() {
        for (String source : List.of("namespace Alias = A;", "using Value = int;")) {
            Parser parser = parse(source);
            assertFalse(parser.succeeded(), source);
            assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")),
                    () -> source + ": " + parser.errors());
        }
        for (String source : List.of("A::Value item;", "int main() { A::Value item; return 0; }")) {
            Parser parser = parse(source);
            assertFalse(parser.succeeded(), source);
            assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("PAR001")),
                    () -> source + ": " + parser.errors());
            assertTrue(parser.errors().stream().noneMatch(d -> d.code().equals("CPP001")),
                    () -> "Unknown types require a lookup diagnostic: " + parser.errors());
        }
    }

    @Test void parsedButUnresolvedCppNamesCannotSilentlyReachIr() {
        for (String source : List.of("using namespace A; int main() { return 0; }",
                "int main() { return ::missing; }",
                "int main() { using A::value; return 0; }")) {
            var api = new CompilerApi(new SourceFile("unresolved.cpp", source), LanguageMode.CPP17_ALGORITHM);
            assertThrows(IllegalStateException.class, api::runToIr, source);
            var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                    .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
            assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP003")),
                    () -> source + ": " + semantic.errors());
        }
    }

    @Test void cModeStillAcceptsNamespaceAndUsingAsOrdinaryIdentifiers() {
        var api = new CompilerApi(new SourceFile("legacy.mc", "int main() { int namespace = 2; int using = 3; return namespace + using; }"));
        assertNotNull(api.runToIr().findFunction("main").orElseThrow());
    }

    private static Parser parse(String text) {
        var lexer = new Lexer(new SourceFile("names.cpp", text), LanguageMode.CPP17_ALGORITHM);
        var parser = new Parser(lexer.lex().tokens(), LanguageMode.CPP17_ALGORITHM, true);
        parser.parse();
        return parser;
    }

}
