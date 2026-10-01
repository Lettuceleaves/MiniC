package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Unmodified C++ algorithm-style programs, queued for the final feature gate. */
@Tag("stl-contract") @Timeout(600)
final class CppOjProgramsTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
            Arguments.of("shortest-path","5 6\n0 1 2\n0 2 7\n1 2 1\n1 3 5\n2 3 1\n3 4 3\n","0 2 3 4 7\n"),
            Arguments.of("grid-bfs","4 5\n.....\n.###.\n...#.\n.....\n","7\n"),
            Arguments.of("lis","8\n10 9 2 5 3 7 101 18\n","4\n"),
            Arguments.of("sliding-median","8 3\n1 3 -1 -3 5 3 6 7\n","1 -1 -1 3 5 6\n"),
            Arguments.of("word-ranking","8\npear apple pear plum apple apple pear kiwi\n","apple:3\npear:3\nkiwi:1\nplum:1\n"));}
    static Stream<Arguments> programsWithOptimization(){return programs().flatMap(arguments->{
        Object[] values=arguments.get();
        return Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED)
                .map(level->Arguments.of(values[0],values[1],values[2],level));
    });}
    @ParameterizedTest(name="{0} [{3}]") @MethodSource("programsWithOptimization")
    void algorithmProgramRetainsCppSource(String name,String input,String expected,OptimizationLevel level)throws Exception{
        String source;try(var resource=getClass().getResourceAsStream("/cpp/oj/"+name+".cpp")){
            assertNotNull(resource);source=new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }
        var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(120),Duration.ofSeconds(45),10_000_000,1_048_576);
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,LanguageMode.CPP17_ALGORITHM,level)
                .run(name+"-"+level,source,input);
        var own=CppOwnLibraryReference.run(temporary,source,input,limits);
        assertAll(()->assertTrue(report.passed(),report::describe),()->assertTrue(own.passed(),own::toString));
        for(var outcome:report.outcomes().values())assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe);
        assertEquals(expected,own.stdout().replace("\r\n","\n"),own::toString);
    }
}
