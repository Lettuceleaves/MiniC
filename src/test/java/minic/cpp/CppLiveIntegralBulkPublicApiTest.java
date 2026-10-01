package minic.cpp;
import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(300)
final class CppLiveIntegralBulkPublicApiTest {
    @TempDir Path temporary;
    @ParameterizedTest @ValueSource(strings={"copy","copy_backward","move","move_backward"})
    void takingAnExplicitIteratorSpecializationAddressStillHasOneCandidate(String algorithm)throws Exception {
        boolean backward=algorithm.endsWith("backward");
        String source="#include <algorithm>\n#include <stdio.h>\nint main(){int a[2]={11,19};int b[2]={};"
                +"auto f=&std::"+algorithm+"<int*,int*>;int* result=f(a,a+2,b"+(backward?"+2":"")+");"
                +"printf(\"%d %d %lld\\n\",b[0],b[1],(long long)(result-b));return 0;}";
        String expected="11 19 "+(backward?0:2)+"\n";
        var report=harness().run("address-"+algorithm,source,"");
        assertTrue(report.passed(),report::describe);
        for(var outcome:report.outcomes().values())assertEquals(expected,CppLiveIntegralBulkTest.normalize(outcome.stdout()));
        var own=CppOwnLibraryReference.run(temporary,source,"",CppLiveIntegralBulkTest.LIMITS);
        assertTrue(own.passed(),own::toString);assertEquals(expected,CppLiveIntegralBulkTest.normalize(own.stdout()));
    }
    @ParameterizedTest @ValueSource(strings={"copy","copy_backward","move","move_backward"})
    void implementationDispatchDoesNotAddNewPublicTemplateSignatures(String algorithm)throws Exception {
        String source="#include <algorithm>\nint main(){int a[2]={11,19};int b[2]={};std::"+algorithm
                +"<int,int>(a,a+2,b"+(algorithm.endsWith("backward")?"+2":"")+");}";
        var report=harness().compile("invalid-template-"+algorithm,source);
        for(var outcome:report.outcomes().values())assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,outcome.status(),report::describe);
        var own=CppOwnLibraryReference.compile(temporary,source,CppLiveIntegralBulkTest.LIMITS);
        assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,own.status(),own::toString);
    }
    private CppDifferentialHarness harness(){return new CppDifferentialHarness(temporary,
            CppDifferentialHarness.referenceCompiler(System.getenv()),CppLiveIntegralBulkTest.LIMITS,LanguageMode.CPP17_ALGORITHM);}
}
