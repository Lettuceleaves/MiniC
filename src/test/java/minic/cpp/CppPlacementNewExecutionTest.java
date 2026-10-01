package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual allocation lookup and construction, with user-defined allocation functions as the oracle. */
@Tag("cpp-differential") @Timeout(90)
final class CppPlacementNewExecutionTest {
    @TempDir Path temporary;
    private static final String HEADERS="#include <stdio.h>\n#include <stdlib.h>\n";
    private static final String PLACEMENT="void* operator new(unsigned long long size,void* storage){return storage;}";
    static Stream<Arguments> programs(){return Stream.of(
            Arguments.of("scalar-paren",PLACEMENT+"int main(){void*s=malloc(32);int*p=new(s) int(7);printf(\"%d %d\\n\",*p,(void*)p==s);free(s);return 0;}","7 1\n"),
            Arguments.of("scalar-empty-list",PLACEMENT+"int main(){void*s=malloc(32);int*p=::new(s) int{};printf(\"%d\\n\",*p);free(s);return 0;}","0\n"),
            Arguments.of("const-target",PLACEMENT+"int main(){void*s=malloc(32);const int*p=new(s) const int(8);printf(\"%d\\n\",*p);free(s);return 0;}","8\n"),
            Arguments.of("pointer-target",PLACEMENT+"int main(){void*s=malloc(32);int n=9;int**p=new(s) int*(&n);printf(\"%d\\n\",**p);free(s);return 0;}","9\n"),
            Arguments.of("final-constructor-address",PLACEMENT+"struct T{T*self;int n;T(int value):self(this),n(value){}};int main(){void*s=malloc(64);T*p=new(s) T(10);printf(\"%d %d %d\\n\",p->n,p->self==p,(void*)p==s);free(s);return 0;}","10 1 1\n"),
            Arguments.of("aggregate-list",PLACEMENT+"struct T{int a;double b;};int main(){void*s=malloc(64);T*p=new(s) T{3,4.5};printf(\"%d %.1f\\n\",p->a,p->b);free(s);return 0;}","3 4.5\n"),
            Arguments.of("allocation-overload", "void* saved;void* operator new(unsigned long long size,int tag){printf(\"I%d \",tag);return saved;}void* operator new(unsigned long long size,double tag){printf(\"F \");return saved;}int main(){saved=malloc(32);int*p=new(6)int(12);printf(\"%d\\n\",*p);free(saved);return 0;}","I6 12\n"),
            Arguments.of("multiple-placement-arguments", "void* operator new(unsigned long long size,void* p,int offset){printf(\"%llu \",size);return (char*)p+offset;}int main(){void*s=malloc(64);long long*p=new(s,16)long long(13);printf(\"%lld %d\\n\",*p,(void*)p==(void*)((char*)s+16));free(s);return 0;}","8 13 1\n"),
            Arguments.of("heap-object-is-not-an-automatic-temporary",PLACEMENT+"struct T{int n;T():n(14){}~T(){printf(\"D \");}};int main(){void*s=malloc(32);T*p=new(s)T;printf(\"%d\\n\",p->n);free(s);return 0;}","14\n"),
            Arguments.of("empty-object",PLACEMENT+"struct T{};int main(){void*s=malloc(32);T*p=new(s)T{};printf(\"%d\\n\",(void*)p==s);free(s);return 0;}","1\n"),
            Arguments.of("private-fields-value-initialized",PLACEMENT+"class T{int n;public:int get(){return n;}};int main(){void*s=malloc(32);*(int*)s=123;T*p=new(s)T();printf(\"%d\\n\",p->get());free(s);return 0;}","0\n"),
            Arguments.of("implicit-default-constructor-value-initialized",PLACEMENT+"struct I{int n;I(){}};class T{int n;I i;public:int get(){return n;}};int main(){void*s=malloc(32);*(int*)s=123;T*p=new(s)T();printf(\"%d\\n\",p->get());free(s);return 0;}","0\n"),
            Arguments.of("construction-does-not-require-destructor-access",PLACEMENT+"class T{~T(){}public:int n;T():n(15){}};int main(){void*s=malloc(32);T*p=new(s)T();printf(\"%d\\n\",p->n);free(s);return 0;}","15\n"));}

    // C++17 [expr.new]/19 requires this order. GCC 8 preevaluates the initializer;
    // GCC fixed that bug in r12-6325: https://gcc.gnu.org/pipermail/gcc-cvs/2022-January/358830.html
    @Test void allocationPrecedesInitializerEvenWithAnOlderReferenceCompiler() throws Exception {
        String source=HEADERS+"void* saved;void* storage(){printf(\"P \");return saved;}void* operator new(unsigned long long size,void* p){printf(\"A \");return p;}int argument(){printf(\"I \");return 5;}struct T{int n;T(int value):n(value){printf(\"C \");}};int main(){saved=malloc(32);T*p=new(storage())T(argument());printf(\"%d\\n\",p->n);free(saved);return 0;}";
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run("standard-allocation-sequence",source,"");
        for(var outcome:report.outcomes().values()){
            assertEquals(CppDifferentialHarness.Status.OK,outcome.status(),report::describe);
            assertEquals(0,outcome.exitCode(),report::describe);
            String output=outcome.stdout().replace("\r\n","\n");
            if(outcome.backend()==CppDifferentialHarness.Backend.GXX) assertTrue(List.of("P A I C 5\n","I P A C 5\n").contains(output),output);
            else assertEquals("P A I C 5\n",output);
        }
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void executesGlobalPlacementAllocationAndInitialization(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,HEADERS+source,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout().replace("\r\n","\n")));
    }

    @ParameterizedTest @ValueSource(strings={
            "void* operator new(int size,void*p){return p;}int main(){return 0;}",
            "int* operator new(unsigned long long size,void*p){return (int*)p;}int main(){return 0;}",
            "namespace N{void* operator new(unsigned long long size,void*p){return p;}}int main(){return 0;}",
            "int main(){void*p=0;new(p)int(1);return 0;}",
            "void* operator new(unsigned long long size,long tag);void* operator new(unsigned long long size,double tag);int main(){new(1)int(1);return 0;}",
            "void* operator new(unsigned long long size,void*p){return p;}int main(){void*p=0;new(p)int&;return 0;}",
            "void* operator new(unsigned long long size,void*p){return p;}int main(){void*p=0;new(p)void;return 0;}",
            "void* operator new(unsigned long long size,void*p){return p;}struct T;int main(){void*p=0;new(p)T;return 0;}",
            "void* operator new(unsigned long long size,void*p){return p;}int main(){void*p=0;new(p)int{2.5};return 0;}"})
    void invalidAllocationProgramsAreRejected(String source)throws Exception{
        Path file=temporary.resolve("invalid.cpp");Files.writeString(file,source);
        var oracle=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(oracle.timedOut());assertNotEquals(0,oracle.exitCode());
        var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertFalse(semantic.succeeded());assertFalse(semantic.errors().isEmpty());
    }
}
