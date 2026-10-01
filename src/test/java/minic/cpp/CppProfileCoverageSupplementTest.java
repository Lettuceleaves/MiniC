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

/** Supplemental API coverage; independent of the immutable unified R2 snapshot. */
@Tag("stl-contract") @Timeout(600)
final class CppProfileCoverageSupplementTest {
    @TempDir Path temporary;
    private static final CppDifferentialHarness.Limits LIMITS = new CppDifferentialHarness.Limits(
            Duration.ofSeconds(180),Duration.ofSeconds(30),2_000_000,1_048_576);

    static Stream<Arguments> programs(){return Stream.of(
            Arguments.of("algorithms.cpp","algorithms ok\n"),
            Arguments.of("storage-tools.cpp","storage tools ok\n"),
            Arguments.of("c-headers-bundle.cpp","C headers and bundle ok\n"));}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void directProfileContractsAgreeAcrossFourBuilds(String name,String expected)throws Exception {
        String source;
        try(var resource=getClass().getResourceAsStream("/cpp/profile-coverage/"+name)) {
            assertNotNull(resource,name);source=new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                LIMITS,LanguageMode.CPP17_ALGORITHM).run(name,source,"");
        var own=CppOwnLibraryReference.run(temporary,source,"",LIMITS);
        assertAll(()->assertTrue(report.passed(),report::describe),()->assertTrue(own.passed(),own::toString));
        for(var outcome:report.outcomes().values()) {
            assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe);
            assertEquals("",outcome.stderr(),report::describe);
        }
        assertEquals(expected,own.stdout().replace("\r\n","\n"),own::toString);
        assertEquals("",own.stderr(),own::toString);
    }

    static Stream<Arguments> invalid(){return Stream.of(
            Arguments.of("enable-if-false","#include <type_traits>\nstd::enable_if<false,int>::type value;int main(){return 0;}"),
            Arguments.of("forward-rvalue-as-lvalue","#include <utility>\nint main(){int& value=std::forward<int&>(3);return value;}"));}
    @ParameterizedTest(name="reject {0}") @MethodSource("invalid")
    void invalidToolContractsAreRejectedByAllBuilds(String name,String source)throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                LIMITS,LanguageMode.CPP17_ALGORITHM).compile(name,source);
        var own=CppOwnLibraryReference.compile(temporary,source,LIMITS);
        for(var outcome:report.outcomes().values())
            assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,outcome.status(),report::describe);
        assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,own.status(),own::toString);
    }
}
