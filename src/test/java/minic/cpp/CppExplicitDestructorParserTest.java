package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit calls retain the destructor type; they are never an ordinary field named ~T. */
final class CppExplicitDestructorParserTest {
    @TempDir Path temporary;
    static Stream<Arguments> forms() { return Stream.of(
            Arguments.of("object", "struct A{~A(){}};void f(A& a){a.~A();}", "a.~A()"),
            Arguments.of("pointer", "struct A{~A(){}};void f(A* p){p->~A();}", "p->~A()"),
            Arguments.of("grouped", "struct A{};void f(A* p){(*p).~A();}", "(*p).~A()"),
            Arguments.of("alias", "namespace N{struct A{};}typedef N::A Alias;void f(Alias* p){p->~Alias();}", "p->~Alias()"),
            Arguments.of("injected-name", "namespace N{struct A{};}void f(N::A* p){p->~A();}", "p->~A()"),
            Arguments.of("injected-name-hides-context", "struct A{};namespace N{struct A{};}void f(N::A* p){p->~A();}", "p->~A()"),
            Arguments.of("side-effect-receiver", "struct A{};A* next();void f(){next()->~A();}", "next()->~A()"),
            Arguments.of("const-receiver", "struct A{};void f(const A* p){p->~A();}", "p->~A()")); }
    @ParameterizedTest(name="{0}") @MethodSource("forms")
    void preservesReceiverAndCompleteSourceRange(String name,String source,String spelling) {
        var api=compiler(source);var parser=stage(api,Parser.class);api.runThrough(parser);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        AstNode call=findCall(parser.result().program());assertNotNull(call);
        assertEquals(spelling,source.substring(call.range().startByte(),call.range().endByte()));
        assertSame(call,AstChildren.firstCppSyntax(call));
        assertEquals(1,AstChildren.of(call).size());
        assertInstanceOf(Expression.class,AstChildren.of(call).getFirst());
    }
    @Test void positiveFormsAreIndependentlyValidCpp17()throws Exception {
        for(var args:forms().toList()) {
            Object[] values=args.get();Path source=temporary.resolve(values[0]+".cpp");Files.writeString(source,(String)values[1]);
            var result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",source.toString()),temporary,"",Duration.ofSeconds(20),65536);
            assertFalse(result.timedOut());assertEquals(0,result.exitCode(),result::stderr);
        }
    }
    @ParameterizedTest @ValueSource(strings={"p->~A(1)","p->~A","p->~()","p->~A(","p->~N::A()","p->~A{}"})
    void malformedCallHasSourceDiagnostic(String expression) {
        var api=compiler("struct A{};void f(A*p){"+expression+";}");var parser=stage(api,Parser.class);api.runThrough(parser);
        assertFalse(parser.succeeded());assertFalse(parser.errors().isEmpty());
    }
    @Test void qualifiedDestructorPrefixHasAnExplicitBoundary()throws Exception {
        String source="namespace N{struct A{};}void f(N::A*p){p->N::A::~A();}";
        Path cpp=temporary.resolve("qualified.cpp");Files.writeString(cpp,source);
        var result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",cpp.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertEquals(0,result.exitCode(),result::stderr);
        var api=compiler(source);var parser=stage(api,Parser.class);api.runThrough(parser);
        assertFalse(parser.succeeded());assertTrue(parser.errors().stream().anyMatch(error->error.code().equals("CPP001") && error.message().contains("限定类型前缀")),()->parser.errors().toString());
    }
    @Test void scalarPseudoDestructorRemainsSourceOnlyRatherThanAnOrdinaryCall() {
        var api=compiler("typedef int I;void f(I*p){p->~I();}");var parser=stage(api,Parser.class);api.runThrough(parser);
        assertTrue(parser.succeeded(),()->parser.errors().toString());assertNotNull(findCall(parser.result().program()));
        var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertFalse(semantic.succeeded());assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP005")));
    }
    @Test void ownerCandidateAndInjectedNameRemainSeparate() {
        var api=compiler("namespace N{struct A{};}typedef N::A Alias;void f(N::A*p){p->~A();p->~Alias();}");
        var parser=stage(api,Parser.class);api.runThrough(parser);assertTrue(parser.succeeded(),()->parser.errors().toString());
        var body=parser.result().program().functions().getFirst().body();
        var first=(CppDestructorCallExpr)((minic.compiler.parser.node.Statement.ExprStmt)body.statements().get(0)).expression();
        var second=(CppDestructorCallExpr)((minic.compiler.parser.node.Statement.ExprStmt)body.statements().get(1)).expression();
        assertNull(first.ownerType());assertEquals(List.of("A"),first.destructorName().segments());
        assertEquals(minic.compiler.type.MiniType.struct("::N::A"),second.ownerType());
        assertEquals(List.of("Alias"),second.destructorName().segments());assertTrue(second.viaPointer());
    }
    @Test void standaloneSourceCallCannotBypassCoreGuards() {
        var range=new minic.SourceRange(2,0,2,7);var nameRange=new minic.SourceRange(2,3,2,5);
        var receiver=new Expression.NameExpr("p",range);
        var call=new CppDestructorCallExpr(receiver,null,new QualifiedName(false,List.of("A"),nameRange),true,nameRange,range);
        assertEquals(List.of(receiver),AstChildren.of(call));assertSame(call,AstChildren.firstCppSyntax(call));
        var main=new minic.compiler.parser.node.Declaration.FunctionDecl("main",minic.compiler.type.MiniType.INT,List.of(),false,
                new minic.compiler.parser.node.Statement.BlockStmt(List.of(new minic.compiler.parser.node.Statement.ExprStmt(call,range)),range),false,range);
        var program=new minic.compiler.parser.node.Declaration.Program(List.of(),List.of(main),range);
        var semantic=new SemanticAnalyzer(program);semantic.analyze();
        assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP002")&&error.range().equals(range)));
        assertThrows(IllegalArgumentException.class,()->new minic.compiler.ir.IrLowerer().lower(program));
        assertThrows(UnsupportedOperationException.class,()->call.destructorName().segments().add("B"));
        assertThrows(NullPointerException.class,()->new CppDestructorCallExpr(null,null,call.destructorName(),true,nameRange,range));
    }
    @Test void cModeStillRejectsDestructorSyntax() {
        var api=new minic.compiler.CompilerApi(new minic.compiler.SourceFile("control.c","struct A{int n;};void f(struct A*p){p->~A();}"),LanguageMode.C);
        var parser=stage(api,Parser.class);api.runThrough(parser);assertFalse(parser.succeeded());
    }
    static AstNode findCall(AstNode root) {
        var pending=new ArrayDeque<AstNode>();pending.add(root);
        while(!pending.isEmpty()) {var node=pending.removeFirst();if(node.getClass().getSimpleName().equals("CppDestructorCallExpr"))return node;pending.addAll(AstChildren.of(node));}
        return null;
    }
}

