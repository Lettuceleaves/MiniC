package minic.compiler.semantic.cpp;

import minic.compiler.type.MiniType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * C++17 [dcl.init.list]/7 numeric rules, independent of AST binding and overload selection.
 * Checks only narrowing of an already viable numeric conversion. It does not authorize a
 * conversion, perform reference binding, rank overloads, or evaluate source expressions.
 */
public final class CppListNarrowing {
    private CppListNarrowing() {}
    public enum Result { SAFE, NARROWING, NEEDS_CONSTANT, OUTSIDE_SUBSET }
    /**
     * The caller supplies an exact, finite value after evaluating the source expression in its
     * own type. For binary floating values use new BigDecimal(double), not valueOf(double).
     * Null means no constant proof: NEEDS_CONSTANT must never be interpreted as acceptance.
     * If the caller knows the expression is nonconstant, NEEDS_CONSTANT is a narrowing error;
     * if evaluation is unsupported, it can instead issue an explicit unsupported diagnostic.
     */
    public static Result check(MiniType source, MiniType target, BigDecimal constant) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (!(source.unqualified() instanceof MiniType.ScalarType from)
                || !(target.unqualified() instanceof MiniType.ScalarType to)) return Result.OUTSIDE_SUBSET;
        var a = from.kind();
        var b = to.kind();
        if (a.floating() && b.integer()) return Result.NARROWING;
        if (a == b) return Result.SAFE;
        if (a.floating() && b.floating() && b.sizeBytes() >= a.sizeBytes()) return Result.SAFE;
        if (a.integer() && b.integer() && min(b).compareTo(min(a)) <= 0 && max(b).compareTo(max(a)) >= 0)
            return Result.SAFE;
        if (constant == null) return Result.NEEDS_CONSTANT;
        if (b.integer()) return constant.compareTo(new BigDecimal(min(b))) >= 0
                && constant.compareTo(new BigDecimal(max(b))) <= 0 ? Result.SAFE : Result.NARROWING;
        double converted = b == MiniType.ScalarKind.FLOAT ? (double) constant.floatValue() : constant.doubleValue();
        // Floating constants may round, but integer constants must survive an exact round trip.
        return Double.isFinite(converted) && (a.floating() || new BigDecimal(converted).compareTo(constant) == 0)
                ? Result.SAFE : Result.NARROWING;
    }

    private static BigInteger min(MiniType.ScalarKind type) {
        return type.signed() ? BigInteger.ONE.shiftLeft(type.sizeBytes() * 8 - 1).negate() : BigInteger.ZERO;
    }
    private static BigInteger max(MiniType.ScalarKind type) {
        if (type == MiniType.ScalarKind.BOOL) return BigInteger.ONE;
        return BigInteger.ONE.shiftLeft(type.sizeBytes() * 8 - (type.signed() ? 1 : 0)).subtract(BigInteger.ONE);
    }
}
