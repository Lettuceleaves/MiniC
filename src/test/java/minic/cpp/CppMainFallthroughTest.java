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

    @Test void generatedFallthroughReturnDoesNotAppearAsASourceAction() {
        for (String source : new String[]{"int main(){int value=1;}",
                "int main(){int value=1;if(value)return 0;}",
                "int main(){return 0;}", "int main(){if(1)return 0;else return 1;}"}) {
            var api=compiler(source);
            var semantic=stage(api,SemanticAnalyzer.class);
            api.setResultRecording(semantic,true);
            api.runThrough(semantic);
            assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
            var originals=java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<minic.compiler.parser.node.AstNode,Boolean>());
            originals.addAll(nodes(semantic.semanticResult().sourceProgram()));
            semantic.stepResults().stream()
                    .flatMap(step -> step.contextAs(minic.compiler.semantic.SemanticResult.class).stream())
                    .flatMap(context -> context.actionOptional().stream())
                    .filter(action -> action.astNode() instanceof minic.compiler.parser.node.AstNode)
                    .forEach(action -> assertTrue(originals.contains(action.astNode()),
                            () -> "Generated node exposed as a source action: "+action.astNode()));
        }
    }
}
