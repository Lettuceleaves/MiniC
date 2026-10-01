package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
final class CppDecltypeIncompleteTest {
    @TempDir Path temporary;
    @ParameterizedTest @ValueSource(strings={
        "struct Box;Box make();typedef decltype(make()) Result;",
        "struct Box;Box make();typedef decltype((make())) Result;",
        "struct Box{~Box()=delete;};Box make();typedef decltype(make()) Result;",
        "struct Box;struct Factory{Box make();};typedef decltype(((Factory*)0)->make()) Result;"
    })
    void decltypeDoesNotMaterializeItsOutermostCallResult(String declarations)throws Exception{
        var report=harness().run("decltype-unmaterialized",declarations+"int main(){Result*p=nullptr;return p==nullptr?0:1;}","");
        assertTrue(report.passed(),report::describe);
    }
    @ParameterizedTest @ValueSource(strings={
        "struct Box;Box make();int main(){return sizeof(make());}",
        "struct Box;Box make();int consume(Box);typedef decltype(consume(make())) Result;int main(){return 0;}",
        "struct Box;Box make();typedef decltype(make().field) Result;int main(){return 0;}"
    })
    void nestedObjectUseStillRequiresCompleteness(String source)throws Exception{
        var report=harness().compile("decltype-nested-completeness",source);
        for(var result:report.outcomes().values())assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,result.status(),report::describe);
    }
    private CppDifferentialHarness harness(){return new CppDifferentialHarness(temporary,
            CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM);}
}
