package minic.compiler.link;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class MathLibraryNativeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("ordinaryCases")
    void linksAndRunsEverySupportedMathFunction(NativeCase testCase) {
        runNativeCase(testCase);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("specialAndErrorCases")
    void preservesSpecialValuesAndTheMsvcrtErrnoProfile(NativeCase testCase) {
        runNativeCase(testCase);
    }

    private static void runNativeCase(NativeCase testCase) {
        SourceFile sourceFile = new SourceFile("stdlib-native-math-" + testCase.name() + ".mc", testCase.source());
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);

        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> testCase.name() + ": pre="
                + session.preprocessor().diagnostics() + ", lex=" + session.lexer().diagnostics()
                + ", parse=" + session.parser().diagnostics() + ", semantic="
                + session.semanticAnalyzer().diagnostics() + ", obj=" + session.objBuilder().diagnostics()
                + ", link=" + session.linker().diagnostics());
        Map<String, Set<String>> imports = PeImportIntegrationTest.readImports(
                session.linker().peImage().orElseThrow().bytes()
        );
        assertEquals(testCase.msvcrtExports(), imports.get("msvcrt.dll"), testCase.name());
        assertEquals(Set.of("ExitProcess"), imports.get("KERNEL32.dll"), testCase.name());
        assertEquals(Set.of("msvcrt.dll", "KERNEL32.dll"), imports.keySet(), testCase.name());

        var artifact = session.linker().result().executableArtifactOptional().orElseThrow();
        var execution = new ExecutableRunner().run(sourceFile, artifact);
        assertTrue(execution.diagnostics().isEmpty(), () -> testCase.name() + ": " + execution.diagnostics());
        assertEquals("", execution.stderr(), testCase.name());
        assertEquals(0, execution.exitCode(), testCase.name());
    }

    static Stream<Arguments> ordinaryCases() {
        return Stream.of(
                mathCase("acos", "return acos(1.0) == 0.0 ? 0 : 1;", "acos"),
                mathCase("asin", "return asin(0.0) == 0.0 ? 0 : 1;", "asin"),
                mathCase("atan", "return atan(0.0) == 0.0 ? 0 : 1;", "atan"),
                mathCase("atan2", "return atan2(0.0, 1.0) == 0.0 ? 0 : 1;", "atan2"),
                mathCase("ceil", "return ceil(1.25) == 2.0 ? 0 : 1;", "ceil"),
                mathCase("cos", "return cos(0.0) == 1.0 ? 0 : 1;", "cos"),
                mathCase("cosh", "return cosh(0.0) == 1.0 ? 0 : 1;", "cosh"),
                mathCase("exp", "return exp(0.0) == 1.0 ? 0 : 1;", "exp"),
                mathCase("fabs", """
                        double negative = 0.0 - 3.5;
                        return fabs(negative) == 3.5 ? 0 : 1;
                        """, "fabs"),
                mathCase("floor", "return floor(1.75) == 1.0 ? 0 : 1;", "floor"),
                mathCase("fmod", "return fmod(5.0, 2.0) == 1.0 ? 0 : 1;", "fmod"),
                mathCase("frexp", """
                        int exponent = 0;
                        double fraction = frexp(8.0, &exponent);
                        return fraction == 0.5 && exponent == 4 ? 0 : 1;
                        """, "frexp"),
                mathCase("ldexp", "return ldexp(0.5, 4) == 8.0 ? 0 : 1;", "ldexp"),
                mathCase("log", "return log(1.0) == 0.0 ? 0 : 1;", "log"),
                mathCase("log10", "return log10(1.0) == 0.0 ? 0 : 1;", "log10"),
                mathCase("modf", """
                        double integerPart = 0.0;
                        double fraction = modf(3.5, &integerPart);
                        return fraction == 0.5 && integerPart == 3.0 ? 0 : 1;
                        """, "modf"),
                mathCase("pow", "return pow(2.0, 3.0) == 8.0 ? 0 : 1;", "pow"),
                mathCase("sin", "return sin(0.0) == 0.0 ? 0 : 1;", "sin"),
                mathCase("sinh", "return sinh(0.0) == 0.0 ? 0 : 1;", "sinh"),
                mathCase("sqrt", "return sqrt(9.0) == 3.0 ? 0 : 1;", "sqrt"),
                mathCase("tan", "return tan(0.0) == 0.0 ? 0 : 1;", "tan"),
                mathCase("tanh", "return tanh(0.0) == 0.0 ? 0 : 1;", "tanh"),

                mathCase("acosf", "return acosf(1.0f) == 0.0f ? 0 : 1;", "acosf"),
                mathCase("asinf", "return asinf(0.0f) == 0.0f ? 0 : 1;", "asinf"),
                mathCase("atanf", "return atanf(0.0f) == 0.0f ? 0 : 1;", "atanf"),
                mathCase("atan2f", "return atan2f(0.0f, 1.0f) == 0.0f ? 0 : 1;", "atan2f"),
                mathCase("ceilf", "return ceilf(1.25f) == 2.0f ? 0 : 1;", "ceilf"),
                mathCase("cosf", "return cosf(0.0f) == 1.0f ? 0 : 1;", "cosf"),
                mathCase("coshf", "return coshf(0.0f) == 1.0f ? 0 : 1;", "coshf"),
                mathCase("expf", "return expf(0.0f) == 1.0f ? 0 : 1;", "expf"),
                mathCase("floorf", "return floorf(1.75f) == 1.0f ? 0 : 1;", "floorf"),
                mathCase("fmodf", "return fmodf(5.0f, 2.0f) == 1.0f ? 0 : 1;", "fmodf"),
                mathCase("logf", "return logf(1.0f) == 0.0f ? 0 : 1;", "logf"),
                mathCase("log10f", "return log10f(1.0f) == 0.0f ? 0 : 1;", "log10f"),
                mathCase("modff", """
                        float integerPart = 0.0f;
                        float fraction = modff(3.5f, &integerPart);
                        return fraction == 0.5f && integerPart == 3.0f ? 0 : 1;
                        """, "modff"),
                mathCase("powf", "return powf(2.0f, 3.0f) == 8.0f ? 0 : 1;", "powf"),
                mathCase("sinf", "return sinf(0.0f) == 0.0f ? 0 : 1;", "sinf"),
                mathCase("sinhf", "return sinhf(0.0f) == 0.0f ? 0 : 1;", "sinhf"),
                mathCase("sqrtf", "return sqrtf(9.0f) == 3.0f ? 0 : 1;", "sqrtf"),
                mathCase("tanf", "return tanf(0.0f) == 0.0f ? 0 : 1;", "tanf"),
                mathCase("tanhf", "return tanhf(0.0f) == 0.0f ? 0 : 1;", "tanhf")
        );
    }

    static Stream<Arguments> specialAndErrorCases() {
        return Stream.of(
                mathErrnoCase("acos-domain", """
                        errno = 0;
                        double value = acos(2.0);
                        unsigned long long bits = 0ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == EDOM
                            && (bits & 9218868437227405312ULL) == 9218868437227405312ULL
                            && (bits & 4503599627370495ULL) != 0ULL ? 0 : 1;
                        """, "_errno", "acos", "memcpy"),
                mathErrnoCase("asin-domain", """
                        errno = 0;
                        double value = asin(2.0);
                        unsigned long long bits = 0ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == EDOM
                            && (bits & 9218868437227405312ULL) == 9218868437227405312ULL
                            && (bits & 4503599627370495ULL) != 0ULL ? 0 : 1;
                        """, "_errno", "asin", "memcpy"),
                mathErrnoCase("fmod-domain", """
                        errno = 0;
                        double value = fmod(1.0, 0.0);
                        unsigned long long bits = 0ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == EDOM
                            && (bits & 9218868437227405312ULL) == 9218868437227405312ULL
                            && (bits & 4503599627370495ULL) != 0ULL ? 0 : 1;
                        """, "_errno", "fmod", "memcpy"),
                mathErrnoCase("sqrt-domain-nan", """
                        errno = 0;
                        double negative = 0.0 - 1.0;
                        double value = sqrt(negative);
                        unsigned long long bits = 0ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == EDOM
                            && (bits & 9218868437227405312ULL) == 9218868437227405312ULL
                            && (bits & 4503599627370495ULL) != 0ULL ? 0 : 1;
                        """, "_errno", "memcpy", "sqrt"),
                mathErrnoCase("log-domain", """
                        errno = 0;
                        double negative = 0.0 - 1.0;
                        double value = log(negative);
                        unsigned long long bits = 0ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == EDOM
                            && (bits & 9218868437227405312ULL) == 9218868437227405312ULL
                            && (bits & 4503599627370495ULL) != 0ULL ? 0 : 1;
                        """, "_errno", "log", "memcpy"),
                mathErrnoCase("log-pole", """
                        errno = 0;
                        double value = log(0.0);
                        double negativeLimit = 0.0 - 1.0e308;
                        return errno == ERANGE && value < negativeLimit ? 0 : 1;
                        """, "_errno", "log"),
                mathErrnoCase("log10-pole", """
                        errno = 0;
                        double value = log10(0.0);
                        double negativeLimit = 0.0 - 1.0e308;
                        return errno == ERANGE && value < negativeLimit ? 0 : 1;
                        """, "_errno", "log10"),
                mathErrnoCase("pow-domain", """
                        errno = 0;
                        double negative = 0.0 - 1.0;
                        double value = pow(negative, 0.5);
                        unsigned long long bits = 0ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == EDOM
                            && (bits & 9218868437227405312ULL) == 9218868437227405312ULL
                            && (bits & 4503599627370495ULL) != 0ULL ? 0 : 1;
                        """, "_errno", "memcpy", "pow"),
                mathErrnoCase("pow-pole", """
                        errno = 0;
                        double negativeExponent = 0.0 - 1.0;
                        double value = pow(0.0, negativeExponent);
                        return errno == ERANGE && value > 1.0e308 ? 0 : 1;
                        """, "_errno", "pow"),
                mathErrnoCase("pow-range", """
                        errno = 0;
                        double value = pow(1.0e308, 2.0);
                        return errno == ERANGE && value > 1.0e308 ? 0 : 1;
                        """, "_errno", "pow"),
                mathErrnoCase("exp-range-inf", """
                        errno = 0;
                        double value = exp(1000.0);
                        return errno == ERANGE && value > 1.0e308 ? 0 : 1;
                        """, "_errno", "exp"),
                mathErrnoCase("cosh-range", """
                        errno = 0;
                        double value = cosh(1000.0);
                        return errno == ERANGE && value > 1.0e308 ? 0 : 1;
                        """, "_errno", "cosh"),
                mathErrnoCase("sinh-range", """
                        errno = 0;
                        double value = sinh(1000.0);
                        return errno == ERANGE && value > 1.0e308 ? 0 : 1;
                        """, "_errno", "sinh"),
                mathErrnoCase("ldexp-range", """
                        errno = 0;
                        double value = ldexp(1.0, 2000);
                        return errno == ERANGE && value > 1.0e308 ? 0 : 1;
                        """, "_errno", "ldexp"),
                mathErrnoCase("exp-underflow-profile", """
                        errno = 0;
                        double negative = 0.0 - 1000.0;
                        double value = exp(negative);
                        unsigned long long bits = 1ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == 0 && bits == 0ULL ? 0 : 1;
                        """, "_errno", "exp", "memcpy"),
                mathErrnoCase("ldexp-underflow-profile", """
                        errno = 0;
                        int exponent = 0 - 2000;
                        double value = ldexp(1.0, exponent);
                        unsigned long long bits = 1ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == 0 && bits == 0ULL ? 0 : 1;
                        """, "_errno", "ldexp", "memcpy"),
                mathErrnoCase("sin-infinity-domain", """
                        double infinity = exp(1000.0);
                        errno = 0;
                        double value = sin(infinity);
                        unsigned long long bits = 0ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == EDOM
                            && (bits & 9218868437227405312ULL) == 9218868437227405312ULL
                            && (bits & 4503599627370495ULL) != 0ULL ? 0 : 1;
                        """, "_errno", "exp", "memcpy", "sin"),
                mathErrnoCase("cos-infinity-domain", """
                        double infinity = exp(1000.0);
                        errno = 0;
                        double value = cos(infinity);
                        unsigned long long bits = 0ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == EDOM
                            && (bits & 9218868437227405312ULL) == 9218868437227405312ULL
                            && (bits & 4503599627370495ULL) != 0ULL ? 0 : 1;
                        """, "_errno", "cos", "exp", "memcpy"),
                mathErrnoCase("tan-infinity-domain", """
                        double infinity = exp(1000.0);
                        errno = 0;
                        double value = tan(infinity);
                        unsigned long long bits = 0ULL;
                        memcpy(&bits, &value, 8ULL);
                        return errno == EDOM
                            && (bits & 9218868437227405312ULL) == 9218868437227405312ULL
                            && (bits & 4503599627370495ULL) != 0ULL ? 0 : 1;
                        """, "_errno", "exp", "memcpy", "tan"),
                mathBitsCase("signed-zero", """
                        double negativeQuarter = 0.0 - 0.25;
                        double negativeZero = ceil(negativeQuarter);
                        double positiveZero = floor(0.25);
                        unsigned long long negativeBits = 0ULL;
                        unsigned long long positiveBits = 0ULL;
                        memcpy(&negativeBits, &negativeZero, 8ULL);
                        memcpy(&positiveBits, &positiveZero, 8ULL);
                        return negativeBits == 9223372036854775808ULL
                            && positiveBits == 0ULL ? 0 : 1;
                        """, "ceil", "floor", "memcpy"),
                mathErrnoCase("atan2-zero-profile", """
                        double negativeQuarter = 0.0 - 0.25;
                        double negativeZero = ceil(negativeQuarter);
                        errno = 0;
                        double positiveResult = atan2(0.0, 0.0);
                        double negativeResult = atan2(negativeZero, 0.0);
                        unsigned long long positiveBits = 1ULL;
                        unsigned long long negativeBits = 1ULL;
                        memcpy(&positiveBits, &positiveResult, 8ULL);
                        memcpy(&negativeBits, &negativeResult, 8ULL);
                        return errno == 0 && positiveBits == 0ULL
                            && negativeBits == 9223372036854775808ULL ? 0 : 1;
                        """, "_errno", "atan2", "ceil", "memcpy"),
                mathErrnoCase("sqrtf-domain", """
                        errno = 0;
                        float negative = 0.0f - 1.0f;
                        float value = sqrtf(negative);
                        unsigned int bits = 0U;
                        memcpy(&bits, &value, 4ULL);
                        return errno == EDOM
                            && (bits & 2139095040U) == 2139095040U
                            && (bits & 8388607U) != 0U ? 0 : 1;
                        """, "_errno", "memcpy", "sqrtf"),
                mathErrnoCase("logf-pole", """
                        errno = 0;
                        float value = logf(0.0f);
                        float negativeLimit = 0.0f - 3.0e38f;
                        return errno == ERANGE && value < negativeLimit ? 0 : 1;
                        """, "_errno", "logf"),
                mathErrnoCase("expf-range", """
                        errno = 0;
                        float value = expf(1000.0f);
                        return errno == ERANGE && value > 3.0e38f ? 0 : 1;
                        """, "_errno", "expf"),
                mathErrnoCase("fmodf-domain", """
                        errno = 0;
                        float value = fmodf(1.0f, 0.0f);
                        unsigned int bits = 0U;
                        memcpy(&bits, &value, 4ULL);
                        return errno == EDOM
                            && (bits & 2139095040U) == 2139095040U
                            && (bits & 8388607U) != 0U ? 0 : 1;
                        """, "_errno", "fmodf", "memcpy"),
                mathErrnoCase("powf-domain", """
                        errno = 0;
                        float negative = 0.0f - 1.0f;
                        float value = powf(negative, 0.5f);
                        unsigned int bits = 0U;
                        memcpy(&bits, &value, 4ULL);
                        return errno == EDOM
                            && (bits & 2139095040U) == 2139095040U
                            && (bits & 8388607U) != 0U ? 0 : 1;
                        """, "_errno", "memcpy", "powf")
        );
    }

    private static Arguments mathCase(String name, String body, String... exports) {
        return nativeCase(name, "#include \"math.mh\"\n", body, exports);
    }

    private static Arguments mathBitsCase(String name, String body, String... exports) {
        return nativeCase(name, "#include \"math.mh\"\n#include \"string.mh\"\n", body, exports);
    }

    private static Arguments mathErrnoCase(String name, String body, String... exports) {
        return nativeCase(
                name,
                "#include \"math.mh\"\n#include \"errno.mh\"\n#include \"string.mh\"\n",
                body,
                exports
        );
    }

    private static Arguments nativeCase(String name, String includes, String body, String... exports) {
        String source = includes + "int main() {\n" + body + "\n}\n";
        return Arguments.of(new NativeCase(name, source, Set.of(exports)));
    }

    record NativeCase(String name, String source, Set<String> msvcrtExports) {
        @Override
        public String toString() {
            return name;
        }
    }
}
