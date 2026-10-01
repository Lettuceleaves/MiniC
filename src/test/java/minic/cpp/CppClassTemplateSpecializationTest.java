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

/** General primary/full/partial matching, queued for unified acceptance. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppClassTemplateSpecializationTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("full-specialization","""
            template<class T>struct Tag{T value;int get(){return 1;}};
            template<>struct Tag<int>{int value;int extra;int get(){return 2;}};
            int main(){Tag<double> a={1.5};Tag<int> b={3,4};printf("%d %d %llu\\n",a.get(),b.get(),(unsigned long long)sizeof(b));return 0;}
            ""","1 2 8\n"),
        Arguments.of("pointer-ordering","""
            template<class T>struct Tag{int get(){return 0;}};
            template<class T>struct Tag<T*>{int get(){return 1;}};
            template<class T>struct Tag<const T*>{int get(){return 2;}};
            int main(){Tag<int> a;Tag<int*> b;Tag<const int*> c;printf("%d %d %d\\n",a.get(),b.get(),c.get());return 0;}
            ""","0 1 2\n"),
        Arguments.of("remove-cv","""
            template<class T>struct RemoveConst{typedef T type;};
            template<class T>struct RemoveConst<const T>{typedef T type;};
            template<class T>struct RemoveVolatile{typedef T type;};
            template<class T>struct RemoveVolatile<volatile T>{typedef T type;};
            template<class T>struct RemoveCV{typedef typename RemoveConst<typename RemoveVolatile<T>::type>::type type;};
            int main(){RemoveCV<const volatile int>::type value=3;value=7;printf("%d\\n",value);return 0;}
            ""","7\n"),
        Arguments.of("value-pattern","""
            template<bool B,class T,class F>struct Choose{typedef T type;};
            template<class T,class F>struct Choose<false,T,F>{typedef F type;};
            int main(){Choose<true,int,double>::type a=3;Choose<false,int,double>::type b=2.5;printf("%d %.1f\\n",a,b);return 0;}
            ""","3 2.5\n"),
        Arguments.of("repeated-type","""
            template<class T,class U>struct Same{int get(){return 0;}};
            template<class T>struct Same<T,T>{int get(){return 1;}};
            int main(){Same<int,double> a;Same<int,int> b;printf("%d %d\\n",a.get(),b.get());return 0;}
            ""","0 1\n"),
        Arguments.of("nested-pattern","""
            template<class T>struct Box{T value;};
            template<class T>struct Unwrap{typedef T type;};
            template<class T>struct Unwrap<Box<T>>{typedef T type;};
            int main(){Unwrap<Box<int>>::type value=9;printf("%d\\n",value);return 0;}
            ""","9\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void specializationsUseStructuralDeduction(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "template<class T,class U>struct B{};template<class T>struct B<T,int>{};template<class U>struct B<int,U>{};int main(){B<int,int> b;return 0;}",
        "template<class T>struct B{};template<class T>struct B<T>{};int main(){return 0;}",
        "template<class T>struct B{};template<class T,class U>struct B<T*>{};int main(){return 0;}",
        "template<class T>struct B{};template<>struct B<int>{};template<>struct B<int>{};int main(){return 0;}",
        "template<class T>struct B{};B<int> b;template<>struct B<int>{};int main(){return 0;}"
    })
    void invalidOrAmbiguousSpecializationsAreRejected(String source)throws Exception{
        Path input=temporary.resolve("invalid.cpp");java.nio.file.Files.writeString(input,source);
        var reference=BoundedProcess.run(java.util.List.of(CppDifferentialHarness.referenceCompiler(System.getenv()).toString(),"-std=c++17","-pedantic-errors","-fsyntax-only",input.toString()),temporary,"",java.time.Duration.ofSeconds(20),64*1024);
        assertFalse(reference.timedOut());assertNotEquals(0,reference.exitCode());
        var api=new CompilerApi(new minic.SourceFile("invalid-template.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(minic.compiler.semantic.SemanticAnalyzer.class::isInstance).map(minic.compiler.semantic.SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(api.stages().stream().anyMatch(stage->!stage.errors().isEmpty()));
    }
}
