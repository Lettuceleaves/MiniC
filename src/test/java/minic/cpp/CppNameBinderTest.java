package minic.cpp;

import minic.compiler.SourceFile;
import minic.compiler.LanguageMode;
import minic.compiler.CompilerApi;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.cpp.CppNameBinder;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CppNameBinderTest {
    @Test void flattensNestedReopenedNamespacesAndPreservesNodeOrigins() {
        Program source = parse("namespace A { int x = 3; namespace B { int f(int x) { return x + A::x; } } } "
                + "namespace A { int g() { return B::f(x); } } int main() { return A::g(); }");
        var bound = success(source);
        assertEquals(1, bound.program().globals().size());
        assertEquals(3, bound.program().functions().size());
        assertTrue(bound.program().declarations().stream().noneMatch(NamespaceDecl.class::isInstance));
        assertTrue(nodes(bound.program()).stream().noneMatch(QualifiedNameExpr.class::isInstance));
        assertTrue(bound.displayNames().containsValue("A::B::f"));
        assertTrue(bound.displayNames().containsValue("A::g"));
        for (AstNode node : nodes(source)) {
            if (node instanceof NameExpr || node instanceof QualifiedNameExpr) {
                assertInstanceOf(NameExpr.class, bound.sourceToCore().get(node));
                assertEquals(node.range(), bound.sourceToCore().get(node).range());
            }
        }
        assertEquals(source.range(), bound.program().range());
        assertSame(bound.program(), bound.sourceToCore().get(source));
        assertInstanceOf(NamespaceDecl.class, source.declarations().getFirst());
    }

    @Test void rootQualifiedReferenceCannotBeCapturedByALocal() {
        var result = success("int x=1; int main(){int x=2; {int x=3; x=x+1;} return ::x+x;}");
        var body = result.program().functions().getFirst().body();
        var local = (VarDeclStmt) body.statements().getFirst();
        var sum = (BinaryExpr) ((ReturnStmt) body.statements().getLast()).expression();
        assertEquals(result.program().globals().getFirst().name(), ((NameExpr) sum.left()).name());
        assertEquals(local.name(), ((NameExpr) sum.right()).name());
        assertNotEquals(local.name(), result.program().globals().getFirst().name());
        var inner = (VarDeclStmt) ((BlockStmt) body.statements().get(1)).statements().getFirst();
        assertNotEquals(local.name(), inner.name());
    }

    @Test void pointOfDeclarationBindsTheInitializerToTheNewLocal() {
        var result = success("int x=4; int main(){int x=sizeof(x); return x;}");
        var local = (VarDeclStmt) result.program().functions().getFirst().body().statements().getFirst();
        var size = (SizeofExpr) local.initializer();
        assertEquals(local.name(), ((NameExpr) ((GroupingExpr) size.expression()).expression()).name());
    }

    @Test void preservesRootAbiNamesAndAvoidsGeneratedNameCollisions() {
        var result = success("extern int printf(const char *format, ...); int minicCppSymbol1=1; "
                + "namespace A { int f(int x){return x;} } int main(){int printf=2; return ::printf(\"%d\", A::f(printf));}");
        assertEquals("printf", result.program().functions().getFirst().name());
        assertEquals("main", result.program().functions().getLast().name());
        var functions = result.program().functions();
        assertTrue(functions.stream().allMatch(f -> f.name().matches("[A-Za-z][A-Za-z0-9_]*")));
        assertEquals(functions.size(), functions.stream().map(FunctionDecl::name).distinct().count());
    }

    @Test void prototypeAndDefinitionShareEntityAndRecursionResolves() {
        var result = success("namespace A {int f(int x); int f(int x){if(x) return f(x-1); return 0;}} "
                + "int main(){return A::f(2);}");
        assertEquals(result.program().functions().get(0).name(), result.program().functions().get(1).name());
        failure("namespace A {int f(){return later();} int later(){return 1;}} int main(){return A::f();}", "未声明");
        failure("namespace A {int f(){return x;} int x=1;} int main(){return A::f();}", "未声明");
    }

    @Test void usingDeclarationFreezesTheEntityAndRespectsBlockLifetime() {
        var result = success("namespace A {int x=1;} namespace B {using A::x; int f(){return x;}} "
                + "int main(){using B::x; return x;}");
        var returned = (NameExpr) ((ReturnStmt) result.program().functions().getLast().body().statements().getLast()).expression();
        assertEquals(result.program().globals().getFirst().name(), returned.name());
        assertTrue(nodes(result.program()).stream().noneMatch(UsingDecl.class::isInstance));
        failure("namespace A {int x;} int main(){{using A::x;} return x;}", "未声明");
        failure("namespace A {} using A::x; namespace A {int x;} int main(){return 0;}", "未声明");
    }

    @Test void usingDirectivesSeeLaterReopeningsAndDeduplicateCycles() {
        success("namespace A {} using namespace A; namespace A {int x=1;} int main(){return x;}");
        success("namespace A {int x=1;} namespace B {using namespace A;} namespace A {using namespace B;} "
                + "using namespace A; using namespace B; int main(){return x;}");
        success("namespace A {int x=1;} namespace B {using A::x;} using namespace A; using namespace B; int main(){return x;}");
    }

    @Test void namespaceDirectivesUseCommonAncestorLookupAndDiagnoseAmbiguity() {
        failure("namespace A {int x;} using namespace A; int x; int main(){return x;}", "二义");
        success("namespace A {int x;} namespace B {int x; using namespace A; int f(){return x;}} int main(){return B::f();}");
        failure("namespace A {int x; namespace B {int x;} using namespace B; int f(){return x;}} int main(){return A::f();}", "二义");
        success("namespace A {int x;} using namespace A; int x; int main(){int x=4;return x;}");
    }

    @Test void localValueDoesNotHideNamespaceQualifierAndGlobalQualifierStaysAtRoot() {
        success("namespace A {int x;} int main(){int A=3; return A::x;}");
        failure("namespace A {int x; int f(){return ::x;}} int main(){return A::f();}", "未声明");
        failure("int main(){return Missing::x;}", "命名空间");
    }

    @Test void scopeOfForAndConditionalBodiesDoesNotLeak() {
        success("int main(){for(int i=0;i<2;i=i+1){int j=i;} return 0;}");
        failure("int main(){for(int i=0;i<2;i=i+1){} return i;}", "未声明");
        failure("namespace A {int x;} int main(){if(1) {using A::x;} return x;}", "未声明");
        failure("int main(){do {int x=0;} while(x); return 0;}", "未声明");
        failure("int main(){for(int i=0;i<2;i=i+1){int i=3;} return 0;}", "重复");
        success("int main(){for(int i=0;i<2;i=i+1){{int i=3;}} return 0;}");
    }

    @Test void switchCasesShareOneLexicalScope() {
        success("namespace A {int x=2;} int main(){switch(0){case 0: using A::x; break; case 1: return x;} return 0;}");
        failure("int main(){switch(0){case 0: int x=2; break; case 1: return x;} return 0;}", "跳过");
        success("int main(){switch(0){case 0: {int x=2;} break; case 1: return 3;} return 0;}");
    }

    @Test void traversesNestedExpressionsWithoutRenamingFieldsOrDesignators() {
        var result = success("struct S {int field;}; namespace N {int x=1; int f(int x){return x;}} "
                + "int main(){struct S s={.field=N::x}; int a[2]={N::x,0}; int y=(int)N::x; "
                + "s.field=N::x; a[N::x]=y; y=sizeof(N::x)+alignof(N::x); "
                + "y=(N::x, N::x ? N::f(N::x) : -N::x); while(N::x){break;} return s.field;}");
        assertTrue(nodes(result.program()).stream().noneMatch(QualifiedNameExpr.class::isInstance));
        for (AstNode node : nodes(result.program())) {
            if (node instanceof FieldAccessExpr field) assertEquals("field", field.fieldName());
            if (node instanceof DesignatedInitExpr designated) {
                assertEquals("field", ((Designator.Field) designated.designators().getFirst()).name());
            }
        }
    }

    @Test void supportsOverloadsAndRejectsUnsupportedEnumsDuplicateDefinitionsAndDynamicInitializersExplicitly() {
        success("namespace A {struct S {int x;};} int main(){return 0;}");
        success("namespace A {typedef int T;} int main(){return 0;}");
        failure("namespace A {enum E {X};} int main(){return 0;}", "类型");
        var overloads = success("namespace A {int f(int x); int f(double x);} int main(){return 0;}");
        assertNotEquals(overloads.program().functions().get(0).name(), overloads.program().functions().get(1).name());
        failure("namespace A {int x; int x;} int main(){return 0;}", "重复");
        failure("namespace A {int x=2;} int y=A::x; int main(){return y;}", "初始化");
        failure("namespace A {int f(){return 1;}} int y=A::f(); int main(){return y;}", "初始化");
        failure("namespace A {extern int printf(char *s, ...);} int main(){return 0;}", "外部");
        success("namespace A {int x;} int main(){typedef int A; return A::x;}");
        success("typedef int T; using ::T; int main(){return 0;}");
        Program source = parse("namespace A {} int main(){return 0;}");
        var declarations = new ArrayList<>(source.declarations());
        declarations.add(1, new StructDecl("A", List.of(new StructField("x", MiniType.INT, source.range())), source.range()));
        failure(new Program(source.structs(), source.enums(), source.typedefs(), source.globals(), source.functions(),
                declarations, source.range()), "冲突");
    }

    @Test void keepsGlobalCTypesAndBindsEnumValuesWithoutChangingFields() {
        var result = success("typedef int Number; struct S{int x;}; enum E{one=1}; "
                + "int main(){struct S s={one}; Number x=one; return s.x+x;}");
        assertEquals("S", result.displayNames().get(result.program().structs().getFirst().name()));
        assertEquals("x", result.program().structs().getFirst().fields().getFirst().name());
        assertEquals(1, result.program().typedefs().size());
        assertEquals(1, result.program().enums().size());
        var shadowed = success("enum E{value=1}; int main(){int value=3; return value;}");
        var body = shadowed.program().functions().getFirst().body();
        assertEquals(((VarDeclStmt) body.statements().getFirst()).name(),
                ((NameExpr) ((ReturnStmt) body.statements().getLast()).expression()).name());
    }

    @Test void inlineAnonymousAggregatesRemainInOrderedDeclarationsAndReachIr() {
        for (String source : List.of(
                "typedef struct {int x;} S; int main(){S s={7};return s.x;}",
                "struct {int x;} g={9}; int main(){return g.x;}")) {
            Program parsed = parse(source);
            assertEquals(1, parsed.structs().size());
            var bound = success(parsed);
            assertEquals(parsed.structs().size(), bound.program().structs().size());
            assertInstanceOf(StructDecl.class, parsed.declarations().getFirst());
            var api = new CompilerApi(new SourceFile("anonymous.cpp", source), LanguageMode.CPP17_ALGORITHM);
            assertNotNull(api.runToIr().findFunction("main").orElseThrow());
        }
    }

    @Test void rewritesEveryLessCommonExpressionContainer() {
        Program original = parse("namespace A {struct S{int x;}; int x=1; S *pointer;} "
                + "int main(){A::pointer->x; return A::x;}");
        FunctionDecl main = original.functions().getFirst();
        var field = (FieldAccessExpr) ((ExprStmt) main.body().statements().getFirst()).expression();
        var qualified = ((ReturnStmt) main.body().statements().getLast()).expression();
        var range = qualified.range();
        var one = new IntegerLiteralExpr(1, "1", range);
        var expressions = List.of(
                new PostfixUpdateExpr(qualified, TokenType.PLUS_PLUS, range),
                new VaStartExpr(qualified, qualified, range),
                new VaArgExpr(qualified, MiniType.INT, range),
                new VaCopyExpr(qualified, qualified, range),
                new VaEndExpr(qualified, range),
                new SizeofExpr(null, MiniType.INT, range),
                new AlignofExpr(null, MiniType.INT, range),
                new SizeofExpr(qualified, null, range),
                new AlignofExpr(qualified, null, range),
                new AssignmentExpr(qualified, TokenType.PLUS_EQUAL, qualified, range),
                new ConditionalExpr(qualified, qualified, qualified, range),
                new CastExpr(MiniType.INT, qualified, range),
                new IndexExpr(qualified, qualified, range),
                field,
                new GroupingExpr(qualified, range),
                new UnaryExpr(TokenType.MINUS, qualified, range),
                new CommaExpr(List.of(qualified, qualified), range),
                new CallExpr(qualified, List.of(qualified), range),
                new AggregateInitExpr(List.of(qualified), range),
                new DesignatedInitExpr(List.of(new Designator.Index(1, range)), qualified, range));
        var statements = new ArrayList<minic.compiler.parser.node.Statement>();
        expressions.forEach(e -> statements.add(new ExprStmt(e, range)));
        statements.add(new ReturnStmt(one, range));
        var replacement = new FunctionDecl(main.name(), main.returnType(), main.parameters(), false,
                new BlockStmt(statements, main.body().range()), false, main.range());
        var program = new Program(original.structs(), original.enums(), original.typedefs(), original.globals(),
                List.of(replacement), List.of(original.declarations().getFirst(), replacement), original.range());
        var bound = success(program);
        assertFalse(nodes(bound.program()).stream().anyMatch(QualifiedNameExpr.class::isInstance));
        for (var expression : expressions) assertEquals(expression.range(), bound.sourceToCore().get(expression).range());
        assertInstanceOf(QualifiedNameExpr.class, field.target());
        assertInstanceOf(NameExpr.class, bound.sourceToCore().get(field.target()));
        assertEquals(field.target().range(), bound.sourceToCore().get(field.target()).range());
    }

    private static CppNameBinder.Result success(String source) { return success(parse(source)); }
    private static CppNameBinder.Result success(Program source) {
        var result = CppNameBinder.bind(source);
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        return result;
    }
    private static void failure(String source, String text) {
        failure(parse(source), text);
    }
    private static void failure(Program source, String text) {
        var result = CppNameBinder.bind(source);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains(text)), () -> result.diagnostics().toString());
    }
    private static Program parse(String text) {
        var lexer = new Lexer(new SourceFile("binding.cpp", text), LanguageMode.CPP17_ALGORITHM);
        var parser = new Parser(lexer.lex().tokens(), LanguageMode.CPP17_ALGORITHM, false);
        parser.parse();
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        return parser.result().program();
    }
    private static List<AstNode> nodes(AstNode root) {
        var result = new ArrayList<AstNode>();
        result.add(root);
        for (int i=0; i<result.size(); i++) result.addAll(AstChildren.of(result.get(i)));
        return result;
    }
}
