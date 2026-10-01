package minic.cpp;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import minic.compiler.parser.node.Expression;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppConditionalPointerTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("different-literal-extents", "int pick(const char*p){return p[0];}int main(){bool b=false;printf(\"%d\\n\",pick(b?\"long\":\"x\"));return 0;}", "120\n"),
        Arguments.of("nested-literal-extents", "int pick(const char*p){return p[0];}int main(){bool a=true;bool b=false;printf(\"%d\\n\",pick(a?(b?\"XX\":\"YY\"):(b?\"x\":\"y\")));return 0;}", "89\n"),
        Arguments.of("different-array-extents", "int main(){int a[2]={1,2};int b[3]={3,4,5};bool flag=false;auto p=flag?a:b;printf(\"%d %d\\n\",*p,(int)sizeof(p));return 0;}", "3 8\n"),
        Arguments.of("pointer-nullptr", "int read(int*p){return p?*p:0;}int main(){int x=7;int*p=&x;printf(\"%d %d\\n\",read(p?p:nullptr),read(false?nullptr:p));return 0;}", "7 7\n"),
        Arguments.of("pointer-zero", "int main(){int x=7;int*p=&x;auto a=true?p:0;auto b=false?0:p;printf(\"%d %d\\n\",*a,*b);return 0;}", "7 7\n"),
        Arguments.of("pointer-cv-void", "int main(){int x=7;int*p=&x;const void*q=p;auto r=false?p:q;printf(\"%d %d\\n\",r==q,(int)sizeof(r));return 0;}", "1 8\n"),
        Arguments.of("function-pointer-noexcept", "int a()noexcept{return 3;}int b(){return 4;}int call(int(*f)()){return f();}int main(){printf(\"%d\\n\",call(false?a:b));return 0;}", "4\n"),
        Arguments.of("prequalified-deep-pointer", "int main(){int x=7;int*p=&x;const int*q=&x;int**a=&p;const int*const*b=&q;auto r=true?a:b;printf(\"%d\\n\",**r);return 0;}", "7\n"),
        Arguments.of("same-array-reference", "int main(){bool flag=false;const char(&r)[2]=flag?\"a\":\"b\";printf(\"%d %d\\n\",r[0],(int)sizeof(r));return 0;}", "98 2\n"),
        Arguments.of("same-function-reference", "int a(){return 3;}int b(){return 4;}int main(){int(&f)()=true?a:b;printf(\"%d\\n\",f());return 0;}", "3\n"),
        Arguments.of("nullptr-literal-equality", "int main(){printf(\"%d %d\\n\",nullptr==nullptr,nullptr!=nullptr);return 0;}", "1 0\n"),
        Arguments.of("selected-arm-once", "int count;int x=7;int*get(){++count;return &x;}int main(){int*p=true?get():nullptr;printf(\"%d %d\\n\",*p,count);return 0;}", "7 1\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void preservesTheCppCommonTypeAndSelectedValue(String name,String source,String output)throws Exception{agree(temporary,name,"#include <stdio.h>\n"+source,output);}
    static Stream<Arguments> invalid(){return Stream.of(
        Arguments.of("unrelated-pointer", "int main(){int*a=0;double*b=0;auto p=true?a:b; // bad\nreturn 0;}"),
        Arguments.of("nonliteral-zero", "int main(){int*a=0;int zero=0;auto p=true?a:zero; // bad\nreturn 0;}"),
        Arguments.of("different-function-signature", "int a(){return 0;}double b(){return 0;}int main(){auto p=true?a:b; // bad\nreturn 0;}")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("invalid")
    void rejectsMissingCompositePointerTypes(String name,String source)throws Exception{reject(temporary,name,source);}
    @Test void nullptrAndLiteralZeroHaveNullptrType()throws Exception{
        // N4659 [expr.cond]/7.5 explicitly permits nullptr_t and a null pointer constant.
        // Local G++ 8.1 rejects both orderings; retain the source and normative outputs.
        // https://timsong-cpp.github.io/cppwp/n4659/expr.cond#7.5
        String source="int pick(decltype(nullptr)){return 11;}int pick(void*){return 22;}int main(){auto p=true?nullptr:0;auto q=false?0:nullptr;printf(\"%d %d %d %d\\n\",p==nullptr,q==nullptr,pick(p),pick(false?0:nullptr));return 0;}";
        var api=compiler("#include <stdio.h>\n"+source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());var result=semantic.semanticResult();
        for(var original:nodes(result.sourceProgram()))if(original instanceof Expression.ConditionalExpr)
            assertEquals(MiniType.VOID.pointerTo(),result.typeOf((Expression)result.sourceToCore().get(original)).orElseThrow());
        var report=new minic.cpp.support.CppDifferentialHarness(temporary,minic.cpp.support.CppDifferentialHarness.referenceCompiler(System.getenv()),
                minic.cpp.support.CppDifferentialHarness.Limits.defaults(),minic.compiler.LanguageMode.CPP17_ALGORITHM).run("nullptr-zero-type","#include <stdio.h>\n"+source,"");
        for(var backend:java.util.List.of(minic.cpp.support.CppDifferentialHarness.Backend.MINIC_NATIVE,minic.cpp.support.CppDifferentialHarness.Backend.MINIC_DEBUG)){
            var outcome=report.outcomes().get(backend);assertEquals(minic.cpp.support.CppDifferentialHarness.Status.OK,outcome.status(),report::describe);
            assertEquals("1 1 11 11\n",outcome.stdout().replace("\r\n","\n"),report::describe);
        }
    }
    @Test void qualificationCombinationAddsIntermediateConst()throws Exception{
        // N4659 [expr]/14 and /15 use int** and const int** as the explicit example:
        // the composite pointer is const int* const*. Local G++ 8.1 rejects this
        // source, so validate the stated language type and both MiniC engines.
        // https://timsong-cpp.github.io/cppwp/n4659/expr#15
        String source="int main(){int x=7;int*p=&x;const int*q=&x;int**a=&p;const int**b=&q;auto r=true?a:b;const int*const*t=r;printf(\"%d\\n\",**t);return 0;}";
        var api=compiler("#include <stdio.h>\n"+source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var result=semantic.semanticResult();var original=nodes(result.sourceProgram()).stream().filter(Expression.ConditionalExpr.class::isInstance).findFirst().orElseThrow();
        MiniType actual=result.typeOf((Expression)result.sourceToCore().get(original)).orElseThrow();
        MiniType constantInt=MiniType.qualified(MiniType.INT,java.util.Set.of(MiniType.TypeQualifier.CONST));
        MiniType expected=MiniType.qualified(constantInt.pointerTo(),java.util.Set.of(MiniType.TypeQualifier.CONST)).pointerTo();
        assertEquals(expected,actual);
        var report=new minic.cpp.support.CppDifferentialHarness(temporary,minic.cpp.support.CppDifferentialHarness.referenceCompiler(System.getenv()),
                minic.cpp.support.CppDifferentialHarness.Limits.defaults(),minic.compiler.LanguageMode.CPP17_ALGORITHM).run("deep-pointer-cv","#include <stdio.h>\n"+source,"");
        for(var backend:java.util.List.of(minic.cpp.support.CppDifferentialHarness.Backend.MINIC_NATIVE,minic.cpp.support.CppDifferentialHarness.Backend.MINIC_DEBUG)){
            var outcome=report.outcomes().get(backend);assertEquals(minic.cpp.support.CppDifferentialHarness.Status.OK,outcome.status(),report::describe);
            assertEquals("7\n",outcome.stdout().replace("\r\n","\n"),report::describe);
        }
    }
    @Test void arrayDecayRetainsTheConditionalSourceIdentityAndRange(){
        String source="int main(){\n bool flag=false;\n auto p=flag?\"long\":\"x\";\n return p[0]-120;\n}";
        var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());var result=semantic.semanticResult();
        var original=nodes(result.sourceProgram()).stream().filter(Expression.ConditionalExpr.class::isInstance).findFirst().orElseThrow();
        var bound=assertInstanceOf(Expression.class,result.sourceToCore().get(original));
        assertEquals(original.range(),bound.range());assertEquals(3,bound.range().startLine());
        MiniType type=result.typeOf(bound).orElseThrow();assertTrue(type.isPointer());assertTrue(type.pointee().isConstQualified());assertEquals(MiniType.CHAR,type.pointee().unqualified());
    }
}
