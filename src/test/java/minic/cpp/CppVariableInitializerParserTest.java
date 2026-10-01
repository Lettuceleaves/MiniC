package minic.cpp;

import minic.SourceRange;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.ir.IrLowerer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Source initialization spelling is retained separately from its existing executable projection. */
@Timeout(90)
class CppVariableInitializerParserTest {
    @TempDir Path temporary;

    static Stream<Arguments> forms() {
        return Stream.of(false, true).flatMap(local -> Stream.of(
                Arguments.of(local, "", CppInitializer.Kind.DEFAULT, "", 0),
                Arguments.of(local, " = 7", CppInitializer.Kind.COPY, "= 7", 1),
                Arguments.of(local, "(7)", CppInitializer.Kind.DIRECT_PAREN, "(7)", 1),
                Arguments.of(local, "{7,}", CppInitializer.Kind.DIRECT_LIST, "{7,}", 1),
                Arguments.of(local, " = {7,}", CppInitializer.Kind.COPY_LIST, "= {7,}", 1)));
    }

    @ParameterizedTest(name = "local={0}, {2}") @MethodSource("forms")
    void allFiveFormsKeepKindsArgumentsAndExactSourceRanges(boolean local, String suffix,
                                                          CppInitializer.Kind kind, String spelling, int count) {
        String declaration = "int value" + suffix + ";";
        String text = "/* 中文 */ " + (local ? "int main(){" + declaration + "return 0;}" : declaration);
        var program = successful(text).result().program();
        AstNode node = local ? locals(program).getFirst() : program.globals().getFirst();
        var source = new SourceFile("initialization.cpp", text);
        var initialization = metadata(node);
        assertNotNull(initialization);
        assertEquals(kind, initialization.kind());
        assertEquals(count, initialization.arguments().size());
        assertEquals(spelling, source.text(initialization.range()));
        assertTrue(node.range().contains(initialization.range()));
        assertEquals(declaration, source.text(node.range()));
        assertEquals(List.of(initialization), AstChildren.of(node));
        Expression projection = projection(node);
        if (kind == CppInitializer.Kind.DEFAULT) assertNull(projection);
        else if (kind == CppInitializer.Kind.COPY) assertSame(initialization.arguments().getFirst(), projection);
        else if (kind == CppInitializer.Kind.COPY_LIST) {
            var aggregate = assertInstanceOf(AggregateInitExpr.class, projection);
            assertSame(initialization.arguments().getFirst(), aggregate.values().getFirst());
            assertEquals("{7,}", source.text(aggregate.range()));
        } else assertSame(initialization, projection, "New execution forms must retain their explicit semantic guard");
    }

    @Test void existingReferenceDirectFormsKeepTheSameExpressionObjectsInBothViews() {
        var variables = locals(successful("int main(){int value=1;int &first(value);const int &second{value};return first;}").result().program());
        var first = variables.get(1);
        assertEquals(CppInitializer.Kind.DIRECT_PAREN, first.cppInitializer().kind());
        assertSame(first.cppInitializer().arguments().getFirst(), ((GroupingExpr) first.initializer()).expression());
        var second = variables.get(2);
        assertEquals(CppInitializer.Kind.DIRECT_LIST, second.cppInitializer().kind());
        assertSame(second.cppInitializer().arguments().getFirst(), ((AggregateInitExpr) second.initializer()).values().getFirst());
    }

    @Test void constructorArgumentsAndParenthesizedCommaExpressionsRemainDifferent() {
        var variables = locals(successful("int main(){int source=1;int one((source,2));int two(source,2);int empty{};return 0;}").result().program());
        assertEquals(1, variables.get(1).cppInitializer().arguments().size());
        assertInstanceOf(CommaExpr.class, ((GroupingExpr) variables.get(1).cppInitializer().arguments().getFirst()).expression());
        assertEquals(2, variables.get(2).cppInitializer().arguments().size());
        assertTrue(variables.get(3).cppInitializer().arguments().isEmpty());
    }

    @Test void typeAndValueNamesControlTheDeclaratorBoundaryAtTheirDeclarationPoint() {
        var program = successful("typedef int Type;int input=3;int object(input);int function(Type);"
                + "int main(){int Type=5;int local(Type);return 0;}").result().program();
        assertEquals(CppInitializer.Kind.DIRECT_PAREN, program.globals().get(1).cppInitializer().kind());
        assertTrue(program.functions().stream().anyMatch(function -> function.name().equals("function")));
        assertEquals(CppInitializer.Kind.DIRECT_PAREN, locals(program).get(1).cppInitializer().kind());
    }

    @Test void emptyAndTypeShapedParenthesesRemainFunctionDeclarations() {
        var program = successful("struct Box{int value;};int empty();Box factory(Box());int (*callback)(int);").result().program();
        assertEquals(List.of("empty", "factory"), program.functions().stream().map(FunctionDecl::name).toList());
        var callback = program.globals().getFirst();
        assertTrue(callback.type().isPointer() && callback.type().pointee().isFunction());
        assertEquals(CppInitializer.Kind.DEFAULT, callback.cppInitializer().kind());
        assertNull(callback.initializer());
    }

    @Test void blockScopeFunctionDeclarationsRemainAnExplicitUnsupportedBoundary() {
        var parser = parse("int main(){int function();return 0;}", LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(diagnostic -> diagnostic.code().equals("CPP001")));
    }

    @ParameterizedTest @ValueSource(strings = {"(1)", "{1}", "(1,2)", "{}"})
    void newlyParsedObjectConstructionCannotSilentlyUseCAggregateSemantics(String initializer) {
        var parser = successful("int main(){int value" + initializer + ";return 0;}");
        var semantic = new SemanticAnalyzer(parser.result().program());
        semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP005")), () -> semantic.errors().toString());
    }

    @Test void existingCopyFormsAndReferenceFormsStillExecuteAcrossThreeBackends() throws Exception {
        CppReferenceTest.agree(temporary, "variable-initialization-compatibility", """
                #include <stdio.h>
                struct Pair{int first;int second;};
                int global=3;
                Pair pair={4,5};
                extern int global;
                int main(){int value=global;Pair local={pair.first,pair.second};
                    int &alias(value);const int &view{alias};alias+=local.first;
                    printf("%d %d %d\\n",value,view,local.second);return 0;}
                """, "7 7 5\n");
    }

    @Test void defaultMetadataDoesNotChangeExternDefinitionsOrSwitchInitializationBarriers() {
        var parser = successful("extern int value;int value;int main(){switch(0){case 0:int local;case 1:break;}return value;}");
        assertNull(parser.result().program().globals().getFirst().initializer());
        assertEquals(CppInitializer.Kind.DEFAULT, parser.result().program().globals().getFirst().cppInitializer().kind());
        var semantic = new SemanticAnalyzer(parser.result().program());
        semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
    }

    @Test void sourceAndCoreDeclarationsKeepOriginalRangesAndIndependentMetadata() {
        var parser = successful("int global=3;int main(){int value=global;return value;}");
        var semantic = new SemanticAnalyzer(parser.result().program()); semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var sourceGlobal = parser.result().program().globals().getFirst();
        var sourceLocal = locals(parser.result().program()).getFirst();
        var coreGlobal = (GlobalVarDecl) semantic.semanticResult().sourceToCore().get(sourceGlobal);
        var coreLocal = (VarDeclStmt) semantic.semanticResult().sourceToCore().get(sourceLocal);
        assertEquals(sourceGlobal.range(), coreGlobal.range());
        assertEquals(sourceLocal.range(), coreLocal.range());
        assertNotNull(sourceGlobal.cppInitializer());
        assertNotNull(sourceLocal.cppInitializer());
        assertNull(coreGlobal.cppInitializer());
        assertNull(coreLocal.cppInitializer());
        assertNotNull(semantic.semanticResult().sourceToCore().get(sourceLocal.initializer()));
        assertEquals(sourceLocal.initializer().range(), semantic.semanticResult().sourceToCore().get(sourceLocal.initializer()).range());
    }

    @Test void cModeAndOldConstructorsKeepTheirExistingApi() {
        var program = parse("int global=3;int main(){int value={4};return value;}", LanguageMode.C).result().program();
        assertNull(program.globals().getFirst().cppInitializer());
        assertNull(locals(program).getFirst().cppInitializer());
        assertTrue(CppReferenceTest.nodes(program).stream().noneMatch(CppInitializer.class::isInstance));
        var range = new SourceRange(1, 0, 1, 1);
        var value = new IntegerLiteralExpr(1, "1", range);
        assertNull(new GlobalVarDecl("value", MiniType.INT, value, false, List.of(), range).cppInitializer());
        assertNull(new VarDeclStmt("value", MiniType.INT, value, List.of(), range).cppInitializer());
        assertNull(new VarDeclStmt("value", MiniType.INT, value, range).cppInitializer());
    }

    @Test void metadataCannotDisagreeWithItsCompatibilityProjectionOrBypassCoreGuards() {
        var range = new SourceRange(1, 0, 1, 1);
        var first = new IntegerLiteralExpr(1, "1", range);
        var second = new IntegerLiteralExpr(1, "1", range);
        var metadata = new CppInitializer(CppInitializer.Kind.COPY, List.of(first), range);
        assertThrows(IllegalArgumentException.class,
                () -> new VarDeclStmt("value", MiniType.INT, second, List.of(), metadata, range));
        assertThrows(IllegalArgumentException.class,
                () -> new GlobalVarDecl("value", MiniType.INT, second, false, List.of(), metadata, range));
        var defaultInitialization = new CppInitializer(CppInitializer.Kind.DEFAULT, List.of(), range);
        assertThrows(IllegalArgumentException.class,
                () -> new GlobalVarDecl("value", MiniType.INT, defaultInitialization, false, List.of(), defaultInitialization, range));
        var global = new GlobalVarDecl("value", MiniType.INT, first, false, List.of(), metadata, range);
        var cProgram = new Program(List.of(), List.of(), List.of(), List.of(global), List.of(), List.of(global), range);
        assertSame(metadata, AstChildren.firstCppSyntax(cProgram));
        var semantic = new SemanticAnalyzer(cProgram); semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP002")));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer(cProgram, Map.of(), Map.of()));
    }

    @Test void nestedCopyListsShareTheirExistingArgumentNodesWithoutDoubleTraversal() {
        var variable = locals(successful("struct Box{int values[2];};int main(){Box value={{1,2}};return 0;}").result().program()).getFirst();
        var projection = (AggregateInitExpr) variable.initializer();
        var nested = projection.values().getFirst();
        assertSame(nested, variable.cppInitializer().arguments().getFirst());
        assertEquals(1, CppReferenceTest.nodes(variable).stream().filter(node -> node == nested).count());
    }

    @ParameterizedTest @ValueSource(strings = {"(1,)", "{1,,2}"})
    void malformedDirectInitializersDoNotConsumeTheNextFunction(String initializer) {
        var parser = parse("int main(){int value"+initializer+";return 0;}int after(){return 2;}", LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.result().program().functions().stream().anyMatch(function -> function.name().equals("after")));
    }

    private static Expression projection(AstNode node) { return node instanceof GlobalVarDecl global ? global.initializer() : ((VarDeclStmt) node).initializer(); }
    private static CppInitializer metadata(AstNode node) { return node instanceof GlobalVarDecl global ? global.cppInitializer() : ((VarDeclStmt) node).cppInitializer(); }
    private static List<VarDeclStmt> locals(Program program) {
        return CppReferenceTest.nodes(program.functions().stream().filter(function -> function.name().equals("main")).findFirst().orElseThrow())
                .stream().filter(VarDeclStmt.class::isInstance).map(VarDeclStmt.class::cast).toList();
    }
    private static Parser successful(String text) {
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        return parser;
    }
    private static Parser parse(String text, LanguageMode mode) {
        var lexer = new Lexer(new SourceFile("initialization.cpp", text), mode);
        var parser = new Parser(lexer.lex().tokens(), mode, true);
        parser.parse();
        return parser;
    }
}
