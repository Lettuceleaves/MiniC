package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(300)
final class CppLiveIntegralBulkExplicitTest {
    @TempDir Path temporary;
    @ParameterizedTest @EnumSource(OptimizationLevel.class)
    void originalIteratorTemplateArgumentsRemainCompatible(OptimizationLevel level)throws Exception {
        String source="""
                #include <algorithm>
                #include <stdio.h>
                int main(){
                    int a[4]={1,2,3,4};int b[4]={};
                    if(std::copy<int*,int*>(a,a+4,b)!=b+4)return 1;
                    if(std::copy_backward<int*,int*>(b,b+4,a+4)!=a)return 2;
                    if(std::move<int*,int*>(a,a+4,b)!=b+4)return 3;
                    if(std::move_backward<int*,int*>(b,b+4,a+4)!=a)return 4;
                    if(std::copy<const int*,int*>(a,a+4,b)!=b+4)return 5;
                    for(int i=0;i<4;++i)printf("%d %d\\n",a[i],b[i]);
                    return 0;
                }
                """;
        String expected="1 1\n2 2\n3 3\n4 4\n";
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppLiveIntegralBulkTest.LIMITS,LanguageMode.CPP17_ALGORITHM,level).run("explicit-bulk-"+level,source,"");
        assertTrue(report.passed(),report::describe);
        for(var outcome:report.outcomes().values())assertEquals(expected,CppLiveIntegralBulkTest.normalize(outcome.stdout()));
        var own=CppOwnLibraryReference.run(temporary,source,"",CppLiveIntegralBulkTest.LIMITS);
        assertTrue(own.passed(),own::toString);
        assertEquals(expected,CppLiveIntegralBulkTest.normalize(own.stdout()));
    }
}
