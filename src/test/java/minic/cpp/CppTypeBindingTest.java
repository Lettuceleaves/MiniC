package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrLowerer;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.cpp.CppNameBinder;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Binder-only slice: old explicit struct syntax and source ASTs with canonical type identities. */
class CppTypeBindingTest {
    @Test void namespaceStructsAndTypedefsNormalizeToDistinctCoreLayouts() {
        var result = success(parse("namespace A {struct S{int x;}; typedef struct S T; "
                + "int get(struct S s){return s.x;}} namespace B {struct S{long long x;}; "
                + "int get(struct S s){return (int)s.x;}} int main(){return 0;}"));
        var a = result.program().structs().get(0);
        var b = result.program().structs().get(1);
        assertNotEquals(a.name(), b.name());
        assertEquals("A::S", result.displayNames().get(a.name()));
        assertEquals("B::S", result.displayNames().get(b.name()));
        assertEquals(MiniType.struct(a.name()), result.program().typedefs().getFirst().type());
        assertEquals(MiniType.struct(a.name()), result.program().functions().get(0).parameters().getFirst().type());
        var semantic = analyze(result);
        assertEquals(4, semantic.semanticResult().structLayout(a.name()).orElseThrow().size());
        assertEquals(8, semantic.semanticResult().structLayout(b.name()).orElseThrow().size());
    }

    @Test void aggregateReturnAndLocalDeclarationsReachIr() {
        var result = success(parse("namespace A {struct S{int x;}; struct S make(int x){struct S s={x};return s;}} "
                + "int main(){return A::make(7).x;}"));
        var semantic = analyze(result);
        var ir = new IrLowerer(result.program(), semantic.semanticResult()).lower();
        assertTrue(ir.findFunction("main").isPresent());
        assertEquals(MiniType.struct(result.program().structs().getFirst().name()),
                result.program().functions().getFirst().returnType());
    }

    @Test void rootTagAndRootValueHaveIndependentCoreIdentities() {
        var result = success(parse("struct S{int x;}; int S=3; int main(){struct S value={4};return S+value.x;}"));
        assertEquals("S", result.program().globals().getFirst().name());
        assertNotEquals("S", result.program().structs().getFirst().name());
        analyze(result);
    }

    @Test void forwardSelfPointerAndNamespaceReopenShareOneTypeIdentity() {
        var result = success(parse("namespace A {struct Node; typedef struct Node *Link;} "
                + "namespace A {struct Node{int value; struct Node *next;};} int main(){return 0;}"));
        var forward = result.program().structs().get(0);
        var definition = result.program().structs().get(1);
        assertEquals(forward.name(), definition.name());
        assertEquals(MiniType.struct(definition.name()).pointerTo(), definition.fields().get(1).type());
        assertEquals(MiniType.struct(definition.name()).pointerTo(), result.program().typedefs().getFirst().type());
        analyze(result);
    }

    @Test void typesImportedByUsingRemainTypesAndSupportEquivalentAliases() {
        success(parse("namespace A {struct S{int x;}; typedef struct S Alias;} "
                + "namespace B {using A::S; using A::Alias;} using A::S; using B::Alias; int main(){return 0;}"));
        failure(parse("namespace A {typedef int T;} using A::T; int main(){return T;}"), "类型");
        failure(parse("namespace A {struct S{int x;};} namespace B {struct S{int y;};} "
                + "using A::S; using B::S; int main(){return 0;}"), "冲突");
        analyze(success(parse("namespace A {struct S{int x;};} using namespace A; "
                + "int main(){struct S value={2};return value.x;}")));
        analyze(success(parse("namespace A {struct S{int x;};} int main(){using A::S; "
                + "struct S value={2};return value.x;}")));
        var completed = success(parse("namespace A {struct S;} using A::S; struct S{int x;}; int main(){return 0;}"));
        assertEquals(completed.program().structs().get(0).name(), completed.program().structs().get(1).name());
        analyze(completed);
    }

    @Test void incompleteObjectsAndDuplicateDefinitionsHaveSourceDiagnostics() {
        failure(parse("namespace A {struct S; struct S value; struct S{int x;};} int main(){return 0;}"), "不完整");
        failure(parse("namespace A {struct S{struct S value;};} int main(){return 0;}"), "不完整");
        Program single = parse("namespace A {struct S{int x;};} int main(){return 0;}");
        NamespaceDecl namespace = (NamespaceDecl) single.declarations().getFirst();
        StructDecl first = (StructDecl) namespace.declarations().getFirst();
        StructDecl duplicate = new StructDecl(first.name(), first.fields(), true, false,
                new minic.SourceRange(2, 1, 2, 20));
        NamespaceDecl doubled = new NamespaceDecl(namespace.name(), List.of(first, duplicate), namespace.range());
        failure(program(List.of(doubled, single.declarations().getLast()), single.range()), "重复");
        success(parse("namespace A {struct S; struct S *value;} int main(){return 0;}"));
    }

    @Test void canonicalTypeReferencesNormalizeEveryCompositeTypeLayer() {
        Program parsed = parse("namespace A {struct S{int x;};} int main(){return 0;}");
        var range = parsed.range();
        MiniType canonical = MiniType.struct("::A::S");
        MiniType qualifiedPointer = MiniType.qualified(MiniType.qualified(canonical, Set.of(MiniType.TypeQualifier.CONST)).pointerTo(),
                Set.of(MiniType.TypeQualifier.CONST));
        MiniType callback = MiniType.function(canonical, List.of(canonical.pointerTo(), canonical.arrayOf(2))).pointerTo();
        var nodes = new ArrayList<Declaration>(parsed.declarations());
        nodes.add(new GlobalVarDecl("items", canonical.arrayOf(2), null, false, List.of(), range));
        nodes.add(new TypedefDecl("Callback", callback, range));
        nodes.add(new GlobalVarDecl("pointer", qualifiedPointer, null, false, List.of(AlignmentSpec.type(canonical, range)), range));
        var result = success(program(nodes, range));
        MiniType normalized = MiniType.struct(result.program().structs().getFirst().name());
        assertEquals(normalized.arrayOf(2), result.program().globals().get(0).type());
        assertEquals(MiniType.function(normalized, List.of(normalized.pointerTo(), normalized.arrayOf(2))).pointerTo(),
                result.program().typedefs().getFirst().type());
        MiniType pointer = result.program().globals().get(1).type();
        assertTrue(pointer.isConstQualified());
        assertTrue(pointer.pointee().isConstQualified());
        assertEquals(normalized, pointer.pointee().unqualified());
        assertEquals(normalized, result.program().globals().get(1).alignmentSpecs().getFirst().type());
    }

    @Test void canonicalTypesInFieldsParametersLocalsAndUnevaluatedExpressionsNormalize() {
        Program parsed = parse("namespace A {struct S{int x;};} int main(){return 0;}");
        var range = parsed.range();
        MiniType canonical = MiniType.struct("::A::S");
        var expressions = List.of(
                new SizeofExpr(null, canonical, range), new AlignofExpr(null, canonical, range),
                new CastExpr(canonical.pointerTo(), new IntegerLiteralExpr(0, "0", range), range),
                new VaArgExpr(new NameExpr("args", range), canonical.pointerTo(), range));
        var statements = new ArrayList<minic.compiler.parser.node.Statement>();
        statements.add(new VarDeclStmt("value", canonical, null, List.of(AlignmentSpec.type(canonical, range)), range));
        statements.add(new VarDeclStmt("args", MiniType.VA_LIST, null, range));
        statements.add(new TypedefStmt("Alias", canonical.pointerTo(), range));
        expressions.forEach(e -> statements.add(new ExprStmt(e, range)));
        statements.add(new ReturnStmt(new IntegerLiteralExpr(0, "0", range), range));
        FunctionDecl main = new FunctionDecl("main", MiniType.INT, List.of(), false, new BlockStmt(statements, range), false, range);
        FunctionDecl pass = new FunctionDecl("pass", canonical, List.of(new Parameter("item", canonical, range)), false, null, false, range);
        StructDecl wrapper = new StructDecl("Wrapper", List.of(new StructField("value", canonical, List.of(AlignmentSpec.type(canonical, range)), range)), range);
        var result = success(program(List.of(parsed.declarations().getFirst(), wrapper, pass, main), range));
        MiniType normalized = MiniType.struct(result.program().structs().getFirst().name());
        assertEquals(normalized, result.program().structs().get(1).fields().getFirst().type());
        assertEquals(normalized, result.program().functions().getFirst().returnType());
        assertEquals(normalized, result.program().functions().getFirst().parameters().getFirst().type());
        var coreMain = result.program().functions().getLast();
        assertEquals(normalized, ((VarDeclStmt) coreMain.body().statements().getFirst()).type());
        assertEquals(normalized.pointerTo(), ((TypedefStmt) coreMain.body().statements().get(2)).type());
        assertEquals(normalized, ((SizeofExpr) result.sourceToCore().get(expressions.get(0))).queriedType());
        assertEquals(normalized, ((AlignofExpr) result.sourceToCore().get(expressions.get(1))).queriedType());
        assertEquals(normalized.pointerTo(), ((CastExpr) result.sourceToCore().get(expressions.get(2))).targetType());
        assertEquals(normalized.pointerTo(), ((VaArgExpr) result.sourceToCore().get(expressions.get(3))).requestedType());
    }

    @Test void missingCanonicalTypesFailBeforeCoreAndUnionNamesRetainUnionLayoutMarker() {
        Program parsed = parse("int main(){return 0;}");
        var nodes = new ArrayList<Declaration>(parsed.declarations());
        nodes.add(new GlobalVarDecl("missing", MiniType.struct("::Missing::S").pointerTo(), null, false, List.of(), parsed.range()));
        failure(program(nodes, parsed.range()), "未声明");
        var result = success(parse("namespace A {union U{int x; long long y;}; union U item;} int main(){return 0;}"));
        assertTrue(result.program().structs().getFirst().name().startsWith("$union$"));
        assertEquals(8, analyze(result).semanticResult().structLayout(result.program().structs().getFirst().name()).orElseThrow().size());
    }

    private static SemanticAnalyzer analyze(CppNameBinder.Result result) {
        var semantic = new SemanticAnalyzer(result.program());
        semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        return semantic;
    }
    private static CppNameBinder.Result success(Program source) {
        var result = CppNameBinder.bind(source);
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        return result;
    }
    private static void failure(Program source, String message) {
        var result = CppNameBinder.bind(source);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains(message)), () -> result.diagnostics().toString());
    }
    private static Program program(List<Declaration> declarations, minic.SourceRange range) {
        return new Program(List.of(), List.of(), List.of(), List.of(), List.of(), declarations, range);
    }
    private static Program parse(String source) {
        var lexer = new Lexer(new SourceFile("type-binding.cpp", source), LanguageMode.CPP17_ALGORITHM);
        var parser = new Parser(lexer.lex().tokens(), LanguageMode.CPP17_ALGORITHM, false);
        parser.parse();
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        return parser.result().program();
    }
}
