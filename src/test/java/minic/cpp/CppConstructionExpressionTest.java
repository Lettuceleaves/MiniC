package minic.cpp;

import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
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
import java.util.List;
import java.util.stream.Stream;

import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** F06c: a class prvalue initializes its final storage rather than a copied intermediate. */
@Tag("cpp-differential") @Execution(ExecutionMode.SAME_THREAD) @Timeout(90)
final class CppConstructionExpressionTest {
    @TempDir Path temporary;
    static final String NODE = """
            #include <stdio.h>
            struct Node { Node *self; int value; int padding[3];
                Node(int n):self(this),value(n){} int read()const{return self==this?value:-1;}
            };
            """;
    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("parenthesized-and-list-prvalues", NODE+"int main(){Node a=Node(3);Node b=Node{4};printf(\"%d %d\\n\",a.read(),b.read());return 0;}","3 4\n"),
                Arguments.of("temporary-member-receiver", NODE+"int main(){printf(\"%d %d\\n\",Node(5).read(),Node{6}.read());return 0;}","5 6\n"),
                Arguments.of("const-reference-and-subobject-lifetime", NODE+"int main(){const Node&r=Node(7);const int&n=Node{8}.value;printf(\"%d %d\\n\",r.read(),n);return 0;}","7 8\n"),
                Arguments.of("direct-and-indirect-record-return", NODE+"Node make(int n){return Node(n);}int main(){Node(*f)(int)=make;Node a=make(9);const Node&b=f(10);printf(\"%d %d\\n\",a.value,b.value);return 0;}","9 10\n"),
                Arguments.of("record-method-return-and-nested-member", NODE+"struct Factory{Node make(int n){return Node{n};}};struct Outer{Node item;Outer():item(Factory{}.make(11)) {}};int main(){Outer o;printf(\"%d\\n\",o.item.read());return 0;}","11\n"),
                Arguments.of("value-and-reference-parameters", NODE+"int take(Node n){return n.value;}int view(const Node&n){return n.read();}int main(){printf(\"%d %d\\n\",take(Node(12)),view(Node{13}));return 0;}","12 13\n"),
                Arguments.of("conditional-and-comma-result-paths", NODE+"int count;int tick(){return ++count;}Node make(int n){return Node(n);}int main(){Node a=(tick(),1?make(14):Node(20));Node b=0?Node(30):Node{15};printf(\"%d %d %d\\n\",a.read(),b.read(),count);return 0;}","14 15 1\n"),
                Arguments.of("qualified-alias-and-value-shadow", """
                        #include <stdio.h>
                        namespace N{struct Box{int n;Box(int v):n(v){}};typedef Box Alias;}
                        int plus(int n){return n+1;}
                        int main(){typedef int(*F)(int);F Alias=plus;N::Alias a=N::Alias{2};
                            printf("%d %d\\n",a.n,Alias(4));return 0;}
                        """, "2 5\n"),
                Arguments.of("scalar-and-pointer-functional-conversions", """
                        #include <stdio.h>
                        int main(){typedef int*P;int value=1;P p=&value;
                            printf("%d %d %d %d\\n",int(3.9),int{},bool(p),P(0)==0);return 0;}
                        ""","3 0 1 1\n"),
                Arguments.of("aggregate-braced-prvalue-and-value-init", """
                        #include <stdio.h>
                        struct Pair{int x;int y;};int read(Pair p){return p.x+p.y;}
                        int main(){Pair p=Pair();printf("%d %d %d\\n",p.x,p.y,read(Pair{3,4}));return 0;}
                        ""","0 0 7\n"),
                Arguments.of("unevaluated-construction-does-not-call", """
                        #include <stdio.h>
                        int calls;struct Box{int value;Box(int n):value(n){++calls;}};
                        int main(){printf("%d %d %d\\n",int(sizeof(Box(3))),int(sizeof(Box{4})),calls);return 0;}
                        ""","4 4 0\n"),
                Arguments.of("standalone-expressions-and-declaration-ambiguity", """
                        #include <stdio.h>
                        int calls;struct Box{Box(){++calls;}Box(int n){calls+=n;}};
                        int main(){Box(2);Box{};Box();Box(named);printf("%d\\n",calls);return 0;}
                        ""","5\n"));
    }
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void allBackendsAgree(String name,String source,String expected)throws Exception{agree(temporary,name,source,expected);}

    @Test void allPositiveSourcesAreIndependentlyAcceptedByCpp17()throws Exception{
        for(var arguments:programs().toList()){
            Object[] values=arguments.get();Path source=temporary.resolve(values[0]+".cpp");Files.writeString(source,(String)values[1]);
            var result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",source.toString()),temporary,"",Duration.ofSeconds(20),65536);
            assertFalse(result.timedOut(),result::stderr);assertEquals(0,result.exitCode(),()->values[0]+"\n"+result.stderr());
        }
    }
    static Stream<Arguments> invalidPrograms(){return Stream.of(
            Arguments.of("list-narrowing","struct Box{Box(int n){}};int main(){Box{3.8}; // bad\nreturn 0;}"),
            Arguments.of("private-constructor","class Box{Box(int n){}};int main(){Box(1); // bad\nreturn 0;}"),
            Arguments.of("no-viable-constructor","struct Box{Box(int n){}};int main(){Box(); // bad\nreturn 0;}"),
            Arguments.of("nonconst-reference","struct Box{Box(int n){}};int main(){Box&r=Box(1); // bad\nreturn 0;}"),
            Arguments.of("scalar-multiple-arguments","int main(){int x=int(1,2); // bad\nreturn 0;}"));}
    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void invalidFormsHaveSourceDiagnostics(String name,String source)throws Exception{reject(temporary,name,source);}

    @Test void bracedSyntaxCannotTurnAValueIntoAType(){
        var api=compiler("int main(){int Box=0;Box{1};return 0;}");var parser=stage(api,Parser.class);api.runThrough(parser);
        assertFalse(parser.succeeded());assertTrue(parser.errors().stream().anyMatch(error->error.range().startLine()==1));
    }

    @Test void abstractFunctionPointerTypeOperandsRemainTypes()throws Exception{
        agree(temporary,"abstract-type-operands","""
                #include <stdio.h>
                int f(int x){return x+1;}
                int main(){int(*p)(int)=(int(*)(int))f;
                    printf("%d %d\\n",p(2),sizeof(int(*)(int))==sizeof(p));return 0;}
                ""","3 1\n");
    }

    @Test void sizeofAmbiguityStillSelectsAFunctionType()throws Exception{
        reject(temporary,"sizeof-function-type","struct Box{};int main(){return sizeof(Box()); // bad\n}");
    }

    @Test void miniCConstructsDirectlyInParameterAndReferenceResultStorage()throws Exception{
        // N4659 [class.temporary]/3 permits extra ABI copies for these trivial special members.
        // Keep MiniC's stronger direct-destination guarantee separate from portable G++ output.
        String source=NODE+"Node make(int n){return Node(n);}int take(Node n){return n.read();}int main(){Node(*f)(int)=make;const Node&b=f(10);printf(\"%d %d\\n\",take(Node(12)),b.read());return 0;}";
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),minic.compiler.LanguageMode.CPP17_ALGORITHM).run("minic-destinations",source,"");
        for(var backend:List.of(CppDifferentialHarness.Backend.MINIC_NATIVE,CppDifferentialHarness.Backend.MINIC_DEBUG)){
            var outcome=report.outcomes().get(backend);assertEquals(CppDifferentialHarness.Status.OK,outcome.status(),outcome::diagnostics);
            assertEquals("12 10\n",outcome.stdout().replace("\r\n","\n"));
        }
    }

    @Test void functionalCopyConstructionCreatesAPrvalueRatherThanAliasingItsOperand()throws Exception{
        agree(temporary,"functional-copy-prvalue","""
                #include <stdio.h>
                struct Box{int value;Box(int n):value(n){}};struct Pair{int value;};
                int main(){Box box(3);const Box&r=Box(box);Pair pair={4};
                    const Pair&s=Pair(pair);const Pair&t=Pair{pair};box.value=8;pair.value=9;
                    printf("%d %d %d %d %d\\n",r.value,s.value,t.value,&r!=&box,&s!=&pair);return 0;}
                ""","3 4 4 1 1\n");
    }

    @Test void incompleteFunctionalConstructionHasADiagnosticRatherThanCrashing()throws Exception{
        reject(temporary,"incomplete-construction","struct Box;int main(){Box(1); // bad\nreturn 0;}");
    }

    @Test void bracedVoidIsNotAnObjectType()throws Exception{
        reject(temporary,"void-list","int main(){void{}; // bad\nreturn 0;}");
    }

    @Test void sizeofNestedConstructionIsAnExpressionRatherThanAFunctionParameterList()throws Exception{
        agree(temporary,"nested-unevaluated-construction","""
                #include <stdio.h>
                int calls;struct Box{int value;Box(int n):value(n){++calls;}};
                int main(){printf("%d %d %d\\n",int(sizeof(Box(int{3}))),int(sizeof(Box(int(4)))),calls);return 0;}
                ""","4 4 0\n");
    }

    @Test void temporaryConstructionKeepsSourceFramesAndUsesNoHeap(){
        assertDebugHistory(NODE+"int main(){const Node&n=Node{4};printf(\"%d\\n\",n.read());return 0;}",
                "4\n","Node::Node","n");
    }

    @Test void standaloneSourceConstructionCannotBypassCoreGuards(){
        var range=new minic.SourceRange(2,0,2,6);
        var initializer=new minic.compiler.parser.node.CppInitializer(minic.compiler.parser.node.CppInitializer.Kind.DIRECT_PAREN,List.of(),range);
        var source=new minic.compiler.parser.node.CppConstructionExpr(minic.compiler.type.MiniType.INT,initializer,range,range);
        assertEquals(List.of(initializer),minic.compiler.parser.node.AstChildren.of(source));
        assertSame(source,minic.compiler.parser.node.AstChildren.firstCppSyntax(source));
        var main=new minic.compiler.parser.node.Declaration.FunctionDecl("main",minic.compiler.type.MiniType.INT,List.of(),false,
                new minic.compiler.parser.node.Statement.BlockStmt(List.of(new minic.compiler.parser.node.Statement.ReturnStmt(source,range)),range),false,range);
        var program=new minic.compiler.parser.node.Declaration.Program(List.of(),List.of(main),range);
        var semantic=new SemanticAnalyzer(program);semantic.analyze();
        assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP002")&&error.range().equals(range)));
        assertThrows(IllegalArgumentException.class,()->new minic.compiler.ir.IrLowerer().lower(program));
        assertThrows(UnsupportedOperationException.class,()->initializer.arguments().add(new IntegerLiteralExpr(1,"1",range)));
    }

    @Test void sourceConstructionAndRecordCallKeepDistinctMappings(){
        String source=NODE+"Node make(int n){return Node(n);}int main(){Node a=make(3);return a.read();}";
        var api=compiler(source);var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var construction=nodes(parser.result().program()).stream().filter(node->node.getClass().getSimpleName().equals("CppConstructionExpr")).findFirst().orElseThrow();
        var call=nodes(parser.result().program()).stream().filter(CallExpr.class::isInstance).map(CallExpr.class::cast).filter(node->node.callee() instanceof NameExpr name&&name.name().equals("make")).findFirst().orElseThrow();
        var map=semantic.semanticResult().sourceToCore();
        assertInstanceOf(ObjectInitExpr.class,map.get(construction));assertInstanceOf(ObjectInitExpr.class,map.get(call));
        assertNotSame(map.get(construction),map.get(call));assertEquals(construction.range(),map.get(construction).range());assertEquals(call.range(),map.get(call).range());
        assertTrue(nodes(semantic.semanticResult().program()).stream().noneMatch(node->node.getClass().getSimpleName().equals("CppConstructionExpr")));
    }
}
