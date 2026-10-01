package minic.cpp;
import minic.compiler.*;
import minic.compiler.semantic.SemanticAnalyzer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;
@Timeout(90)
final class CppMainFallthroughTest {
    @TempDir Path temporary;
    @Test void globalMainFallsThroughAfterLocalCleanup() throws Exception {
        agree(temporary,"main-fallthrough", """
                #include <stdio.h>
                struct Guard {~Guard(){printf("cleanup\\n");}};
                int main(){Guard guard;printf("body\\n");}
                """, "body\ncleanup\n");
    }
    @Test void explicitReturnKeepsItsValueAndControlFlow() throws Exception {
        agree(temporary,"main-explicit-return", """
                #include <stdio.h>
                int value=0;int result(){printf("return\\n");return value;}
                int main(){return result();printf("unreachable\\n");}
                """, "return\n");
    }
    @Test void libraryAdaptorProgramFallsThroughNormally() throws Exception {
        String source;
        try(var input=getClass().getResourceAsStream("/cpp/library-containers/adaptors.cpp")) {
            assertNotNull(input);source=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        agree(temporary,"library-adaptors-main-fallthrough",source,"9876543210 123456789 123456789\n");
    }
    @Test void ordinaryAndNamespaceFunctionsStillRequireAValue() {
        for(String source:new String[]{"int f(){} int main(){return 0;}","namespace N{int main(){}} int main(){return 0;}"}) {
            var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
            assertFalse(semantic.succeeded(),source);
        }
    }
    @Test void cModeRetainsItsExistingFallthroughDiagnostic() {
        var api=new CompilerApi(new SourceFile("main.c","int main(){}"));
        var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertFalse(semantic.succeeded());
    }
}
