package minic.cpp;

import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.CppDifferentialHarness;
import minic.compiler.LanguageMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppTemplateInstantiationRepairTest {
    @TempDir Path temporary;
    @ParameterizedTest @ValueSource(strings={
        "template<class T,T V>struct Constant{static constexpr T value=V;}; int main(){return Constant<bool,false>::value;}",
        "int answer(){return 7;} template<class T>int use(){int x=answer();return x;} int main(){return use<int>();}",
        "template<class T>struct Box{template<int V>int get(){int x=V;return x;}};int main(){Box<int>b;return b.get<4>();}",
        "template<class T>struct Extent{static constexpr int value=0;}; template<class T,int N>struct Extent<T[N]>{static constexpr int value=N;};int main(){return Extent<int[3]>::value;}"
    })
    void sourceInstantiationKeepsProjectionAndDeducesArrayBounds(String source) {
        var api=CppReferenceTest.compiler(source);
        var semantic=CppReferenceTest.stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(semantic.errors().isEmpty(),()->semantic.errors().toString());
    }
    @Test void zeroNonTypeArgumentDoesNotBecomeANullPointerLiteral() {
        var api=CppReferenceTest.compiler("template<int N>int* pointer(){return N;} int main(){return pointer<0>()==0;}");
        var semantic=CppReferenceTest.stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertFalse(semantic.errors().isEmpty(),"A non-type template parameter is not a C++17 integer literal");
    }
    @Test void substitutedInitializersAndArraySpecializationsExecute() throws Exception {
        String source="""
            #include <stdio.h>
            template<class T,T V>struct Constant{static constexpr T value=V;};
            template<class T>struct Extent{static constexpr int value=0;};
            template<class T,int N>struct Extent<T[N]>{static constexpr int value=N;};
            int calls;int answer(){++calls;return 7;}
            template<class T>int use(){int x=answer();return x;}
            int main(){int x=use<int>();printf("%d %d %d %d %d\\n",Constant<bool,false>::value,Constant<bool,true>::value,Extent<int[3]>::value,x,calls);return 0;}
            """;
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run("template-instantiation-repair",source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals("0 1 3 7 1\n",report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
}
