package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugMathEndToEndParityTest {
    @Test
    void executesThePublishedMathSurfaceNativelyAndInTheDebugger() {
        String source = """
                #include "math.mh"
                #include "errno.mh"
                int main() {
                    if (acos(1.0) != 0.0 || asin(0.0) != 0.0 || atan(0.0) != 0.0) return 1;
                    if (atan2(0.0, 1.0) != 0.0 || ceil(1.25) != 2.0 || floor(1.75) != 1.0) return 2;
                    if (cos(0.0) != 1.0 || cosh(0.0) != 1.0 || exp(0.0) != 1.0) return 3;
                    if (fabs(0.0 - 3.5) != 3.5 || fmod(5.0, 2.0) != 1.0) return 4;
                    int exponent = 0;
                    if (frexp(8.0, &exponent) != 0.5 || exponent != 4 || ldexp(0.5, 4) != 8.0) return 5;
                    if (log(1.0) != 0.0 || log10(1.0) != 0.0) return 6;
                    double integer_part = 0.0;
                    if (modf(3.5, &integer_part) != 0.5 || integer_part != 3.0) return 7;
                    if (pow(2.0, 3.0) != 8.0 || sin(0.0) != 0.0 || sinh(0.0) != 0.0) return 8;
                    if (sqrt(9.0) != 3.0 || tan(0.0) != 0.0 || tanh(0.0) != 0.0) return 9;

                    if (acosf(1.0f) != 0.0f || asinf(0.0f) != 0.0f || atanf(0.0f) != 0.0f) return 10;
                    if (atan2f(0.0f, 1.0f) != 0.0f || ceilf(1.25f) != 2.0f
                            || floorf(1.75f) != 1.0f) return 11;
                    if (cosf(0.0f) != 1.0f || coshf(0.0f) != 1.0f || expf(0.0f) != 1.0f) return 12;
                    if (fmodf(5.0f, 2.0f) != 1.0f || logf(1.0f) != 0.0f
                            || log10f(1.0f) != 0.0f) return 13;
                    float float_integer_part = 0.0f;
                    if (modff(3.5f, &float_integer_part) != 0.5f || float_integer_part != 3.0f) return 14;
                    if (powf(2.0f, 3.0f) != 8.0f || sinf(0.0f) != 0.0f
                            || sinhf(0.0f) != 0.0f) return 15;
                    if (sqrtf(9.0f) != 3.0f || tanf(0.0f) != 0.0f || tanhf(0.0f) != 0.0f) return 16;

                    double negative_zero = ceil(0.0 - 0.25);
                    double negative_one = 0.0 - 1.0;
                    if (!(atan2(negative_zero, negative_one) < 0.0)) return 17;
                    return 0;
                }
                """;
        runParity("math-debug-native-parity.mc", source);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorProfileCases")
    void matchesTheNativeDomainAndRangeProfile(String name, String body) {
        runParity("math-error-parity-" + name + ".mc", """
                #include "math.mh"
                #include "errno.mh"
                #include "string.mh"
                int main() {
                """ + body + "\n}\n");
    }

    private void runParity(String name, String source) {
        SourceFile sourceFile = new SourceFile(name, source);

        CompileObservationSession nativeSession = CompileObservationSession.fromSource(sourceFile);
        nativeSession.compilerApi().run();
        assertTrue(nativeSession.linker().succeeded(), () -> "stage=" + nativeSession.currentStage()
                + ", preprocess=" + nativeSession.preprocessor().diagnostics()
                + ", lexer=" + nativeSession.lexer().diagnostics()
                + ", parser=" + nativeSession.parser().diagnostics()
                + ", semantic=" + nativeSession.semanticAnalyzer().diagnostics()
                + ", obj=" + nativeSession.objBuilder().diagnostics()
                + ", link=" + nativeSession.linker().diagnostics());
        var nativeExecution = new ExecutableRunner().run(
                sourceFile,
                nativeSession.linker().result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(nativeExecution.diagnostics().isEmpty(), () -> nativeExecution.diagnostics().toString());

        DebugApi debug = new DebugApi(sourceFile);
        int remaining = 100_000;
        while (debug.canNext() && remaining-- > 0) {
            debug.next();
        }
        if (debug.canNext()) {
            fail("debugger did not complete within the step budget");
        }

        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop().error());
        assertEquals(nativeExecution.exitCode(), (int) debug.current().runtime().returnValue().integer());
        assertEquals(nativeExecution.stdout().replace("\r\n", "\n"), debug.current().runtime().stdout());
        assertEquals(0, nativeExecution.exitCode());
    }

    private static Stream<Arguments> errorProfileCases() {
        return Stream.of(
                Arguments.of("sqrt-domain", """
                        errno = 0;
                        double negative = 0.0 - 1.0;
                        double value = sqrt(negative);
                        unsigned long long bits = 0;
                        memcpy(&bits, &value, 8);
                        return errno == EDOM
                            && (bits & 0x7ff0000000000000ULL) == 0x7ff0000000000000ULL
                            && (bits & 0x000fffffffffffffULL) != 0 ? 0 : 1;
                        """),
                Arguments.of("log-pole", """
                        errno = 0;
                        double value = log(0.0);
                        double negative_limit = 0.0 - 1.0e308;
                        return errno == ERANGE && value < negative_limit ? 0 : 1;
                        """),
                Arguments.of("exp-overflow", """
                        errno = 0;
                        double value = exp(1000.0);
                        return errno == ERANGE && value > 1.0e308 ? 0 : 1;
                        """),
                Arguments.of("exp-underflow", """
                        errno = 0;
                        double negative = 0.0 - 1000.0;
                        double value = exp(negative);
                        return errno == 0 && value == 0.0 ? 0 : 1;
                        """)
        );
    }
}
