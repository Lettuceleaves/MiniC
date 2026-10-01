package minic.cpp;

import minic.compiler.parser.Parser;
import minic.compiler.parser.node.CppDestructorCallExpr;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

/** Explicit destructor calls bind the written type and evaluate the receiver once. */
@Tag("cpp-differential") @Timeout(90)
final class CppExplicitDestructorExecutionTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() { return Stream.of(
            Arguments.of("trivial-object", "struct T{};int main(){T value;value.~T();printf(\"done \");return 0;}", "done "),
            Arguments.of("trivial-pointer-once", "struct T{};int count;T* next(T*p){++count;return p;}int main(){T value;next(&value)->~T();printf(\"%d \",count);return 0;}", "1 "),
            Arguments.of("trivial-grouped-once", "struct T{};int count;T* next(T*p){++count;return p;}int main(){T value;(*next(&value)).~T();printf(\"%d \",count);return 0;}", "1 "),
            Arguments.of("injected-name", "namespace N{struct T{};}int main(){N::T value;value.~T();printf(\"done \");return 0;}", "done "),
            Arguments.of("injected-name-over-context", "struct T{};namespace N{struct T{};}int main(){N::T value;value.~T();printf(\"done \");return 0;}", "done "),
            Arguments.of("alias-and-const", "namespace N{struct T{};}typedef N::T Alias;int main(){const Alias value={};value.~Alias();printf(\"done \");return 0;}", "done "),
            Arguments.of("pseudo-scalar-once", "typedef int I;int count;I* next(I*p){++count;return p;}int main(){I value=2;next(&value)->~I();printf(\"%d \",count);return 0;}", "1 "),
            Arguments.of("pseudo-const-scalar", "typedef int I;int main(){const I value=2;value.~I();printf(\"done \");return 0;}", "done "),
            Arguments.of("pseudo-pointer-object", "typedef int* P;int main(){P value=nullptr;value.~P();printf(\"done \");return 0;}", "done "));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void evaluatedCallsAgreeAcrossBackends(String name,String body,String expected) throws Exception {
        agree(temporary,name,"#include <stdio.h>\n"+body,expected);
    }

    @ParameterizedTest @ValueSource(strings={
            "typedef int I;int main(){I value;value.~I();return 0;}",
            "typedef int I;int calls;I next(){++calls;return 1;}int main(){next().~I();return calls-1;}"
    })
    void pseudoDestructorEvaluatesWithoutInventingAnLvalueRead(String source) throws Exception {
        agree(temporary,"pseudo-value-category",source,"");
    }

    @Test void independentCpp17OracleAcceptsTheRuntimePrograms() throws Exception {
        for(var arguments:programs().toList()) {
            var values=arguments.get();
            oracle(values[0].toString(),"#include <stdio.h>\n"+values[1],true);
        }
    }

    @ParameterizedTest @ValueSource(strings={
            "struct T{~T(){}};void destroy(T*p){p->~T();}int main(){return 0;}",
            "struct T{~T();};T::~T(){}void destroy(T&value){value.~T();}int main(){return 0;}",
            "class T{~T(){} public:void destroy(T*p){p->~T();}};int main(){return 0;}",
            "struct T{~T(){}};void destroy(const T*p){p->~T();}int main(){return 0;}",
            "namespace N{struct T{~T(){}};}void destroy(N::T*p){p->~T();}int main(){return 0;}"
    })
    void nontrivialCallsKeepSourceIdentityAndVoidType(String source) throws Exception {
        oracle("nontrivial",source,true);
        var api=compiler(source);var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);assertTrue(parser.succeeded(),()->parser.errors().toString());
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var original=nodes(parser.result().program()).stream().filter(CppDestructorCallExpr.class::isInstance).findFirst().orElseThrow();
        var normalized=semantic.semanticResult().sourceToCore().get(original);
        assertNotNull(normalized);assertNotSame(original,normalized);assertEquals(original.range(),normalized.range());
        assertEquals(minic.compiler.type.MiniType.VOID,semantic.semanticResult().typeOf((CppDestructorCallExpr)original).orElseThrow());
    }

    @ParameterizedTest @ValueSource(strings={
            "class T{~T(){}};void destroy(T*p){p->~T(); // bad\n}int main(){return 0;}",
            "struct T{protected:~T(){}};void destroy(T*p){p->~T(); // bad\n}int main(){return 0;}",
            "struct A{};struct B{};void destroy(A*p){p->~B(); // bad\n}int main(){return 0;}",
            "namespace A{struct T{};}namespace B{struct T{};}typedef B::T Other;void destroy(A::T*p){p->~Other(); // bad\n}int main(){return 0;}",
            "struct T;void destroy(T*p){p->~T(); // bad\n}int main(){return 0;}",
            "typedef double D;void destroy(int*p){p->~D(); // bad\n}int main(){return 0;}",
            "typedef int I;void destroy(void*p){p->~I(); // bad\n}int main(){return 0;}",
            "struct T{};void destroy(T*p){int value=p->~T(); // bad\n}int main(){return 0;}"
    })
    void wrongTypeAccessOrUseHasAnOrdinarySourceError(String source) throws Exception {
        reject(temporary,"invalid-explicit-destructor",source);
        var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.errors().stream().noneMatch(error->error.code().equals("CPP005")),()->semantic.errors().toString());
    }

    private void oracle(String name,String source,boolean accepted) throws Exception {
        Path file=temporary.resolve(name+".cpp");Files.writeString(file,source);
        var result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                "-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(result.timedOut());assertFalse(result.outputExceeded());
        assertEquals(accepted,result.exitCode()==0,result::stderr);
    }
}
