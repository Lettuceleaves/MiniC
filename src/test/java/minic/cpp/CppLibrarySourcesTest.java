package minic.cpp;

import minic.compiler.LanguageMode;
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

/** Full-pipeline library cases queued for the unified functional gate. */
@Tag("stl-contract") @Timeout(600)
final class CppLibrarySourcesTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("library-containers/ordered.cpp",""),
        Arguments.of("library-containers/proxy-bits.cpp",""),
        Arguments.of("library-containers/adaptors.cpp",""),
        Arguments.of("library-containers/shuffle.cpp",""),
        Arguments.of("library-containers/streams.cpp","12 hello remaining line\nZ\n"),
        Arguments.of("library-review/stream-state.cpp","a \n42 true   "),
        Arguments.of("library-review/tree-copy-erase.cpp",""),
        Arguments.of("library-review/vector-access-and-bits.cpp",""),
        Arguments.of("library-review/stream-floating-format.cpp",""),
        Arguments.of("library-review/aligned-containers.cpp",""),
        Arguments.of("library-review/bitset-stream-npos.cpp","x 101q 11"),
        Arguments.of("library-review/equal-pair-hint.cpp",""),
        Arguments.of("library-sequences/deque-modifiers.cpp",""),
        Arguments.of("library-sequences/deque-segments.cpp",""),
        Arguments.of("library-sequences/string-find-convert.cpp",""),
        Arguments.of("library-sequences/string-storage.cpp",""),
        Arguments.of("strtof-runtime/string-stof.cpp","")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void libraryProgramsAgree(String name,String input)throws Exception{
        String source;
        try(var resource=getClass().getResourceAsStream("/cpp/"+name)){
            assertNotNull(resource,name);source=new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }
        var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(90),Duration.ofSeconds(30),2_000_000,1_048_576);
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,
                LanguageMode.CPP17_ALGORITHM).run(name,source,input);
        var own=CppOwnLibraryReference.run(temporary,source,input,limits);
        assertAll(()->assertTrue(report.passed(),report::describe),()->assertTrue(own.passed(),own::toString));
        var reference=report.outcomes().get(CppDifferentialHarness.Backend.GXX);
        assertEquals(reference.stdout().replace("\r\n","\n"),own.stdout().replace("\r\n","\n"),own::toString);
        assertEquals(reference.stderr().replace("\r\n","\n"),own.stderr().replace("\r\n","\n"),own::toString);
    }
    @org.junit.jupiter.api.Test
    void constPairAssignmentIsRejectedByBothLibraries()throws Exception{
        String source;
        try(var resource=getClass().getResourceAsStream("/cpp/library-review/negative/pair-const-assignment.cpp")){
            assertNotNull(resource);source=new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }
        var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(90),Duration.ofSeconds(30),2_000_000,1_048_576);
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,
                LanguageMode.CPP17_ALGORITHM).run("const-pair-assignment",source,"");
        var own=CppOwnLibraryReference.compile(temporary,source,limits);
        for(var outcome:report.outcomes().values())
            assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,outcome.status(),report::describe);
        assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,own.status(),own::toString);
    }
}
