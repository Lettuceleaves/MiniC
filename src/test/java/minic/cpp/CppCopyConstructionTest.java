package minic.cpp;

import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.ConstructorMember;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
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

/** F08: copy construction is a source operation, including at ABI boundaries. */
@Tag("cpp-differential") @Execution(ExecutionMode.SAME_THREAD) @Timeout(90)
final class CppCopyConstructionTest {
    @TempDir Path temporary;
    static final String BOX="""
            #include <stdio.h>
            int copies;
            struct Box{Box*self;int value;Box(int n):self(this),value(n){}
                Box(const Box&other):self(this),value(other.value){++copies;}
                int read()const{return self==this?value:-1;}};
            """;
    static Stream<Arguments> programs(){return Stream.of(
            Arguments.of("copy-initialization-forms",BOX+"int main(){Box a(3);Box b=a;Box c(a);Box d{a};Box e=Box(a);printf(\"%d %d %d %d %d\\n\",b.read(),c.read(),d.read(),e.read(),copies);return 0;}","3 3 3 3 4\n"),
            Arguments.of("mutable-and-const-copy-overloads","""
                    #include <stdio.h>
                    int trace;struct Box{int value;Box(int n):value(n){}
                    Box(Box&other):value(other.value){trace=trace*10+1;}
                    Box(const Box&other):value(other.value){trace=trace*10+2;}};
                    int main(){Box a(3);const Box b(4);Box c=a;Box d=b;printf("%d %d %d\\n",trace,c.value,d.value);return 0;}
                    ""","12 3 4\n"),
            Arguments.of("by-value-argument-copies-final-parameter",BOX+"int take(Box value){value.value+=2;return value.read();}int main(){Box a(5);int result=take(a);printf(\"%d %d %d\\n\",result,a.read(),copies);return 0;}","7 5 1\n"),
            Arguments.of("return-reference-operand-copies-once",BOX+"Box copy(const Box&source){return source;}int main(){Box a(6);Box b=copy(a);printf(\"%d %d %d\\n\",a.read(),b.read(),copies);return 0;}","6 6 1\n"),
            Arguments.of("mandatory-prvalue-elision-bypasses-copy",BOX+"Box make(int n){return Box(n);}int take(Box n){return n.read();}int main(){Box a=make(7);int b=take(Box(8));printf(\"%d %d %d\\n\",a.read(),b,copies);return 0;}","7 8 0\n"),
            Arguments.of("copy-temporary-reference-lifetime",BOX+"int main(){Box a(9);const Box&b=Box(a);a.value=2;printf(\"%d %d %d %d\\n\",a.read(),b.read(),copies,&a!=&b);return 0;}","2 9 1 1\n"),
            Arguments.of("implicit-outer-copy-invokes-member-copy",BOX+"struct Outer{Box item;Outer(int n):item(n){}};int main(){Outer a(10);Outer b=a;printf(\"%d %d %d\\n\",a.item.read(),b.item.read(),copies);return 0;}","10 10 1\n"),
            Arguments.of("copy-does-not-replay-default-member-initializers","""
                    #include <stdio.h>
                    int initializations;struct Box{int value=++initializations;Box(){}};
                    int main(){Box a;Box b=a;printf("%d %d %d\\n",a.value,b.value,initializations);return 0;}
                    ""","1 1 1\n"),
            Arguments.of("implicit-copy-keeps-reference-and-const-members","""
                    #include <stdio.h>
                    struct View{int&value;const int tag;View(int&v):value(v),tag(4){}};
                    int main(){int x=2;View a(x);View b=a;b.value=7;printf("%d %d %d %d\\n",x,a.tag,b.tag,&b.value==&x);return 0;}
                    ""","7 4 4 1\n"),
            Arguments.of("out-of-line-copy-constructor","""
                    #include <stdio.h>
                    int copies;namespace N{struct Box{int value;Box(int n):value(n){}Box(const Box&);};}
                    N::Box::Box(const N::Box&other):value(other.value+1){++copies;}
                    int main(){N::Box a(4);N::Box b=a;printf("%d %d\\n",b.value,copies);return 0;}
                    ""","5 1\n"),
            Arguments.of("deep-copy-owns-independent-storage","""
                    #include <stdio.h>
                    #include <stdlib.h>
                    int copies;struct Owner{int*data;Owner(int n):data((int*)malloc(sizeof(int))){*data=n;}
                    Owner(const Owner&source):data((int*)malloc(sizeof(int))){*data=*source.data;++copies;}};
                    int main(){Owner a(3);Owner b=a;*b.data=8;printf("%d %d %d %d\\n",*a.data,*b.data,a.data!=b.data,copies);free(a.data);free(b.data);return 0;}
                    ""","3 8 1 1\n"),
            Arguments.of("unevaluated-copy-needs-no-definition","""
                    #include <stdio.h>
                    struct Box{int value;Box(int n):value(n){}Box(const Box&);};
                    int main(){Box a(3);printf("%d\\n",int(sizeof(Box(a))));return 0;}
                    ""","4\n"));}

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void allBackendsAgree(String name,String source,String expected)throws Exception{agree(temporary,name,source,expected);}

    @Test void everyPositiveSourceIsAcceptedByCpp17()throws Exception{
        for(var argument:programs().toList()){Object[] values=argument.get();oracle(temporary,(String)values[0],(String)values[1]);}
    }
    static void oracle(Path directory,String name,String source)throws Exception{
        Path file=directory.resolve(name+".cpp");Files.writeString(file,source);
        var result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),directory,"",Duration.ofSeconds(20),65536);
        assertFalse(result.timedOut(),result::stderr);assertEquals(0,result.exitCode(),result::stderr);
    }

    @Test void copyConstructorKeepsItsSourceReferenceSignature(){
        var api=compiler(BOX+"int main(){Box a(1);Box b=a;return b.value;}");
        var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var source=nodes(parser.result().program()).stream().filter(ConstructorMember.class::isInstance).map(ConstructorMember.class::cast)
                .filter(c->c.parameters().size()==1&&c.parameters().getFirst().type().isReference()).findFirst().orElseThrow();
        var core=assertInstanceOf(FunctionDecl.class,semantic.semanticResult().sourceToCore().get(source));
        assertEquals(source.range(),core.range());assertEquals(2,core.parameters().size());
        assertTrue(source.parameters().getFirst().type().referent().isConstQualified());assertTrue(core.parameters().get(1).type().isPointer());
    }

    static Stream<Arguments> invalidCopies(){return Stream.of(
            Arguments.of("private-copy","class Box{Box(const Box&other){}public:Box(){}};int main(){Box a;Box b=a; // bad\nreturn 0;}"),
            Arguments.of("mutable-copy-cannot-bind-const","struct Box{Box(){}Box(Box&other){}};int main(){const Box a;Box b=a; // bad\nreturn 0;}"));}
    @ParameterizedTest(name="{0}") @MethodSource("invalidCopies")
    void invalidCopiesAreDiagnosedAtUse(String name,String source)throws Exception{
        reject(temporary,name,source);var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP004")),()->semantic.errors().toString());
    }
}
