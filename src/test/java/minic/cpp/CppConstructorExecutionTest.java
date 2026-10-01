package minic.cpp;

import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.ConstructorMember;
import minic.compiler.parser.node.Declaration.FunctionDecl;
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

/** F06b: ordinary constructors initialize their final storage, in field declaration order. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppConstructorExecutionTest {
    @TempDir Path temporary;

    private static final String ADDRESS_SOURCE = """
            #include <stdio.h>
            namespace Model {
                struct Node {
                    Node *self;
                    int value;
                    Node(int amount) : self(this), value(amount) { value += 1; }
                    int read() const { return value; }
                };
            }
            int main() {
                Model::Node node(6);
                printf("%d %d\\n", node.self == &node, node.read());
                return 0;
            }
            """;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("five-variable-initialization-forms", """
                        #include <stdio.h>
                        struct Box { int value; Box():value(1){} Box(int n):value(n){} };
                        int main() {
                            Box a; Box b(2); Box c{3}; Box d=4; Box e={5};
                            printf("%d %d %d %d %d\\n",a.value,b.value,c.value,d.value,e.value);
                            return 0;
                        }
                        """, "1 2 3 4 5\n"),
                Arguments.of("members-run-in-declaration-order-before-body", """
                        #include <stdio.h>
                        int trace=0;
                        int mark(int digit){trace=trace*10+digit;return digit;}
                        struct Pair {
                            int first; int second;
                            Pair():second(mark(2)),first(mark(1)){mark(3);}
                        };
                        int main(){Pair p; printf("%d %d %d\\n",trace,p.first,p.second);return 0;}
                        """, "123 1 2\n"),
                Arguments.of("explicit-member-initialization-suppresses-default", """
                        #include <stdio.h>
                        int calls=0; int next(){return ++calls;}
                        struct Box { int first=next(); int second=next(); Box():second(9){} };
                        int main(){Box a;Box b;printf("%d %d %d %d %d\\n",a.first,a.second,b.first,b.second,calls);return 0;}
                        """, "1 9 2 9 2\n"),
                Arguments.of("argument-once-before-member-initializers", """
                        #include <stdio.h>
                        int trace=0; int mark(int n){trace=trace*10+n;return n;}
                        struct Box {int value; Box(int n):value(mark(2)+n){mark(3);}};
                        int main(){Box value(mark(1));printf("%d %d\\n",trace,value.value);return 0;}
                        """, "123 3\n"),
                Arguments.of("const-and-reference-members-initialize-once", """
                        #include <stdio.h>
                        struct View {
                            const int tag; int &value;
                            View(int &target,int n):value(target),tag(n){value+=tag;}
                        };
                        int main(){int x=2;View view(x,3);view.value=8;
                            printf("%d %d %d\\n",x,view.tag,&view.value==&x);return 0;}
                        """, "8 3 1\n"),
                Arguments.of("qualified-out-of-line-and-distinct-record-identities", """
                        #include <stdio.h>
                        namespace A {struct Box {int value;Box(int n);};}
                        namespace B {struct Box {int value;Box(int n):value(n+10){}};}
                        A::Box::Box(int n):value(n+1){}
                        int main(){A::Box a(2);B::Box b(2);printf("%d %d\\n",a.value,b.value);return 0;}
                        """, "3 12\n"),
                Arguments.of("named-destination-has-stable-this-address", ADDRESS_SOURCE, "1 7\n"),
                Arguments.of("nested-member-constructs-at-final-address", """
                        #include <stdio.h>
                        struct Item {Item*self;int value;Item(int n):self(this),value(n){}};
                        struct Owner {int prefix;Item item;Owner(int n):item(n),prefix(4){}};
                        int main(){Owner owner(7);printf("%d %d %d\\n",owner.item.self==&owner.item,owner.item.value,owner.prefix);return 0;}
                        """, "1 7 4\n"),
                Arguments.of("implicit-default-construction-runs-default-members", """
                        #include <stdio.h>
                        int calls=0;int next(){return ++calls;}
                        struct Leaf {int value;Leaf():value(next()){}};
                        struct Owner {int first=next();Leaf leaf;int last=next();};
                        int main(){Owner owner;printf("%d %d %d %d\\n",owner.first,owner.leaf.value,owner.last,calls);return 0;}
                        """, "1 2 3 3\n"),
                Arguments.of("copy-and-direct-conversion-use-same-nonexplicit-constructor", """
                        #include <stdio.h>
                        struct Int {int value;Int(int n):value(n){}};
                        int main(){Int direct(3.8);Int copy=3.8;
                            printf("%d %d\\n",direct.value,copy.value);return 0;}
                        """, "3 3\n"),
                Arguments.of("constructor-overload-exact-and-promotion", """
                        #include <stdio.h>
                        struct Box {int kind;Box(int n):kind(1){} Box(double n):kind(2){}};
                        int main(){char ch=3;Box a(ch);Box b(3.0);printf("%d %d\\n",a.kind,b.kind);return 0;}
                        """, "1 2\n"),
                Arguments.of("loop-reentry-constructs-once-per-iteration", """
                        #include <stdio.h>
                        int calls=0;
                        struct Box {int value;Box(int n):value(n){++calls;}};
                        int main(){int sum=0;for(int i=0;i<3;++i){Box box(i);sum+=box.value;}
                            printf("%d %d\\n",sum,calls);return 0;}
                        """, "3 3\n"),
                Arguments.of("constructor-body-sees-later-method-and-private-members", """
                        #include <stdio.h>
                        class Box {
                            int value;
                        public:
                            Box(int n){value=n;finish();}
                            int read() const{return value;}
                        private:
                            void finish(){value+=2;}
                        };
                        int main(){Box box(5);printf("%d\\n",box.read());return 0;}
                        """, "7\n"),
                Arguments.of("array-elements-run-default-construction", """
                        #include <stdio.h>
                        int calls=0;
                        struct Box{int sequence;Box():sequence(++calls){}};
                        int main(){Box values[2];printf("%d %d %d\\n",calls,values[0].sequence,values[1].sequence);return 0;}
                        """, "2 1 2\n"),
                Arguments.of("aggregate-element-overrides-default-member-initializer", """
                        #include <stdio.h>
                        int calls=0;int next(){++calls;return 1;}
                        struct Box{int value=next();};
                        int main(){Box value{2};printf("%d %d\\n",value.value,calls);return 0;}
                        """, "2 0\n"),
                Arguments.of("array-member-empty-list-initializes-each-element", """
                        #include <stdio.h>
                        struct Box{int values[2];Box():values{}{} };
                        int main(){Box value;printf("%d %d\\n",value.values[0],value.values[1]);return 0;}
                        """, "0 0\n"),
                Arguments.of("aggregate-member-nonempty-list-initializes-value", """
                        #include <stdio.h>
                        struct Pair{int value;};struct Box{Pair p;Box():p{3}{} };
                        int main(){Box value;printf("%d\\n",value.p.value);return 0;}
                        """, "3\n"),
                Arguments.of("global-construction-completes-before-main", """
                        #include <stdio.h>
                        int calls=0;struct Box{int sequence;Box():sequence(++calls){}};Box global;
                        int main(){printf("%d %d\\n",calls,global.sequence);return 0;}
                        """, "1 1\n"));
    }

    @Test @Timeout(120)
    void independentGxxOracleAcceptsEveryPositiveProgramAndExpectedOutput() throws Exception {
        for (var arguments : programs().toList()) {
            Object[] values=arguments.get();
            Path source=temporary.resolve(values[0]+".cpp");
            Path executable=temporary.resolve(values[0]+".exe");
            Files.writeString(source,(String)values[1]);
            var compile=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                    "-std=c++17","-pedantic-errors",source.toString(),"-o",executable.toString()),
                    temporary,"",Duration.ofSeconds(20),65536);
            assertFalse(compile.timedOut(),compile::stderr);
            assertEquals(0,compile.exitCode(),values[0]+": "+compile.stderr());
            var run=BoundedProcess.run(List.of(executable.toString()),temporary,"",Duration.ofSeconds(5),65536);
            assertFalse(run.timedOut());
            assertEquals(0,run.exitCode(),run::stderr);
            assertEquals(values[2],run.stdout().replace("\r\n","\n"),values[0].toString());
        }
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void constructorExecutionAgreesAcrossThreeBackends(String name,String source,String expected) throws Exception {
        agree(temporary,name,source,expected);
    }

    static Stream<Arguments> invalidPrograms() {
        return Stream.of(
                Arguments.of("private-constructor","class Box{Box(){} };\nint main(){Box b; // bad\nreturn 0;}"),
                Arguments.of("duplicate-member-initializer","struct Box{int x;\nBox():x(1),x(2){} // bad\n};int main(){return 0;}"),
                Arguments.of("unknown-member-initializer","struct Box{int x;\nBox():missing(1){} // bad\n};int main(){return 0;}"),
                Arguments.of("no-viable-constructor","struct Box{Box(int*n){} };\nint main(){Box b(4); // bad\nreturn 0;}"),
                Arguments.of("no-default-constructor","struct Box{Box(int n){} };\nint main(){Box b; // bad\nreturn 0;}"),
                Arguments.of("uninitialized-reference-member","struct Box{int&r;\nBox(){} // bad\n};int main(){return 0;}"),
                Arguments.of("const-member-write-after-construction","struct Box{const int x;Box():x(1){} };\nint main(){Box b;\nb.x=2; // bad\nreturn 0;}"),
                Arguments.of("direct-list-narrowing","struct Box{int x;Box(int n):x(n){} };\nint main(){Box b{3.8}; // bad\nreturn 0;}"),
                Arguments.of("copy-list-narrowing","struct Box{int x;Box(int n):x(n){} };\nint main(){Box b={3.8}; // bad\nreturn 0;}"),
                Arguments.of("duplicate-constructor-definition","struct Box{Box(){}\nBox(){} // bad\n};int main(){return 0;}"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void illFormedConstructionHasSpecificSourceDiagnostics(String name,String source) throws Exception {
        reject(temporary,name,source);
        var api=compiler(source);
        var semantic=stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);
        int line=(int)source.substring(0,source.indexOf("// bad")).chars().filter(c->c=='\n').count()+1;
        assertTrue(semantic.errors().stream().anyMatch(error -> error.range().startLine()==line
                && !error.code().equals("CPP005")),()->semantic.errors().toString());
    }

    @Test void constructorMappingPreservesOriginalSignatureAndSourceRange() {
        var api=compiler(ADDRESS_SOURCE);
        var parser=stage(api,Parser.class);
        var semantic=stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var constructor=nodes(parser.result().program()).stream().filter(ConstructorMember.class::isInstance)
                .map(ConstructorMember.class::cast).findFirst().orElseThrow();
        assertEquals(1,constructor.parameters().size());
        var core=assertInstanceOf(FunctionDecl.class,semantic.semanticResult().sourceToCore().get(constructor));
        assertEquals(constructor.range(),core.range());
        assertEquals(2,core.parameters().size(),"source constructor keeps only its written parameter");
        assertTrue(core.parameters().getFirst().type().isPointer());
        assertTrue(semantic.semanticResult().displayNames().containsValue("Model::Node::Node"));
        for(var parameter:constructor.parameters()) assertNotNull(semantic.semanticResult().sourceToCore().get(parameter));
    }

    @Test void constructionUsesNoHeapAndDebuggerHistoryRetainsConstructorFrame() {
        assertDebugHistory(ADDRESS_SOURCE,"1 7\n","Model::Node::Node","amount");
    }

    static Stream<Arguments> boundaryPrograms() {
        return Stream.of(
                Arguments.of("default-member-lookup-excludes-constructor-parameters", """
                        #include <stdio.h>
                        int seed=4;
                        struct Box {int value=seed;Box(int seed);};
                        Box::Box(int seed){}
                        int main(){Box box(9);printf("%d\\n",box.value);return 0;}
                        """, "4\n"),
                Arguments.of("unused-implicitly-deleted-default-constructor", """
                        #include <stdio.h>
                        struct View {int &value;int tag=2;};
                        int main(){printf("unused\\n");return 0;}
                        """, "unused\n"),
                Arguments.of("const-owner-does-not-const-qualify-reference-referent", """
                        #include <stdio.h>
                        struct View {int &value;View(int &v):value(v){} void write(int n)const{value=n;}};
                        int main(){int x=1;const View v(x);v.value=4;v.write(6);
                            printf("%d %d\\n",x,&v.value==&x);return 0;}
                        """, "6 1\n"),
                Arguments.of("copy-preserves-reference-member-binding", """
                        #include <stdio.h>
                        struct View {int &value;View(int &v):value(v){}};
                        int main(){int x=1;View a(x);View b=a;b.value=9;
                            printf("%d %d %d\\n",x,&a.value==&x,&b.value==&x);return 0;}
                        """, "9 1 1\n"),
                Arguments.of("empty-aggregate-list-initializes-members-in-order", """
                        #include <stdio.h>
                        struct Box {int first;int second=first+3;};
                        int main(){Box box{};printf("%d %d\\n",box.first,box.second);return 0;}
                        """, "0 3\n"),
                Arguments.of("nonaggregate-implicit-value-init-zeroes-before-default-construction", """
                        #include <stdio.h>
                        class Box {int first;int second=first+3;
                        public:int sum()const{return first+second;}};
                        int main(){Box box{};printf("%d\\n",box.sum());return 0;}
                        """, "3\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("boundaryPrograms")
    void constructorLookupAndReferenceBoundariesAgree(String name,String source,String expected) throws Exception {
        agree(temporary,name,source,expected);
    }

    @Test void defaultMemberCannotSeeLaterNamespaceDeclarations() throws Exception {
        reject(temporary,"late-dmi-name","""
                struct Box {
                    int value=later; // bad
                    Box();
                };
                int later=7;
                Box::Box(){}
                int main(){return 0;}
                """);
    }

    @Test void deletedImplicitDefaultConstructorIsDiagnosedAtUse() throws Exception {
        reject(temporary,"deleted-default","""
                struct View {int &value;int tag=2;};
                int main(){View value; // bad
                    return 0;
                }
                """);
    }

    @Test void emptyAggregateListDoesNotPreinitializeLaterMembersBeforeDmiRuns() {
        // Reading the later member is undefined in C++; this is a debugger check, not a reference run.
        var source="struct Box{int first=second;int second;};int main(){Box box{};return box.first;}";
        var debug=new minic.debug.DebugApi(new minic.compiler.SourceFile("aggregate-order.cpp",source),"",
                minic.compiler.LanguageMode.CPP17_ALGORITHM);
        for(int step=0;debug.canNext()&&step<1000;step++)debug.next();
        assertEquals(minic.debug.Debugger.Status.FAILED,debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"),debug.current().stop()::error);
    }

    @Test void listConstructorArgumentsCannotHideNarrowingInsideReferenceTemporaries() throws Exception {
        reject(temporary,"reference-parameter-narrowing","""
                struct Box {int value;Box(const int &n):value(n){}};
                int main(){Box value{3.8}; // bad
                    return 0;
                }
                """);
    }

    @Test void singleSelfByValueParameterIsNotAValidConstructor() throws Exception {
        reject(temporary,"self-value-constructor","""
                struct Box {
                    Box(Box other){} // bad
                };
                int main(){return 0;}
                """);
    }

}
