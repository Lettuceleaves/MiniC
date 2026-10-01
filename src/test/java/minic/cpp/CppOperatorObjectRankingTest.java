package minic.cpp;

import minic.compiler.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.cpp.CppOverloadResolver;
import minic.compiler.semantic.cpp.CppValueCategory;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** C++17 [over.match.funcs]/4-5, [over.ics.ref]/1 and [over.ics.rank]/3.2.3. */
@Timeout(90)
final class CppOperatorObjectRankingTest {
    @TempDir Path temporary;
    private static final MiniType VALUE=MiniType.struct("::Value");
    private static MiniType constant(MiniType type){return MiniType.qualified(type,Set.of(MiniType.TypeQualifier.CONST));}
    static Stream<Arguments> rankings(){return Stream.of(
        Arguments.of("const receiver versus const reference",VALUE,CppValueCategory.LVALUE,constant(VALUE),constant(VALUE).referenceTo(),MiniType.LONG_LONG,MiniType.INT,MiniType.INT,CppOverloadResolver.Status.SELECTED,"free"),
        Arguments.of("other argument favors member",VALUE,CppValueCategory.LVALUE,constant(VALUE),constant(VALUE).referenceTo(),MiniType.LONG_LONG,MiniType.INT,MiniType.LONG_LONG,CppOverloadResolver.Status.SELECTED,"member"),
        Arguments.of("const object equal binding",constant(VALUE),CppValueCategory.LVALUE,constant(VALUE),constant(VALUE).referenceTo(),MiniType.LONG_LONG,MiniType.INT,MiniType.LONG_LONG,CppOverloadResolver.Status.SELECTED,"member"),
        Arguments.of("value and implicit reference tie",VALUE,CppValueCategory.LVALUE,constant(VALUE),VALUE,MiniType.INT,MiniType.INT,MiniType.INT,CppOverloadResolver.Status.AMBIGUOUS,null),
        Arguments.of("opposing receiver and argument wins",VALUE,CppValueCategory.LVALUE,VALUE,constant(VALUE).referenceTo(),MiniType.LONG_LONG,MiniType.INT,MiniType.INT,CppOverloadResolver.Status.AMBIGUOUS,null),
        Arguments.of("unqualified receiver not worse than rvalue reference",VALUE,CppValueCategory.PRVALUE,VALUE,VALUE.referenceTo(MiniType.ReferenceKind.RVALUE),MiniType.INT,MiniType.INT,MiniType.INT,CppOverloadResolver.Status.AMBIGUOUS,null)
    );}
    @ParameterizedTest(name="{0}") @MethodSource("rankings")
    void ranksImplicitObjectLikeReferenceBinding(String name,MiniType source,CppValueCategory category,MiniType owner,MiniType firstFree,
            MiniType memberArgument,MiniType freeArgument,MiniType operand,CppOverloadResolver.Status expected,String winner) {
        var member=new CppOverloadResolver.Candidate<>("member",List.of(memberArgument),false,owner);
        var free=new CppOverloadResolver.Candidate<>("free",List.of(firstFree,freeArgument),false);
        var arguments=List.of(new CppOverloadResolver.Argument(source,category,false),new CppOverloadResolver.Argument(operand,CppValueCategory.PRVALUE,false));
        for(var candidates:List.of(List.of(member,free),List.of(free,member))){
            var result=CppOverloadResolver.resolveOperators(candidates,arguments);
            assertEquals(expected,result.status(),name);
            if(winner!=null)assertEquals(winner,result.winner().identity(),name);
        }
    }

    @Test void originalMemberFreeSelectionUsesTheSameConstBinding()throws Exception{
        String source="""
            #include <stdio.h>
            struct Value {int number;int operator+(long long amount)const{return number+(int)amount+100;}};
            int operator+(const Value& value,int amount){return value.number+amount+200;}
            int main(){Value v={3};printf("%d %d\\n",v+2,v+2LL);return 0;}
            """;
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run("original-member-free",source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals("205 105\n",report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }

    @ParameterizedTest @ValueSource(strings={
        "struct Value{int operator+(long long){return 1;}};int operator+(const Value&,int){return 2;}int main(){Value v={};return v+2;}",
        "struct Value{int operator+(int)const{return 1;}};int operator+(Value,int){return 2;}int main(){Value v={};return v+2;}",
        "struct Value{int operator+(int){return 1;}};int operator+(Value&&,int){return 2;}int main(){return Value{}+2;}"
    }) void conflictingOrIndistinguishableConversionsStayAmbiguous(String source)throws Exception{
        Path input=temporary.resolve("ambiguous.cpp");Files.writeString(input,source);
        var reference=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()).toString(),"-std=c++17","-pedantic-errors","-fsyntax-only",input.toString()),temporary,"",Duration.ofSeconds(20),64*1024);
        assertFalse(reference.timedOut());assertNotEquals(0,reference.exitCode(),reference.stderr());
        var api=new CompilerApi(new SourceFile("ambiguous.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP004")&&error.message().contains("operator+")),semantic.errors()::toString);
    }
}
