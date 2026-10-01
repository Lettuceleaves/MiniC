package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
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

/** Arrow follows member-only recursive operator lookup until an actual pointer is reached. */
@Tag("cpp-differential") @Timeout(90)
final class CppArrowOperatorTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs() { return Stream.of(
        Arguments.of("read-write-and-method", "struct T{int n;int add(int x){return n+x;}};struct P{T*p;T*operator->(){return p;}};int main(){T value={2};P p={&value};p->n+=3;printf(\"%d %d\",value.n,p->add(4));return 0;}", "5 9"),
        Arguments.of("const-overload", "struct T{int n;};struct P{T*p;T*operator->(){printf(\"M \");return p;}const T*operator->()const{printf(\"C \");return p;}};int main(){T value={7};P p={&value};const P c={&value};printf(\"%d \",p->n);printf(\"%d\",c->n);return 0;}", "M 7 C 7"),
        Arguments.of("recursive-reference", "struct T{int n;};struct Inner{T*p;T*operator->(){printf(\"I \");return p;}};struct Outer{Inner i;Inner&operator->(){printf(\"O \");return i;}};int main(){T v={8};Outer p={{&v}};printf(\"%d\",p->n);return 0;}", "O I 8"),
        Arguments.of("recursive-temporary-lifetime", "struct T{int n;};struct Inner{T*p;Inner(T*x):p(x){}~Inner(){printf(\"D \");}T*operator->(){printf(\"I \");return p;}};struct Outer{T*p;Inner operator->(){printf(\"O \");return Inner(p);}};int main(){T v={9};Outer p={&v};printf(\"%d \",p->n);printf(\"E\");return 0;}", "O I 9 D E"),
        Arguments.of("receiver-once", "int calls;struct T{int n;};struct P{T*p;T*operator->(){++calls;return p;}};P&get(P&p){++calls;return p;}int main(){T v={10};P p={&v};int n=get(p)->n;printf(\"%d %d\",n,calls);return 0;}", "10 2"),
        Arguments.of("unevaluated", "struct T{int n;};struct P{T*operator->(){printf(\"BAD\");return nullptr;}};int main(){P p;printf(\"%llu\",sizeof(p->n));return 0;}", "4"),
        Arguments.of("out-of-line", "namespace N{struct T{int n;};struct P{T*p;T*operator->();};}N::T*N::P::operator->(){return p;}int main(){N::T v={11};N::P p={&v};printf(\"%d\",p->n);return 0;}", "11"),
        Arguments.of("pseudo-destructor", "typedef int I;struct P{I*p;I*operator->(){printf(\"A \");return p;}};int main(){I n=1;P p={&n};p->~I();printf(\"E\");return 0;}", "A E")
    ); }
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void arrowExecutionAgreesWithCpp(String name,String body,String expected) throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,"#include <stdio.h>\n"+body,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout()));
    }
    static Stream<Arguments> invalidPrograms() { return Stream.of(
        Arguments.of("struct P{int operator->(){return 1;}};int main(){P p;return p->n;}", "CPP004", "requires a pointer", "p->n"),
        Arguments.of("struct P{void operator->(){}};int main(){P p;p->n;return 0;}", "CPP004", "requires a pointer", "p->n"),
        Arguments.of("struct P{P&operator->(){return *this;}};int main(){P p;return p->n;}", "CPP004", "Recursive operator->", "p->n"),
        Arguments.of("struct T{int n;};class P{T*operator->();};int main(){P p;return p->n;}", "CPP004", "private", "p->n"),
        Arguments.of("struct T{int n;};struct P{T*operator->();};int main(){const P p={};return p->n;}", "CPP004", "没有匹配的运算符重载", "p->n"),
        Arguments.of("struct T{int n;};struct P{const T*operator->();};int main(){P p;p->n=2;return 0;}", "SEM001", "const", "p->n=2"),
        Arguments.of("struct T;struct P{T*operator->();};int main(){P p;return p->n;}", "CPP005", "完整对象类型", "p->n"),
        Arguments.of("struct P{};P*operator->(P&p);int main(){return 0;}", "CPP004", "成员形式", "operator->"),
        Arguments.of("struct P{P*operator->(int);};int main(){return 0;}", "CPP004", "参数个数", "operator->")
    ); }
    @ParameterizedTest @MethodSource("invalidPrograms")
    void invalidArrowProgramsHaveSourceDiagnostics(String source, String code, String reason, String site) throws Exception {
        Path file=temporary.resolve("invalid.cpp");Files.writeString(file,source);
        var result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(result.timedOut());assertNotEquals(0,result.exitCode());
        var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertFalse(semantic.succeeded());assertFalse(semantic.errors().isEmpty());
        var original = new minic.compiler.SourceFile("arrow.cpp", source);
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals(code)
                && error.message().contains(reason) && original.text(error.range()).contains(site)), () -> semantic.errors().toString());
    }
}
