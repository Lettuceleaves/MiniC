package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Windows LLP64 source cases exercise the original, unoptimized debug interpreter. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppUnsigned64DebugTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("unsigned-division-and-remainder", """
                        #include <stdio.h>
                        int main(){unsigned long long maximum=0xffffffffffffffffULL;
                            unsigned long long high=0x8000000000000000ULL;
                            printf("%llu %llu %llu %llu\\n",maximum/2ULL,maximum%2ULL,maximum/high,maximum%high);return 0;}
                        """, "9223372036854775807 1 1 9223372036854775807\n"),
                Arguments.of("unsigned-relational-operators", """
                        #include <stdio.h>
                        int main(){unsigned long long maximum=0xffffffffffffffffULL;
                            unsigned long long high=0x8000000000000000ULL;unsigned long long zero=0ULL;
                            printf("%d %d %d %d %d %d %d\\n",maximum>zero,high>maximum,maximum>=maximum,
                                zero<high,high<=maximum,maximum==maximum,maximum!=zero);return 0;}
                        """, "1 0 1 1 1 1 1\n"),
                Arguments.of("unsigned-logical-right-shift", """
                        #include <stdio.h>
                        int main(){unsigned long long maximum=0xffffffffffffffffULL;
                            unsigned long long high=0x8000000000000000ULL;
                            printf("%llu %llu %llu\\n",maximum>>1,maximum>>63,high>>63);return 0;}
                        """, "9223372036854775807 1 1\n"),
                Arguments.of("signed-negative-controls", """
                        #include <stdio.h>
                        int main(){long narrow=-9L;long long wide=-9LL;
                            printf("%ld %ld %lld %lld %d %d\\n",narrow/2L,narrow%2L,wide/2LL,wide%2LL,
                                narrow<0L,wide>=0LL);return 0;}
                        """, "-4 -1 -4 -1 1 0\n"),
                Arguments.of("llp64-promotion-and-width", """
                        #include <stdio.h>
                        int main(){unsigned long narrow=0xffffffffUL;long negative=-1L;
                            long long wide=-1LL;unsigned long long one=1ULL;
                            printf("%lu %lu %d %d %d %llu\\n",narrow/2UL,narrow>>31,wide<1UL,
                                negative<one,narrow==0xffffffffULL,one+narrow);return 0;}
                        """, "2147483647 1 1 0 1 4294967296\n")
        );
    }

    @ParameterizedTest(name = "{0}") @MethodSource("programs")
    void debugUsesTheOperandWidthAndSignedness(String name, String source, String expected) throws Exception {
        var report = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(),
                LanguageMode.CPP17_ALGORITHM).run(name, source, "");
        assertTrue(report.passed(), report::describe);
        for (var outcome : report.outcomes().values())
            assertEquals(expected, outcome.stdout().replace("\r\n", "\n"), report::describe);
    }
}
