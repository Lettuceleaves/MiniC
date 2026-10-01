package minic.cpp;

import minic.compiler.*;
import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Source cases queued for the user's unified acceptance run. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppNonTypeTemplateTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("dependent-array","""
            template<class T,int N>struct Buffer{T values[N];int size(){return N;}T& at(int i){return values[i];}};
            int main(){Buffer<int,3> a={{2,4,6}};Buffer<double,2> b={{1.5,2.5}};a.at(1)=9;printf("%d %d %.1f %llu %llu\\n",a.size(),a.at(1),b.at(1),(unsigned long long)sizeof(a),(unsigned long long)sizeof(b));return 0;}
            ""","3 9 2.5 12 16\n"),
        Arguments.of("dependent-value-type","""
            template<class T,T V>struct Constant{T get(){return V;}};
            int main(){Constant<bool,true> b;Constant<unsigned long long,18446744073709551615ULL> u;Constant<int,-3> s;printf("%d %llu %d\\n",b.get(),u.get(),s.get());return 0;}
            ""","1 18446744073709551615 -3\n"),
        Arguments.of("default-value","""
            template<int N=3,int M=N+1>struct Size{int values[M];int get(){return N*10+M;}};
            int main(){Size<> a={{1,2,3,4}};Size<5> b={{1,2,3,4,5,6}};printf("%d %d %llu\\n",a.get(),b.get(),(unsigned long long)sizeof(b));return 0;}
            ""","34 56 24\n"),
        Arguments.of("canonical-key","""
            template<int N>struct Box{int value;Box* self(){return this;}};
            int main(){Box<3> b={7};Box<1+2>* same=b.self();printf("%d\\n",same->value);return 0;}
            ""","7\n"),
        Arguments.of("dependent-nested-value","""
            template<int N>struct Buffer{int values[N];int size(){return N;}};
            template<int N>struct Outer{Buffer<N+1> inner;int size(){return inner.size();}};
            int main(){Outer<2> o={{{1,2,3}}};printf("%d %d\\n",o.size(),o.inner.values[2]);return 0;}
            ""","3 3\n"),
        Arguments.of("constant-operators","""
            template<unsigned N>struct Box{int value;unsigned get(){return N;}};
            int main(){Box<(8>>1)> a={1};Box<(3<4?7:9)> b={2};Box<(0xffffffffU+1U)> c={3};printf("%u %u %u\\n",a.get(),b.get(),c.get());return 0;}
            ""","4 7 0\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void integralArgumentsAndBoundsAgreeWithCpp(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "template<int N>struct B{int data[N];};int main(){B<0> b;return 0;}",
        "template<bool V>struct B{};int main(){B<2> b;return 0;}",
        "template<int N>struct B{};int main(){int n=3;B<n> b;return 0;}",
        "template<int N>struct B{};int main(){B<(1/0)> b;return 0;}",
        "template<int N>struct B{int N;};int main(){return 0;}"
    })
    void invalidConstantArgumentsAreDiagnosed(String source)throws Exception{
        Path input=temporary.resolve("invalid.cpp");java.nio.file.Files.writeString(input,source);
        var reference=BoundedProcess.run(java.util.List.of(CppDifferentialHarness.referenceCompiler(System.getenv()).toString(),"-std=c++17","-pedantic-errors","-fsyntax-only",input.toString()),temporary,"",java.time.Duration.ofSeconds(20),64*1024);
        assertFalse(reference.timedOut());assertNotEquals(0,reference.exitCode());
        var api=new CompilerApi(new minic.SourceFile("invalid-template.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(minic.compiler.semantic.SemanticAnalyzer.class::isInstance).map(minic.compiler.semantic.SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(api.stages().stream().anyMatch(stage->!stage.errors().isEmpty()));
    }
}
