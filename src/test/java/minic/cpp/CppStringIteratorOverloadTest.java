package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Raw pointer iterators must not make valid integer-zero index calls ambiguous. */
@Tag("stl-contract") @Timeout(180) @Execution(ExecutionMode.SAME_THREAD)
final class CppStringIteratorOverloadTest {
    @TempDir Path temporary;
    private static final CppDifferentialHarness.Limits LIMITS = new CppDifferentialHarness.Limits(
        Duration.ofSeconds(120),Duration.ofSeconds(30),3_000_000,1_048_576);
    static Stream<Arguments> programs() {
        return Stream.of("zero-indices","iterator-positions","converted-positions")
            .flatMap(name->Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED)
                .map(level->Arguments.of(name,level)));
    }
    @ParameterizedTest(name="{0} [{1}]") @MethodSource("programs")
    void indexAndIteratorOverloadsPreserveTheirValidCalls(String name,OptimizationLevel level)throws Exception {
        String source;
        try(var input=getClass().getResourceAsStream("/cpp/string-iterator-overloads/"+name+".cpp")) {
            assertNotNull(input,name);source=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),LIMITS,
            LanguageMode.CPP17_ALGORITHM,level).run(name,source,"");
        var own=CppOwnLibraryReference.run(temporary,source,"",LIMITS);
        assertAll(()->assertTrue(report.passed(),report::describe),()->assertTrue(own.passed(),own::toString));
        assertEquals(name+" ok\n",report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
        assertEquals(name+" ok\n",own.stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={"s.erase((int*)0);","s.erase(1,s.end());","s.insert(1,'q');","s.insert(0,'q');","s.replace(s.begin(),0,\"x\");","s.replace(s.begin(),1,\"x\");"})
    void unrelatedPointersAndIntegerIteratorPositionsRemainRejected(String call)throws Exception {
        String source="#include <string>\nint main(){std::string s(\"abc\");"+call+"}\n";
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),LIMITS,
            LanguageMode.CPP17_ALGORITHM).compile("invalid-position",source);
        var own=CppOwnLibraryReference.compile(temporary,source,LIMITS);
        for(var outcome:report.outcomes().values())assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,outcome.status(),report::describe);
        assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,own.status(),own::toString);
    }
}
