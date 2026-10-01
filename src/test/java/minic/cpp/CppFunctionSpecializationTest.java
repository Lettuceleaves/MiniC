package minic.cpp;

import minic.compiler.*;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
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

/** Explicit-specialization and exception-specification deduction corpus; execution is deferred. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppFunctionSpecializationTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("explicit-template-id", """
            template<class T>int pick(T value){return 1;}
            template<>int pick<int>(int value){return value+4;}
            int main(){printf("%d %d %d\\n",pick(2),pick(2.0),pick<int>(3));return 0;}
            ""","6 1 7\n"),
        Arguments.of("deduced-and-empty-template-ids", """
            template<class T>int one(T value){return 1;}
            template<>int one(int value){return 2;}
            template<class T>int two(T value){return 3;}
            template<>int two<>(double value){return 4;}
            int main(){printf("%d %d %d %d\\n",one(0),one(0.0),two(0),two(0.0));return 0;}
            ""","2 1 3 4\n"),
        Arguments.of("ordinary-overload-stays-distinct", """
            template<class T>int pick(T value){return 1;}
            template<>int pick<int>(int value){return 2;}
            int pick(int value){return 3;}
            int main(){printf("%d %d\\n",pick(0),pick<int>(0));return 0;}
            ""","3 2\n"),
        Arguments.of("primary-partial-ordering", """
            template<class T>int pick(T value){return 1;}
            template<class T>int pick(T* value){return 2;}
            template<>int pick(int* value){return 3;}
            int main(){int x=0;double y=0;printf("%d %d %d\\n",pick(&x),pick(&y),pick(0));return 0;}
            ""","3 2 1\n"),
        Arguments.of("qualified-namespace-and-body-lookup", """
            namespace N{typedef int Number;int offset=8;template<class T>int pick(T){return 1;}}
            template<>int N::pick<int>(Number value){return value+offset;}
            int main(){printf("%d %d\\n",N::pick(2),N::pick(2.0));return 0;}
            ""","10 1\n"),
        Arguments.of("prototype-before-use", """
            template<class T>int pick(T){return 1;}
            template<>int pick<int>(int);
            int use(){return pick(4);}
            template<>int pick<int>(int value){return value+2;}
            int main(){printf("%d\\n",use());return 0;}
            ""","6\n"),
        Arguments.of("recursive-specialization", """
            template<class T>T fact(T value){return value;}
            template<>int fact<int>(int value){return value<=1?1:value*fact<int>(value-1);}
            int main(){printf("%d\\n",fact(5));return 0;}
            ""","120\n"),
        Arguments.of("function-pointer-specialization", """
            template<class T>T value(T x){return x;}
            template<>int value<int>(int x){return x+7;}
            int main(){int(*p)(int)=value;int(*q)(int)=value<int>;printf("%d %d %d\\n",p(1),q(2),p==q);return 0;}
            ""","8 9 1\n"),
        Arguments.of("default-arguments-from-primary", """
            template<class T>int sum(T value,int extra=3){return value+extra;}
            template<>int sum<int>(int value,int extra){return value*extra;}
            int main(){printf("%d %d\\n",sum(4),sum(4,5));return 0;}
            ""","12 20\n"),
        Arguments.of("non-type-template-arguments", """
            template<int N>int number(){return N;}
            template<>int number<4>(){return 9;}
            int main(){printf("%d %d\\n",number<3>(),number<4>());return 0;}
            ""","3 9\n"),
        Arguments.of("pack-specialization", """
            template<class... T>int count(T... values){return sizeof...(T);}
            template<>int count<int,double>(int a,double b){return 7;}
            int main(){printf("%d %d %d\\n",count(),count(1),count(1,2.0));return 0;}
            ""","0 1 7\n"),
        Arguments.of("noexcept-parameter-deduction", """
            void safe()noexcept{}void risky(){}
            template<bool B>int kind(void(*operation)()noexcept(B)){operation();return B;}
            int main(){printf("%d %d\\n",kind(safe),kind(risky));return 0;}
            ""","1 0\n"),
        Arguments.of("noexcept-target-deduction", """
            template<bool B>int answer()noexcept(B){return B?7:3;}
            int main(){int(*safe)()noexcept=answer;int(*risky)()=answer;printf("%d %d\\n",safe(),risky());return 0;}
            ""","7 3\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void specializationKeepsPrimaryIdentity(String name,String source,String expected)throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "template<>int missing<int>(int x){return x;}int main(){return 0;}",
        "template<class T>T pick(T);template<>double pick<int>(int value){return value;}int main(){return 0;}",
        "template<class T>int pick(T){return 1;}template<>int pick<int>(int){return 2;}template<>int pick<int>(int){return 3;}int main(){return 0;}",
        "template<class T>int pick(T){return 1;}int before=pick(1);template<>int pick<int>(int){return 2;}int main(){return before;}",
        "template<class T>int pick(T,int=2){return 1;}template<>int pick<int>(int,int=3){return 2;}int main(){return 0;}",
        "template<class T>int pick(T){return 1;}template<>int pick<int>(int){return unknown;}int main(){return 0;}",
        "template<class T>int pick(T)noexcept{return 1;}template<>int pick<int>(int)noexcept(false){return 2;}int main(){return 0;}",
        "template<class T>int pick(T){return 1;}template<>int pick<int>(int)=delete;int main(){return pick(1);}",
        "template<class T>int pick(T){return 1;}namespace Other{template<>int pick<int>(int){return 2;}}int main(){return 0;}"
    })
    void rejectsIllFormedSpecializations(String source)throws Exception {
        Path file=temporary.resolve("invalid.cpp");Files.writeString(file,source);
        var reference=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()).toString(),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(reference.timedOut());assertFalse(reference.outputExceeded());assertNotEquals(0,reference.exitCode());
        var api=new CompilerApi(new SourceFile("invalid-specialization.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(minic.compiler.semantic.SemanticAnalyzer.class::isInstance).findFirst().orElseThrow();api.runThrough(semantic);
        assertTrue(api.stages().stream().anyMatch(stage->!stage.errors().isEmpty()));
    }
    @Test void templateIdAndSourceRangesAreRetained(){
        String text="template<int N>int answer();template<>int answer<7>()noexcept(false){return 8;}";
        var source=new SourceFile("specialization.cpp",text);var lexer=new Lexer(source,LanguageMode.CPP17_ALGORITHM);var tokens=lexer.lex();
        var parser=new Parser(tokens.tokens(),LanguageMode.CPP17_ALGORITHM,true);parser.parse();assertTrue(parser.succeeded(),()->parser.errors().toString());
        var node=assertInstanceOf(FunctionTemplateDecl.class,parser.result().program().declarations().getLast());
        assertTrue(node.specialization());assertEquals(1,node.specializationArguments().size());assertTrue(node.function().exceptionSpecification().specified());
        assertEquals("template<>int answer<7>()noexcept(false){return 8;}",source.text(node.range()));assertNotNull(AstChildren.firstCppSyntax(node));
    }
}
