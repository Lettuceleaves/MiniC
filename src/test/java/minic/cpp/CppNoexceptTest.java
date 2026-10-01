package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Queued for unified acceptance. Merely compiling this Java class does not execute an oracle. */
@Tag("cpp-differential") @Timeout(90)
final class CppNoexceptTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs() { return Stream.of(
        Arguments.of("returned-safe-function-pointer", "int target()noexcept{return 9;}typedef int(*Pointer)()noexcept;Pointer factory()noexcept{return target;}int main(){printf(\"%d %d\\n\",noexcept(factory()()),factory()());return 0;}", "1 9\n"),
        Arguments.of("copy-argument-contributes", "struct A{A()noexcept{}A(const A&)noexcept(false){}};int accept(A)noexcept{return 1;}int main(){A value;printf(\"%d %d\\n\",noexcept(accept(value)),noexcept(accept(A())));return 0;}", "0 1\n"),
        Arguments.of("ordinary-and-nested", "int safe()noexcept{return 1;}int loose(){return 2;}int main(){printf(\"%d %d %d %d\\n\",noexcept(safe()),noexcept(loose()),noexcept(noexcept(loose())),sizeof(noexcept(safe()))==sizeof(bool));return 0;}", "1 0 1 1\n"),
        Arguments.of("no-runtime-evaluation", "int count;int touch()noexcept{++count;return 7;}int main(){bool result=noexcept(touch()+count++);printf(\"%d %d\\n\",result,count);return 0;}", "1 0\n"),
        Arguments.of("undefined-unevaluated-functions", "int absent()noexcept;int loose();int main(){printf(\"%d %d\\n\",noexcept(absent()),noexcept(loose()));return 0;}", "1 0\n"),
        Arguments.of("parameter-names-in-specification", "int safe(int value)noexcept(sizeof(value)==sizeof(int)){return value;}int main(){printf(\"%d %d\\n\",noexcept(safe(3)),safe(3));return 0;}", "1 3\n"),
        Arguments.of("pointer-conversion-and-overload", "int f()noexcept{return 3;}int choose(int(*)()noexcept){return 1;}int choose(int(*)()){return 2;}int main(){int(*loose)()=f;int(*safe)()noexcept=f;printf(\"%d %d %d %d\\n\",noexcept(loose()),noexcept(safe()),choose(f),loose());return 0;}", "0 1 1 3\n"),
        Arguments.of("function-reference-conversion", "int f()noexcept{return 4;}int main(){int(&loose)()=f;int(&safe)()noexcept=f;printf(\"%d %d %d\\n\",noexcept(loose()),noexcept(safe()),safe());return 0;}", "0 1 4\n"),
        Arguments.of("noncapturing-lambda-pointer", "int main(){auto lambda=[]()noexcept{return 8;};int(*p)()noexcept=lambda;printf(\"%d %d %d\\n\",noexcept(lambda()),noexcept(p()),p());return 0;}", "1 1 8\n"),
        Arguments.of("temporary-destructor-contributes", "struct T{T()noexcept{}~T()noexcept(false){}};T make()noexcept{return T();}int main(){T value;printf(\"%d %d %d\\n\",noexcept(T()),noexcept(make()),noexcept(value.~T()));return 0;}", "0 0 0\n"),
        Arguments.of("defaulted-special-members", "struct A{A()noexcept{}A(const A&)noexcept{}A&operator=(const A&)noexcept{return *this;}~A()noexcept{}};struct B{A value;};int main(){B a,b;printf(\"%d %d %d %d\\n\",noexcept(B()),noexcept(B(a)),noexcept(a=b),noexcept(a.~B()));return 0;}", "1 1 1 1\n"),
        Arguments.of("user-body-does-not-infer-spec", "struct A{A(){}A(const A&){}A&operator=(const A&){return *this;}~A(){}};int main(){A a,b;printf(\"%d %d %d %d\\n\",noexcept(A()),noexcept(A(a)),noexcept(a=b),noexcept(a.~A()));return 0;}", "0 0 0 1\n"),
        Arguments.of("dmi-overrides-throwing-default", "struct Leaf{Leaf()noexcept(false){}Leaf(int)noexcept{}};struct Mid{Leaf value=Leaf(1);};struct Outer{Mid value;};int main(){printf(\"%d %d\\n\",noexcept(Mid()),noexcept(Outer()));return 0;}", "1 1\n"),
        Arguments.of("explicit-member-init-overrides-dmi", "int loose(){return 1;}struct Mid{int value=loose();Mid()noexcept:value(2){}};struct Outer{Mid value;};int main(){printf(\"%d %d\\n\",noexcept(Mid()),noexcept(Outer()));return 0;}", "1 1\n"),
        Arguments.of("lazy-unused-class-member-spec", "template<class T>struct Holder{void unused()noexcept(sizeof(typename T::missing)>0);};Holder<int> value;int main(){printf(\"ok\\n\");return 0;}", "ok\n"),
        Arguments.of("dependent-spec-selected-on-use", "template<class T>int inspect(T value)noexcept(noexcept(value.run())){return value.run();}struct A{int run()noexcept{return 4;}};struct B{int run(){return 5;}};int main(){A a;B b;printf(\"%d %d %d\\n\",noexcept(inspect(a)),noexcept(inspect(b)),inspect(a));return 0;}", "1 0 4\n"),
        Arguments.of("explicit-false-matches-absent", "int f()noexcept(false);int f(){return 6;}int main(){int(*p)()noexcept(false)=f;printf(\"%d %d\\n\",noexcept(p()),p());return 0;}", "0 6\n"),
        Arguments.of("undefined-behavior-is-not-throwing", "int main(){int value=0;printf(\"%d %d\\n\",noexcept(1/value),noexcept(*(int*)nullptr));return 0;}", "1 1\n")
    ); }
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void sourceResultAndEffectsAgreeAcrossAllBackends(String name,String body,String expected) throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                new CppDifferentialHarness.Limits(Duration.ofSeconds(30),Duration.ofSeconds(10),50000,1048576),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+body,"");
        assertEquals(CppDifferentialHarness.Status.OK,report.outcomes().get(CppDifferentialHarness.Backend.GXX).status(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"),report::describe);
        assertTrue(report.passed(),report::describe);
    }
    // Keep these original source fixtures against the language contract. GCC 8
    // predates CWG 3128 and omits default arguments from implicit ctor noexcept.
    // https://cplusplus.github.io/CWG/issues/3128.html
    // https://timsong-cpp.github.io/cppwp/n4659/except.spec#7.2
    static Stream<Arguments> normativePrograms() { return Stream.of(
        Arguments.of("dead-branch-still-potentially-throwing", "int loose(){return 1;}int main(){printf(\"%d %d %d %d\\n\",noexcept(false&&loose()),noexcept(true||loose()),noexcept(true?1:loose()),noexcept(sizeof(loose())));return 0;}", "0 0 0 1\n"),
        Arguments.of("default-argument-contributes", "int loose(){return 2;}struct Leaf{Leaf(int value=loose())noexcept{}};struct Outer{Leaf value;};int main(){printf(\"%d %d %d\\n\",noexcept(Leaf()),noexcept(Leaf(1)),noexcept(Outer()));return 0;}", "0 1 0\n")
    ); }
    @ParameterizedTest(name="{0}") @MethodSource("normativePrograms")
    void standardRulesRemainCoveredWhenTheLegacyOracleDisagrees(String name,String body,String expected) throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,"#include <stdio.h>\n"+body,"");
        for(var backend:List.of(CppDifferentialHarness.Backend.MINIC_NATIVE,CppDifferentialHarness.Backend.MINIC_DEBUG)) {
            var outcome=report.outcomes().get(backend);
            assertEquals(CppDifferentialHarness.Status.OK,outcome.status(),report::describe);
            assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe);
        }
    }
    @Test void aThrowingFunctionCannotBindToANonThrowingFunctionReference() throws Exception {
        // N4659 [dcl.init.ref]/4.2 allows only the opposite direction. G++ 8
        // incorrectly accepts this source; compile-only prevents executing it.
        // https://timsong-cpp.github.io/cppwp/n4659/dcl.init.ref#4.2
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).compile("reference-strengthening",
                "void loose(){}int main(){void(&r)()noexcept=loose;return 0;}");
        for(var backend:List.of(CppDifferentialHarness.Backend.MINIC_NATIVE,CppDifferentialHarness.Backend.MINIC_DEBUG))
            assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,report.outcomes().get(backend).status(),report::describe);
    }

    static Stream<Arguments> rejectedPrograms() { return Stream.of(
        Arguments.of("floating-modulo", "int main(){return noexcept(1%1.5);}"),
        Arguments.of("null-arithmetic", "int main(){return noexcept(1+nullptr);}"),
        Arguments.of("const-assignment", "int main(){const int value=1;return noexcept(value=2);}"),
        Arguments.of("nonpointer-dereference", "int main(){return noexcept(*1);}"),
        Arguments.of("invalid-sizeof", "int main(){return noexcept(sizeof(void));}"),
        Arguments.of("nonconstant-specification", "int value;void f()noexcept(value);int main(){return 0;}"),
        Arguments.of("redeclaration-spec-mismatch", "int f()noexcept;int f(){return 1;}int main(){return 0;}"),
        Arguments.of("cannot-overload-only-noexcept", "void f()noexcept;void f();int main(){return 0;}"),
        Arguments.of("pointer-strengthening", "void loose(){}int main(){void(*p)()noexcept=loose;return 0;}"),
        Arguments.of("private-call-still-invalid", "class A{void f()noexcept{}};int main(){A value;return noexcept(value.f());}"),
        Arguments.of("deleted-call-still-invalid", "void f()noexcept=delete;int main(){return noexcept(f());}"),
        Arguments.of("deleted-destructor-still-invalid", "struct A{A()noexcept{}~A()=delete;};int main(){return noexcept(A());}"),
        Arguments.of("used-dependent-spec-invalid", "template<class T>struct Holder{void unused()noexcept(sizeof(typename T::missing)>0);};int main(){Holder<int> value;return noexcept(value.unused());}")
    ); }
    @ParameterizedTest(name="{0}") @MethodSource("rejectedPrograms")
    void invalidUnevaluatedExpressionsAndSpecificationsAreRejected(String name,String source) throws Exception {
        Path file=temporary.resolve(name+".cpp");Files.writeString(file,source);
        var oracle=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(oracle.timedOut());assertFalse(oracle.outputExceeded());assertNotEquals(0,oracle.exitCode(),oracle::stderr);
        var api=new CompilerApi(new SourceFile(name+".cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        var parser=api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        assertFalse(parser.succeeded()&&semantic.succeeded(),"MiniC must reject: "+source);
    }
}
