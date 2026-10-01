package minic.compiler.semantic.cpp;

import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Stream;
import static minic.compiler.semantic.cpp.CppListNarrowing.*;
import static minic.compiler.type.MiniType.*;
import static org.junit.jupiter.api.Assertions.*;

final class CppListNarrowingTest {
    static Stream<Arguments> conversions() {
        return Stream.of(
            row(INT, LONG, null, Result.SAFE),
            row(UNSIGNED_INT, LONG_LONG, null, Result.SAFE),
            row(BOOL, CHAR, null, Result.SAFE),
            row(UNSIGNED_CHAR, SHORT, null, Result.SAFE),
            row(SIGNED_CHAR, CHAR, null, Result.SAFE),
            row(CHAR, UNSIGNED_CHAR, null, Result.NEEDS_CONSTANT),
            row(UNSIGNED_INT, INT, null, Result.NEEDS_CONSTANT),
            row(LONG_LONG, UNSIGNED_LONG_LONG, null, Result.NEEDS_CONSTANT),
            row(INT, BOOL, null, Result.NEEDS_CONSTANT),
            row(BOOL, FLOAT, null, Result.NEEDS_CONSTANT),
            row(UNSIGNED_CHAR, DOUBLE, null, Result.NEEDS_CONSTANT),
            row(FLOAT, DOUBLE, null, Result.SAFE),
            row(DOUBLE, FLOAT, null, Result.NEEDS_CONSTANT),
            row(DOUBLE, INT, "1", Result.NARROWING),
            row(DOUBLE, BOOL, "0", Result.NARROWING),
            row(INT, CHAR, "127", Result.SAFE),
            row(INT, CHAR, "128", Result.NARROWING),
            row(INT, CHAR, "-128", Result.SAFE),
            row(INT, CHAR, "-129", Result.NARROWING),
            row(INT, UNSIGNED_CHAR, "255", Result.SAFE),
            row(INT, UNSIGNED_CHAR, "-1", Result.NARROWING),
            row(INT, BOOL, "0", Result.SAFE),
            row(INT, BOOL, "1", Result.SAFE),
            row(INT, BOOL, "2", Result.NARROWING),
            row(UNSIGNED_LONG_LONG, LONG_LONG, "9223372036854775807", Result.SAFE),
            row(UNSIGNED_LONG_LONG, LONG_LONG, "9223372036854775808", Result.NARROWING),
            row(UNSIGNED_LONG_LONG, DOUBLE, "18446744073709551615", Result.NARROWING),
            row(LONG_LONG, DOUBLE, "1152921504606846976", Result.SAFE),
            row(LONG_LONG, DOUBLE, "9007199254740993", Result.NARROWING),
            row(INT, FLOAT, "16777216", Result.SAFE),
            row(INT, FLOAT, "16777217", Result.NARROWING),
            row(DOUBLE, FLOAT, "0.1", Result.SAFE),
            row(DOUBLE, FLOAT, "1e100", Result.NARROWING),
            row(DOUBLE, FLOAT, "1e-100", Result.SAFE));
    }
    private static Arguments row(MiniType source, MiniType target, String constant, Result expected) {
        return Arguments.of(source, target, constant == null ? null : new BigDecimal(constant), expected);
    }
    @ParameterizedTest @MethodSource("conversions")
    void followsNumericRangeAndConstantExceptions(MiniType source, MiniType target, BigDecimal constant, Result expected) {
        assertEquals(expected, check(source, target, constant));
    }
    @Test void topLevelQualificationsDoNotChangeRepresentability() {
        assertEquals(Result.SAFE, check(qualified(INT, Set.of(TypeQualifier.CONST)),
                qualified(LONG_LONG, Set.of(TypeQualifier.VOLATILE)), null));
    }
    @Test void leavesReferenceBindingAndUserConversionsToTheirOwnRules() {
        assertEquals(Result.OUTSIDE_SUBSET, check(INT.referenceTo(), INT, null));
        assertEquals(Result.OUTSIDE_SUBSET, check(struct("S"), INT, null));
        assertEquals(Result.OUTSIDE_SUBSET, check(INT.pointerTo(), BOOL, null));
        assertEquals(Result.OUTSIDE_SUBSET, check(NULL, BOOL, null));
    }
}
