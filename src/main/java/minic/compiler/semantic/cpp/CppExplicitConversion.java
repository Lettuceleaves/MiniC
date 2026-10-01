package minic.compiler.semantic.cpp;

import minic.compiler.semantic.manager.TypeCompatibility;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import java.util.Objects;

/**
 * C++17 scalar/pointer cast notation, also used by one-argument functional notation.
 * N4659 [expr.type.conv]/2, [expr.cast]/4, [expr.reinterpret.cast]/4-8 and [expr.static.cast]/6.
 * Class conversions and reference value categories are deliberately handled by their binders.
 */
public final class CppExplicitConversion {
    private CppExplicitConversion() {}

    public enum Result { ALLOWED, INVALID, OUTSIDE_SUBSET }

    public static Result check(MiniType source, MiniType target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        target = target.unqualified();
        if (target.isVoid()) return Result.ALLOWED;
        if (target.isReference()) return Result.OUTSIDE_SUBSET;
        if (target.isFunction() || target.isArray()) return Result.INVALID;
        if (source.isReference()) source = source.referent();
        source = TypeCompatibility.decay(source).unqualified();
        if (source.isStruct() || target.isStruct()) return Result.OUTSIDE_SUBSET;
        if (source.isVoid()) return Result.INVALID;
        if (target.isNullPointer()) return source.isNullPointer() ? Result.ALLOWED : Result.OUTSIDE_SUBSET;
        if (target.isPointer()) {
            // Cast notation includes const_cast and reinterpret_cast followed by const_cast.
            // The Windows x64 target also supports object/function pointer round trips.
            if (source.isPointer() || source.isNullPointer() || source.isIntegerScalar()) return Result.ALLOWED;
            return source.isScalar() ? Result.INVALID : Result.OUTSIDE_SUBSET;
        }
        if (target.isScalar()) {
            if (source.isScalar()) return Result.ALLOWED;
            if (source.isPointer() || source.isNullPointer()) {
                if (target.equals(MiniType.BOOL)) return Result.ALLOWED;
                return target.isIntegerScalar() && TypeLayout.sizeOf(target) >= TypeLayout.POINTER_SIZE_BYTES
                        ? Result.ALLOWED : Result.INVALID;
            }
        }
        return Result.OUTSIDE_SUBSET;
    }
}
