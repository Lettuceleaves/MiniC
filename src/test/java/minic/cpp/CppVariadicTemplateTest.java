package minic.cpp;

import minic.compiler.*;
import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred acceptance cases: compile this class now; execute only in the final unified suite. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppVariadicTemplateTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("empty-and-heterogeneous-pack", """
            template<class... T>int count(T... values){return sizeof...(T)*10+sizeof...(values);}
            int main(){printf("%d %d %d\\n",count(),count(2),count(1,2.0,'a'));return 0;}
            ""","0 11 33\n"),
        Arguments.of("function-pointer-pack-target", """
            template<class... T>int count(T... values){return sizeof...(T);}
            int main(){int(*p)(int,double)=count;printf("%d\\n",p(1,2.0));return 0;}
            ""","2\n"),
        Arguments.of("recursive-function-pack", """
            int sum(){return 0;}template<class H,class... T>int sum(H head,T... tail){return (int)head+sum(tail...);}
            int main(){printf("%d %d\\n",sum(1,2,3,4),sum(1.5,3));return 0;}
            ""","10 4\n"),
        Arguments.of("explicit-pack-prefix", """
            template<class... T>int count(T... values){return sizeof...(T);}
            int main(){printf("%d %d\\n",count<int,double>(2,3),count<int>(1,2.0,3));return 0;}
            ""","2 3\n"),
        Arguments.of("class-empty-and-partial-specialization", """
            template<class... T>struct Chain;
            template<>struct Chain<>{int size(){return 0;}};
            template<class H,class... T>struct Chain<H,T...>{Chain<T...> tail;int size(){return 1+tail.size();}};
            int main(){Chain<> a;Chain<int,double,char> b;printf("%d %d\\n",a.size(),b.size());return 0;}
            ""","0 3\n"),
        Arguments.of("non-type-pack", """
            template<int... N>int total(){int values[sizeof...(N)]={N...};int result=0;for(int i=0;i<(int)sizeof...(N);i++)result+=values[i];return result;}
            template<int... N>struct Values{int get(){return total<N...>();}};
            int main(){Values<2,4,7> values;printf("%d %d\\n",total<1,3,5>(),values.get());return 0;}
            ""","9 13\n"),
        Arguments.of("forwarding-parameter-pack", """
            template<class T>T&& forward(T& value){return (T&&)value;}
            int select(int& value){value+=2;return 1;}int select(int&& value){return 2;}
            template<class... T>int choices(T&&... values){int results[sizeof...(T)]={select(forward<T>(values))...};int result=0;for(int i=0;i<(int)sizeof...(T);i++)result=result*10+results[i];return result;}
            int main(){int value=3;int code=choices(value,7);printf("%d %d\\n",code,value);return 0;}
            ""","12 5\n"),
        Arguments.of("member-and-constructor-packs", """
            int sum(){return 0;}template<class H,class... T>int sum(H h,T... tail){return (int)h+sum(tail...);}
            struct Box{int value;template<class... T>Box(T... args):value(sum(args...)){}template<class... T>int add(T... args){return value+sum(args...);}};
            int main(){Box a(1,2,3);Box b;printf("%d %d %d\\n",a.value,b.value,a.add(4,5));return 0;}
            ""","6 0 15\n"),
        Arguments.of("fixed-template-arity-from-expansion", """
            template<class A,class B>struct Pair{A first;B second;};
            template<class... T>struct Holder{Pair<T...> pair;};
            int main(){Holder<int,double> value={{3,2.5}};printf("%d %.1f\\n",value.pair.first,value.pair.second);return 0;}
            ""","3 2.5\n"),
        Arguments.of("class-and-method-packs-zipped", """
            template<class... L>struct Zip{template<class... R>int sum(R... values){int parts[sizeof...(L)]={(int)sizeof(L)+(int)values...};int total=0;for(int i=0;i<(int)sizeof...(L);i++)total+=parts[i];return total;}};
            int main(){Zip<int,double> value;printf("%d\\n",value.sum(1,2));return 0;}
            ""","15\n"),
        Arguments.of("nested-expansion", """
            int sum(){return 0;}template<class H,class... T>int sum(H h,T... rest){return h+sum(rest...);}
            template<class... T>int nested(T... values){return sum((sum(values...)+values)...);}
            int main(){printf("%d\\n",nested(1,2,3));return 0;}
            ""","24\n"),
        Arguments.of("query-type-pack", """
            struct Box{Box(int,double){}};
            template<class T,class... A>int possible(){return __is_constructible(T,A...);}
            int main(){printf("%d %d %d\\n",possible<Box,int,double>(),possible<Box>(),possible<int>());return 0;}
            ""","1 0 1\n"),
        Arguments.of("pack-size-in-default", """
            template<bool V,class T=int>struct Enable{};template<class T>struct Enable<true,T>{typedef T type;};
            template<class... A,typename Enable<(sizeof...(A)>1),int>::type=0>int choose(A... values){return 7;}
            int choose(...){return 2;}int main(){printf("%d %d\\n",choose(1),choose(1,2));return 0;}
            ""","2 7\n"),
        Arguments.of("braced-elements-and-overloads", """
            #include <initializer_list>
            template<class... T>int lengths(std::initializer_list<T>... lists){int result[sizeof...(T)]={(int)lists.size()...};return result[0]+result[1];}
            int main(){printf("%d\\n",lengths({1,2},{3.5,4.5,5.5}));return 0;}
            ""","5\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void packsAgreeAcrossBackends(String name,String source,String expected)throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "template<class... T,class U>struct Bad{};int main(){return 0;}",
        "template<class... T=int>struct Bad{};int main(){return 0;}",
        "template<class... T>int bad(T... values){return values;}int main(){return bad(1,2);}",
        "template<class T>int bad(T value){return sizeof...(T);}int main(){return bad(1);}",
        "int main(){return sizeof...(missing);}",
        "template<class... T>int bad(T... values){int data[2]={(1)...};return 0;}int main(){return bad(1);}",
        "template<class... A>struct Zip{template<class... B>int f(B... values){int data[sizeof...(A)]={(int)sizeof(A)+(int)values...};return data[0];}};int main(){Zip<int,double> z;return z.f(1);}",
        "template<class A,class B>struct Pair{};template<class... T>struct Holder{Pair<T...> member;};int main(){Holder<int> value;return 0;}"
    })
    void malformedOrUnexpandedPacksAreDiagnosed(String source)throws Exception {
        Path file=temporary.resolve("invalid.cpp");Files.writeString(file,source);
        var reference=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()).toString(),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),64*1024);
        assertFalse(reference.timedOut());assertFalse(reference.outputExceeded());assertNotEquals(0,reference.exitCode());
        var api=new CompilerApi(new SourceFile("invalid-pack.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(minic.compiler.semantic.SemanticAnalyzer.class::isInstance).findFirst().orElseThrow();api.runThrough(semantic);
        assertTrue(api.stages().stream().anyMatch(stage->!stage.errors().isEmpty()));
    }
}
