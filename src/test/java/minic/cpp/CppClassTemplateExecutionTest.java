package minic.cpp;

import minic.compiler.*;
import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Unified final acceptance: a template spelling never delegates MiniC compilation to the oracle. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppClassTemplateExecutionTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("distinct-layouts","""
            template<class T>struct Box{T value;T get()const{return value;}void set(T next){value=next;}T& ref(){return value;}};
            int main(){Box<int> a={3};Box<double> b={2.5};a.set(7);b.ref()=4.5;
              printf("%llu %llu %d %.1f\\n",(unsigned long long)sizeof(a),(unsigned long long)sizeof(b),a.get(),b.get());return 0;}
            ""","4 8 7 4.5\n"),
        Arguments.of("recursive-pointers","""
            template<class T>struct Node{T value;Node* next;Node<T>* link(){return next;}};
            int main(){Node<int> b={9,0};Node<int> a={4,&b};Node<double> d={2.5,0};
              printf("%d %d %.1f\\n",a.value,a.link()->value,d.value);return 0;}
            ""","4 9 2.5\n"),
        Arguments.of("later-record-argument","""
            template<class T>struct Box{T value;T get()const{return value;}};
            struct Pair{int a;int b;};
            int main(){Box<Pair> box={{3,8}};Pair result=box.get();printf("%d %d %llu\\n",result.a,result.b,(unsigned long long)sizeof(box));return 0;}
            ""","3 8 8\n"),
        Arguments.of("definition-point","""
            int choose(int){return 1;}int value=7;
            namespace N{template<class T>struct Box{T stored;int get(){return choose(2.5)+value;}};}
            int choose(double){return 100;}
            namespace Other{int value=50;int run(){N::Box<int> box={3};return box.get();}}
            int main(){printf("%d\\n",Other::run());return 0;}
            ""","8\n"),
        Arguments.of("namespace-identity","""
            namespace A{template<class T>struct Box{T value;int tag(){return 1;}};}
            namespace B{template<class T>struct Box{T value;int extra;int tag(){return 2;}};}
            int main(){A::Box<int> a={3};B::Box<int> b={4,5};printf("%d %d %llu %llu\\n",a.tag(),b.tag(),(unsigned long long)sizeof(a),(unsigned long long)sizeof(b));return 0;}
            ""","1 2 4 8\n"),
        Arguments.of("construction-destruction","""
            int log=0;template<class T>struct Box{T value;Box(T v):value(v){log=log*10+1;}~Box(){log=log*10+2;}T get(){return value;}};
            int main(){{Box<int> a(3);Box<double> b(2.5);printf("%d %.1f %d\\n",a.get(),b.get(),log);}printf("%d\\n",log);return 0;}
            ""","3 2.5 11\n1122\n"),
        Arguments.of("unused-dependent-method","""
            template<class T>struct Box{T value;int unused(){return value.noSuchMember;}T get(){return value;}};
            int main(){Box<int> b={7};printf("%d\\n",b.get());return 0;}
            ""","7\n")
        ,Arguments.of("unevaluated-method","""
            template<class T>struct Box{T value;int unused(){return value.noSuchMember;}T get(){return value;}};
            int main(){Box<int> b={7};printf("%llu %d\\n",(unsigned long long)sizeof(b.unused()),b.get());return 0;}
            ""","4 7\n")
    );}

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void templatesAgreeAcrossNativeDebugAndReference(String name,String text,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+text,"");
        assertEquals(CppDifferentialHarness.Status.OK,report.outcomes().get(CppDifferentialHarness.Backend.GXX).status(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"),report::describe);
        assertTrue(report.passed(),report::describe);
    }
}
