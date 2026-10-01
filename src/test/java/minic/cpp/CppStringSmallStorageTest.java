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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Candidate O12 acceptance sources; run only after the optimization start gate. */
@Tag("stl-contract") @Timeout(600) @Execution(ExecutionMode.SAME_THREAD)
final class CppStringSmallStorageTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){
        return Stream.of("constructors","alias-and-terminator","move-and-swap","capacity-and-modifiers","object-lifetime","union-prerequisite")
            .flatMap(name->Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED)
                .map(level->Arguments.of(name,level)));
    }
    @ParameterizedTest(name="{0} [{1}]") @MethodSource("programs")
    void storageSemanticsAgreeAcrossSourceDebugAndNativeLibraries(String name,OptimizationLevel level)throws Exception {
        String source;try(var input=getClass().getResourceAsStream("/cpp/string-small-storage/"+name+".cpp")){
            assertNotNull(input,name);source=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(120),Duration.ofSeconds(45),3_000_000,1_048_576);
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,
            LanguageMode.CPP17_ALGORITHM,level).run(name+"-"+level,source,"");
        var own=CppOwnLibraryReference.run(temporary,source,"",limits);
        assertAll(()->assertTrue(report.passed(),report::describe),()->assertTrue(own.passed(),own::toString));
        var reference=report.outcomes().get(CppDifferentialHarness.Backend.GXX);
        assertEquals(reference.stdout().replace("\r\n","\n"),own.stdout().replace("\r\n","\n"),own::toString);
        assertEquals("",own.stderr(),own::toString);
    }
}
