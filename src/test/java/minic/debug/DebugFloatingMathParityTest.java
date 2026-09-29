package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugFloatingMathParityTest {
    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach
    void setUp() {
        SourceFile source = new SourceFile("floating-math-provider.mc", "");
        runtime = new DebugRuntime(new DebugProgram(source, new IrResult(List.of())), "");
        library = new DebugSystemLibrary();
    }

    @ParameterizedTest(name = "{0}({1})")
    @MethodSource("doubleUnaryCases")
    void evaluatesDoubleUnaryFunctions(String function, double input, double expected) {
        Value result = call(function, number(IrType.DOUBLE, input));
        assertEquals(IrType.DOUBLE, result.type());
        assertEquals(expected, result.real(), 1.0e-12);
    }

    @ParameterizedTest(name = "{0}f({1})")
    @MethodSource("floatUnaryCases")
    void evaluatesTheConfirmedFloatUnaryFunctions(String function, float input, float expected) {
        Value result = call(function, number(IrType.FLOAT, input));
        assertEquals(IrType.FLOAT, result.type());
        assertEquals(expected, (float) result.real(), 2.0e-6f);
    }

    @ParameterizedTest(name = "{0}({1}, {2})")
    @MethodSource("binaryCases")
    void evaluatesBinaryFunctions(
            String function,
            IrType type,
            double left,
            double right,
            double expected,
            double tolerance
    ) {
        Value result = call(function, number(type, left), number(type, right));
        assertEquals(type, result.type());
        assertEquals(expected, result.real(), tolerance);
    }

    @Test
    void frexpNormalizesNormalSubnormalInfinityNanAndSignedZero() {
        long exponent = runtime.allocateZeroed(4, 4, "heap", "exponent");

        assertEquals(0.5, call("frexp", number(IrType.DOUBLE, 8.0), pointer(exponent)).real());
        assertEquals(4, runtime.read(exponent, IrType.INT).integer());

        assertEquals(0.5, call("frexp", number(IrType.DOUBLE, Double.MIN_VALUE), pointer(exponent)).real());
        assertEquals(-1073, runtime.read(exponent, IrType.INT).integer());

        double negativeZero = call("frexp", number(IrType.DOUBLE, -0.0), pointer(exponent)).real();
        assertNegativeZero(negativeZero);
        assertEquals(0, runtime.read(exponent, IrType.INT).integer());

        assertEquals(Double.POSITIVE_INFINITY,
                call("frexp", number(IrType.DOUBLE, Double.POSITIVE_INFINITY), pointer(exponent)).real());
        assertEquals(0, runtime.read(exponent, IrType.INT).integer());

        assertTrue(Double.isNaN(call("frexp", number(IrType.DOUBLE, Double.NaN), pointer(exponent)).real()));
        assertEquals(0, runtime.read(exponent, IrType.INT).integer());
    }

    @Test
    void modfAndModffWriteTheIntegralPartIntoVirtualMemory() {
        long doubleIntegral = runtime.allocateZeroed(8, 8, "heap", "double-integral");
        Value doubleFraction = call("modf", number(IrType.DOUBLE, -3.25), pointer(doubleIntegral));
        assertEquals(-0.25, doubleFraction.real());
        assertEquals(-3.0, runtime.read(doubleIntegral, IrType.DOUBLE).real());

        long floatIntegral = runtime.allocateZeroed(4, 4, "heap", "float-integral");
        Value floatFraction = call("modff", number(IrType.FLOAT, 2.75), pointer(floatIntegral));
        assertEquals(0.75f, (float) floatFraction.real());
        assertEquals(2.0f, (float) runtime.read(floatIntegral, IrType.FLOAT).real());

        Value infiniteFraction = call("modf", number(IrType.DOUBLE, Double.NEGATIVE_INFINITY),
                pointer(doubleIntegral));
        assertNegativeZero(infiniteFraction.real());
        assertEquals(Double.NEGATIVE_INFINITY, runtime.read(doubleIntegral, IrType.DOUBLE).real());

        Value nanFraction = call("modf", number(IrType.DOUBLE, Double.NaN), pointer(doubleIntegral));
        assertTrue(Double.isNaN(nanFraction.real()));
        assertTrue(Double.isNaN(runtime.read(doubleIntegral, IrType.DOUBLE).real()));
    }

    @Test
    void preservesRequiredSignedZeroResults() {
        assertNegativeZero(call("sin", number(IrType.DOUBLE, -0.0)).real());
        assertNegativeZero(call("tan", number(IrType.DOUBLE, -0.0)).real());
        assertNegativeZero(call("tanh", number(IrType.DOUBLE, -0.0)).real());
        assertNegativeZero(call("sqrt", number(IrType.DOUBLE, -0.0)).real());
        assertNegativeZero(call("fmod", number(IrType.DOUBLE, -0.0),
                number(IrType.DOUBLE, 3.0)).real());
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(
                call("fabs", number(IrType.DOUBLE, -0.0)).real()));
    }

    @Test
    void propagatesNanAndHandlesInfinityBeforeApplyingErrors() {
        assertTrue(Double.isNaN(call("sqrt", number(IrType.DOUBLE, Double.NaN)).real()));
        assertEquals(0, runtime.errno());

        assertEquals(Double.POSITIVE_INFINITY,
                call("sqrt", number(IrType.DOUBLE, Double.POSITIVE_INFINITY)).real());
        assertEquals(0, runtime.errno());

        assertEquals(2.0, call("fmod", number(IrType.DOUBLE, 2.0),
                number(IrType.DOUBLE, Double.POSITIVE_INFINITY)).real());
        assertEquals(0, runtime.errno());
    }

    @Test
    void powHandlesCStandardNanAndInfinitySpecialCases() {
        assertEquals(1.0, call("pow", number(IrType.DOUBLE, 1.0),
                number(IrType.DOUBLE, Double.NaN)).real());
        assertEquals(1.0, call("pow", number(IrType.DOUBLE, Double.NaN),
                number(IrType.DOUBLE, 0.0)).real());
        assertEquals(1.0, call("pow", number(IrType.DOUBLE, -1.0),
                number(IrType.DOUBLE, Double.POSITIVE_INFINITY)).real());
        assertEquals(Double.POSITIVE_INFINITY,
                call("pow", number(IrType.DOUBLE, Double.NEGATIVE_INFINITY),
                        number(IrType.DOUBLE, 0.5)).real());
        assertEquals(0, runtime.errno());
    }

    @ParameterizedTest(name = "{0} produces errno {3}")
    @MethodSource("errorCases")
    void appliesTheWindowsCrtDomainAndRangeProfile(
            String function,
            Value left,
            Value right,
            int expectedErrno
    ) {
        runtime.setErrno(0);
        Value result = right == null ? call(function, left) : call(function, left, right);

        assertEquals(expectedErrno, runtime.errno());
        if (expectedErrno == DebugLibrarySupport.EDOM) {
            assertTrue(Double.isNaN(result.real()), function);
        }
    }

    @Test
    void successfulCallsDoNotClearExistingErrno() {
        runtime.setErrno(7);
        assertEquals(2.0, call("sqrt", number(IrType.DOUBLE, 4.0)).real());
        assertEquals(7, runtime.errno());
    }

    @Test
    void ldexpScalesExactlyAndReportsRange() {
        assertEquals(12.0, call("ldexp", number(IrType.DOUBLE, 1.5), integer(3)).real());
        assertEquals(0, runtime.errno());

        Value overflow = call("ldexp", number(IrType.DOUBLE, 1.0), integer(100_000));
        assertEquals(Double.POSITIVE_INFINITY, overflow.real());
        assertEquals(DebugLibrarySupport.ERANGE, runtime.errno());
    }

    @Test
    void matchesTheCrtZeroAndUnderflowProfile() {
        Value positive = call("atan2", number(IrType.DOUBLE, 0.0), number(IrType.DOUBLE, 0.0));
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(positive.real()));
        assertEquals(0, runtime.errno());

        Value negative = call("atan2", number(IrType.DOUBLE, -0.0), number(IrType.DOUBLE, 0.0));
        assertNegativeZero(negative.real());
        assertEquals(0, runtime.errno());

        assertEquals(0.0, call("exp", number(IrType.DOUBLE, -1000.0)).real());
        assertEquals(0, runtime.errno());
        assertEquals(0.0, call("ldexp", number(IrType.DOUBLE, 1.0), integer(-2000)).real());
        assertEquals(0, runtime.errno());

        double negativeZero = call("ldexp", number(IrType.DOUBLE, -0.0), integer(10)).real();
        assertNegativeZero(negativeZero);
        assertEquals(0, runtime.errno());
    }

    private Value call(String name, Value... arguments) {
        DebugLibraryCallResult result = library.invoke(name, runtime, List.of(arguments)).orElseThrow();
        return ((Returned) result).value();
    }

    private Value number(IrType type, double value) {
        return Value.of(type, value);
    }

    private Value integer(int value) {
        return Value.of(IrType.INT, value);
    }

    private Value pointer(long value) {
        return Value.of(IrType.POINTER, value);
    }

    private void assertNegativeZero(double value) {
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(value));
    }

    private static Stream<Arguments> doubleUnaryCases() {
        return Stream.of(
                Arguments.of("acos", 1.0, 0.0),
                Arguments.of("asin", 1.0, Math.PI / 2.0),
                Arguments.of("atan", 1.0, Math.PI / 4.0),
                Arguments.of("ceil", -1.25, -1.0),
                Arguments.of("cos", 0.0, 1.0),
                Arguments.of("cosh", 0.0, 1.0),
                Arguments.of("exp", 1.0, Math.E),
                Arguments.of("fabs", -2.5, 2.5),
                Arguments.of("floor", -1.25, -2.0),
                Arguments.of("log", Math.E, 1.0),
                Arguments.of("log10", 100.0, 2.0),
                Arguments.of("sin", Math.PI / 2.0, 1.0),
                Arguments.of("sinh", 0.0, 0.0),
                Arguments.of("sqrt", 4.0, 2.0),
                Arguments.of("tan", 0.0, 0.0),
                Arguments.of("tanh", 0.0, 0.0)
        );
    }

    private static Stream<Arguments> floatUnaryCases() {
        return Stream.of(
                Arguments.of("acosf", 1.0f, 0.0f),
                Arguments.of("asinf", 1.0f, (float) (Math.PI / 2.0)),
                Arguments.of("atanf", 1.0f, (float) (Math.PI / 4.0)),
                Arguments.of("ceilf", -1.25f, -1.0f),
                Arguments.of("cosf", 0.0f, 1.0f),
                Arguments.of("coshf", 0.0f, 1.0f),
                Arguments.of("expf", 1.0f, (float) Math.E),
                Arguments.of("floorf", -1.25f, -2.0f),
                Arguments.of("logf", (float) Math.E, 1.0f),
                Arguments.of("log10f", 100.0f, 2.0f),
                Arguments.of("sinf", (float) (Math.PI / 2.0), 1.0f),
                Arguments.of("sinhf", 0.0f, 0.0f),
                Arguments.of("sqrtf", 4.0f, 2.0f),
                Arguments.of("tanf", 0.0f, 0.0f),
                Arguments.of("tanhf", 0.0f, 0.0f)
        );
    }

    private static Stream<Arguments> binaryCases() {
        return Stream.of(
                Arguments.of("atan2", IrType.DOUBLE, 1.0, 1.0, Math.PI / 4.0, 1.0e-12),
                Arguments.of("fmod", IrType.DOUBLE, 5.5, 2.0, 1.5, 0.0),
                Arguments.of("pow", IrType.DOUBLE, 2.0, 10.0, 1024.0, 0.0),
                Arguments.of("atan2f", IrType.FLOAT, 1.0, 1.0, (float) (Math.PI / 4.0), 2.0e-6),
                Arguments.of("fmodf", IrType.FLOAT, 5.5, 2.0, 1.5, 0.0),
                Arguments.of("powf", IrType.FLOAT, 2.0, 10.0, 1024.0, 0.0)
        );
    }

    private static Stream<Arguments> errorCases() {
        return Stream.of(
                Arguments.of("sqrt", Value.of(IrType.DOUBLE, -1.0), null, DebugLibrarySupport.EDOM),
                Arguments.of("log", Value.of(IrType.DOUBLE, -1.0), null, DebugLibrarySupport.EDOM),
                Arguments.of("log", Value.of(IrType.DOUBLE, 0.0), null, DebugLibrarySupport.ERANGE),
                Arguments.of("cos", Value.of(IrType.DOUBLE, Double.POSITIVE_INFINITY), null,
                        DebugLibrarySupport.EDOM),
                Arguments.of("fmod", Value.of(IrType.DOUBLE, 1.0), Value.of(IrType.DOUBLE, 0.0),
                        DebugLibrarySupport.EDOM),
                Arguments.of("pow", Value.of(IrType.DOUBLE, -2.0), Value.of(IrType.DOUBLE, 0.5),
                        DebugLibrarySupport.EDOM),
                Arguments.of("pow", Value.of(IrType.DOUBLE, 0.0), Value.of(IrType.DOUBLE, -1.0),
                        DebugLibrarySupport.ERANGE),
                Arguments.of("pow", Value.of(IrType.DOUBLE, 1.0e308), Value.of(IrType.DOUBLE, 2.0),
                        DebugLibrarySupport.ERANGE),
                Arguments.of("exp", Value.of(IrType.DOUBLE, 1000.0), null,
                        DebugLibrarySupport.ERANGE),
                Arguments.of("cosh", Value.of(IrType.DOUBLE, 1000.0), null,
                        DebugLibrarySupport.ERANGE),
                Arguments.of("sinh", Value.of(IrType.DOUBLE, 1000.0), null,
                        DebugLibrarySupport.ERANGE)
        );
    }
}
