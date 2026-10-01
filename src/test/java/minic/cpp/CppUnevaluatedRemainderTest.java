package minic.cpp;

import minic.compiler.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
final class CppUnevaluatedRemainderTest {
    @TempDir Path temporary;
    @ParameterizedTest @ValueSource(strings={
        "return noexcept(1%1.5);", "return sizeof(1.0%2);", "decltype(1%2.0) x;return 0;",
        "return 3%2.5;", "double value=2;value%=1;return 0;"
    })
    void remainderRequiresIntegralOperandsEvenWhenUnevaluated(String body) throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).compile("invalid-remainder","int main(){"+body+"}");
        for(var outcome:report.outcomes().values())
            assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,outcome.status(),report::describe);
    }
    @Test void cModeKeepsTheSameIntegralRemainderConstraint() {
        var api=new CompilerApi(new SourceFile("invalid.c","int main(){return 3%1.5;}"));
        var semantic=api.stages().stream().filter(SemanticAnalyzer.class::isInstance).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertFalse(semantic.succeeded());
    }
}
