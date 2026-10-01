package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.GlobalVarDecl;
import minic.compiler.parser.node.Declaration.NamespaceDecl;
import minic.compiler.parser.node.Declaration.StructDecl;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement.VarDeclStmt;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Parser-only checks keep lookup/disambiguation failures separate from semantic binding. */
@Tag("cpp-frontend")
@Timeout(10)
final class CppTypeParserTest {
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"qualified-slots", "global-type-queries", "using-reopen", "isolated-layout", "nested-qualified",
            "local-value-shadow", "declaration-point-scopes", "forward-self-pointer", "same-type-paths"})
    void namespaceTypeFixturesParseBeforeSemanticBinding(String fixture) throws Exception {
        successfulParse(resource(fixture));
    }

    @Test
    void declaratorIsVisibleInSizeofInitializerAndControlledScopesRestoreOuterType() throws Exception {
        var parser = successfulParse(resource("declaration-point-scopes"));
        var program = parser.result().program();
        var init = program.declarations().stream().filter(NamespaceDecl.class::isInstance)
                .map(NamespaceDecl.class::cast).filter(namespace -> namespace.name().segments().getFirst().equals("Init"))
                .findFirst().orElseThrow();
        var global = init.declarations().stream().filter(GlobalVarDecl.class::isInstance)
                .map(GlobalVarDecl.class::cast).findFirst().orElseThrow();
        var ownGlobalSize = assertInstanceOf(Expression.SizeofExpr.class, global.initializer());
        assertNull(ownGlobalSize.queriedType());
        var groupedName = assertInstanceOf(Expression.GroupingExpr.class, ownGlobalSize.expression());
        assertEquals("Word", assertInstanceOf(Expression.NameExpr.class, groupedName.expression()).name());

        var localSize = program.functions().stream().filter(function -> function.name().equals("local_size"))
                .findFirst().orElseThrow();
        var local = assertInstanceOf(VarDeclStmt.class, localSize.body().statements().getFirst());
        assertNull(assertInstanceOf(Expression.SizeofExpr.class, local.initializer()).queriedType());
        var main = program.functions().stream().filter(function -> function.name().equals("main")).findFirst().orElseThrow();
        var restored = main.body().statements().stream().filter(VarDeclStmt.class::isInstance).map(VarDeclStmt.class::cast)
                .filter(variable -> variable.name().startsWith("after_")).toList();
        assertEquals(5, restored.size());
        for (var variable : restored) {
            var size = assertInstanceOf(Expression.SizeofExpr.class, variable.initializer());
            assertEquals(MiniType.LONG_LONG, size.queriedType(), variable.name());
            assertNull(size.expression(), variable.name());
        }
    }

    @Test
    void parameterScopesHideTypesOnlyUntilTheirOwnDeclaratorEnds() {
        var parser = successfulParse("namespace A { typedef long long Number; } using A::Number; "
                + "int first(int Number); Number global; "
                + "int second(int (*operation)(int Number), Number value) { return (int)value; }");
        assertEquals(MiniType.LONG_LONG, parser.result().program().globals().getFirst().type());
        assertEquals(MiniType.LONG_LONG, parser.result().program().functions().getLast().parameters().getLast().type());

        var invalid = parse("namespace A { typedef int Number; } using A::Number;\n"
                + "int invalid(int Number, Number value) { return value; }");
        assertFalse(invalid.succeeded());
        assertTrue(invalid.errors().stream().anyMatch(error -> error.range().startLine() == 2
                && error.message().contains("Number") && error.code().equals("PAR001")), () -> invalid.errors().toString());
    }

    @Test
    void forwardAndSelfPointerUseTheSameCanonicalAggregateIdentity() {
        var parser = successfulParse("namespace A { struct Node; typedef Node Link; "
                + "struct Node { int value; Node *next; }; } "
                + "namespace B { struct Node { long long value; }; } A::Link first; B::Node second;");
        var program = parser.result().program();
        var namespace = assertInstanceOf(NamespaceDecl.class, program.declarations().getFirst());
        var declarations = namespace.declarations().stream().filter(StructDecl.class::isInstance)
                .map(StructDecl.class::cast).toList();
        assertEquals(2, declarations.size());
        assertEquals("::A::Node", declarations.getFirst().name());
        assertEquals(declarations.getFirst().name(), declarations.getLast().name());
        assertEquals(MiniType.struct("::A::Node").pointerTo(), declarations.getLast().fields().getLast().type());
        assertEquals(MiniType.struct("::A::Node"), program.globals().getFirst().type());
        assertEquals(MiniType.struct("::B::Node"), program.globals().getLast().type());
    }

    @Test
    void qualifiedCastAndSizeofRetainOriginalSourceText() {
        String content = "namespace A { typedef long long Word; struct Item { int value; }; }\n"
                + "int main() { int value = 2; A::Word casted = (::A::Word)value; int size = sizeof(A::Item); return size; }";
        var source = new SourceFile("types.cpp", content);
        var parser = successfulParse(content);
        var statements = parser.result().program().functions().getFirst().body().statements();
        var cast = assertInstanceOf(Expression.CastExpr.class, assertInstanceOf(VarDeclStmt.class, statements.get(1)).initializer());
        assertEquals(MiniType.LONG_LONG, cast.targetType());
        assertEquals("(::A::Word)value", source.text(cast.range()));
        var size = assertInstanceOf(Expression.SizeofExpr.class, assertInstanceOf(VarDeclStmt.class, statements.get(2)).initializer());
        assertEquals(MiniType.struct("::A::Item"), size.queriedType());
        assertEquals("sizeof(A::Item)", source.text(size.range()));
    }

    private static Parser successfulParse(String content) {
        var parser = parse(content);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        return parser;
    }

    private static Parser parse(String content) {
        var api = new CompilerApi(new SourceFile("types.cpp", content), LanguageMode.CPP17_ALGORITHM);
        var parser = api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        api.runThrough(parser);
        return parser;
    }

    private static String resource(String name) throws IOException {
        try (var stream = CppTypeParserTest.class.getResourceAsStream("/cpp/namespace-types/" + name + ".cpp")) {
            if (stream == null) throw new IOException("Missing namespace type fixture: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
