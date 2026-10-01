package minic.compiler.semantic.cpp;

import minic.compiler.type.MiniType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import static minic.compiler.type.MiniType.TypeQualifier.*;

/**
 * Source-type overload selection, independent of name lookup and core ABI lowering.
 * Implements standard conversions for the current scalar/pointer/lvalue-reference subset.
 * Candidates must already have resolved types and distinct entity identities. This class does
 * not implement inheritance, user conversions, templates, default arguments, initializer lists,
 * ref-qualified methods, access checks, or deleted/copy constructor checks.
 *
 * Ranking follows C++17 [over.ics.rank], [over.ics.ref], [dcl.init.ref], and [conv.qual].
 * Argument expressions are never evaluated or lowered here.
 */
public final class CppOverloadResolver {
    private CppOverloadResolver() {}
    public enum Status { SELECTED, NO_VIABLE, AMBIGUOUS }
    /** implicitObjectType is the cv-qualified owner class, not its lowered pointer ABI type. */
    public record Candidate<T>(T identity, List<MiniType> parameterTypes, boolean variadic,
                               MiniType implicitObjectType) {
        public Candidate {
            Objects.requireNonNull(identity, "identity");
            parameterTypes = List.copyOf(parameterTypes);
            if (implicitObjectType != null && !implicitObjectType.isStruct())
                throw new IllegalArgumentException("implicit object must have class type");
        }
        public Candidate(T identity, List<MiniType> parameterTypes, boolean variadic) {
            this(identity, parameterTypes, variadic, null);
        }
    }
    /** The caller marks only integer literal zero (not a runtime/constant-expression zero). */
    public record Argument(MiniType type, CppValueCategory category, boolean nullPointerConstant) {
        public Argument {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(category, "category");
            if (nullPointerConstant && !type.isNullPointer()
                    && (!type.isIntegerScalar() || type.unqualified().equals(MiniType.BOOL)
                        || category != CppValueCategory.PRVALUE))
                throw new IllegalArgumentException("null pointer constant must be integer literal zero or nullptr");
        }
    }
    /** viable retains input order, including viable candidates dominated by the winner. */
    public record Resolution<T>(Status status, Candidate<T> winner, List<Candidate<T>> viable) {
        public Resolution {
            Objects.requireNonNull(status, "status");
            viable = List.copyOf(viable);
            if ((status == Status.SELECTED) != (winner != null)
                    || (winner != null && !viable.contains(winner))
                    || (status == Status.NO_VIABLE) != viable.isEmpty())
                throw new IllegalArgumentException("inconsistent overload resolution");
        }
    }
    public static <T> Resolution<T> resolve(List<Candidate<T>> candidates, List<Argument> arguments) {
        return resolve(candidates, arguments, null);
    }
    public static <T> Resolution<T> resolve(List<Candidate<T>> candidates, List<Argument> arguments,
                                          Argument receiver) {
        candidates = List.copyOf(candidates);
        arguments = List.copyOf(arguments);
        var viable = new ArrayList<Viable<T>>();
        for (var candidate : candidates) {
            var conversions = conversions(candidate, arguments, receiver);
            if (conversions != null) viable.add(new Viable<>(candidate, conversions));
        }
        var entities = viable.stream().map(Viable::candidate).toList();
        if (viable.isEmpty()) return new Resolution<>(Status.NO_VIABLE, null, entities);
        for (var candidate : viable) {
            boolean wins = true;
            for (var other : viable) {
                if (candidate != other && !better(candidate.conversions, other.conversions)) {
                    wins = false;
                    break;
                }
            }
            if (wins) return new Resolution<>(Status.SELECTED, candidate.candidate, entities);
        }
        return new Resolution<>(Status.AMBIGUOUS, null, entities);
    }

    private enum Rank { EXACT, PROMOTION, CONVERSION, ELLIPSIS }
    // Lvalue/array/function transformations are deliberately excluded from subsequence ranking.
    private enum Step { NONE, NUMERIC, NULL_POINTER, POINTER_VOID, POINTER_BOOL, ELLIPSIS }
    private record Conversion(Rank rank, Step step, boolean qualification,
                              MiniType target, boolean reference) {}
    private record Viable<T>(Candidate<T> candidate, List<Conversion> conversions) {}

    private static <T> List<Conversion> conversions(Candidate<T> candidate, List<Argument> args, Argument receiver) {
        int fixed = candidate.parameterTypes.size();
        if (args.size() < fixed || (!candidate.variadic && args.size() != fixed)) return null;
        var result = new ArrayList<Conversion>();
        // A homogeneous lookup set contains either ordinary functions or member functions.
        // Reject absent/extraneous receivers instead of comparing conversion lists of unequal size.
        if ((candidate.implicitObjectType != null) != (receiver != null)) return null;
        if (receiver != null) {
            MiniType source = expressionType(receiver.type), target = canonical(candidate.implicitObjectType);
            if (!sameUnqualified(source, target) || !cv(target).containsAll(cv(source))) return null;
            // No ref-qualifiers in this subset: same-class prvalues may bind to a mutable receiver.
            result.add(new Conversion(Rank.EXACT, Step.NONE, false, target, true));
        }
        for (int i=0; i<args.size(); i++) {
            if (expressionType(args.get(i).type).isVoid()) return null;
            Conversion converted = i < fixed ? convert(args.get(i), canonical(candidate.parameterTypes.get(i)))
                    : new Conversion(Rank.ELLIPSIS, Step.ELLIPSIS, false, null, false);
            if (converted == null) return null;
            result.add(converted);
        }
        return result;
    }

    private static Conversion convert(Argument argument, MiniType parameter) {
        MiniType source = expressionType(argument.type);
        if (!parameter.isReference()) return valueConversion(argument, decay(parameter).unqualified());
        MiniType target = parameter.referent();
        boolean related = sameUnqualified(source, target);
        boolean compatible = related && cv(target).containsAll(cv(source));
        if (argument.category == CppValueCategory.LVALUE && compatible)
            return new Conversion(Rank.EXACT, Step.NONE, false, target, true);
        if (!cv(target).contains(CONST) || cv(target).contains(VOLATILE)) return null;
        // A related-but-incompatible binding cannot drop volatile/const via a copied temporary.
        if (related && !compatible) return null;
        if (compatible) return new Conversion(Rank.EXACT, Step.NONE, false, target, true);
        // Arrays/functions cannot be synthesized as conversion temporaries.
        if (target.isArray() || target.isFunction() || source.isStruct() || target.isStruct()) return null;
        Conversion converted = valueConversion(argument, target.unqualified());
        return converted == null ? null : new Conversion(converted.rank, converted.step,
                converted.qualification, target, true);
    }

    private static Conversion valueConversion(Argument argument, MiniType target) {
        MiniType source = decay(expressionType(argument.type)).unqualified();
        if (source.isVoid() || target.isVoid()) return null;
        if (source.equals(target)) return new Conversion(Rank.EXACT, Step.NONE, false, target, false);
        if (source.isScalar() && target.isScalar()) {
            boolean promotion = (target.equals(MiniType.INT) && source instanceof MiniType.ScalarType scalar
                    && scalar.kind().integer() && scalar.kind().integerRank() < MiniType.ScalarKind.INT.integerRank())
                    || source.equals(MiniType.FLOAT) && target.equals(MiniType.DOUBLE);
            return new Conversion(promotion ? Rank.PROMOTION : Rank.CONVERSION, Step.NUMERIC, false, target, false);
        }
        if (target.isPointer() && (source.isNullPointer() || argument.nullPointerConstant))
            return new Conversion(Rank.CONVERSION, Step.NULL_POINTER, false, target, false);
        if (!source.isPointer()) return null;
        if (target.equals(MiniType.BOOL))
            return new Conversion(Rank.CONVERSION, Step.POINTER_BOOL, false, target, false);
        if (!target.isPointer()) return null;
        if (qualificationConvertible(source, target))
            return new Conversion(Rank.EXACT, Step.NONE, true, target, false);
        if (target.pointee().isVoid() && !source.pointee().isFunction()
                && cv(target.pointee()).containsAll(cv(source.pointee()))) {
            // Pointer conversion first preserves source pointee cv; qualification may then add cv.
            boolean qualification = !cv(target.pointee()).equals(cv(source.pointee()));
            return new Conversion(Rank.CONVERSION, Step.POINTER_VOID, qualification, target, false);
        }
        return null;
    }

    private static boolean better(List<Conversion> first, List<Conversion> second) {
        boolean strictly = false;
        for (int i=0; i<first.size(); i++) {
            int comparison = compare(first.get(i), second.get(i));
            if (comparison > 0) return false;
            strictly |= comparison < 0;
        }
        return strictly;
    }

    /** Negative means first is better. Zero includes indistinguishable, not just identical. */
    private static int compare(Conversion first, Conversion second) {
        if (first.rank != second.rank) return first.rank.compareTo(second.rank);
        if (first.rank == Rank.ELLIPSIS) return 0;
        if ((first.step == Step.POINTER_BOOL) != (second.step == Step.POINTER_BOOL))
            return first.step == Step.POINTER_BOOL ? 1 : -1;
        if (first.step == second.step && first.step != Step.NULL_POINTER) {
            // NONE without qualification is identity, a proper subsequence of any qualification
            // sequence. POINTER_VOID has the same pointer conversion prefix on both sides.
            // Null-pointer conversions directly produce different target types: no cv tiebreak.
            if (first.qualification != second.qualification) return first.qualification ? 1 : -1;
            // Qualification signatures have a partial, not a numeric, ordering.
            if (first.qualification && similar(first.target, second.target)) {
                int qualification = qualificationSubset(first.target, second.target);
                if (qualification != 0) return qualification;
            }
        }
        if (first.reference && second.reference && sameUnqualified(first.target, second.target))
            return subset(cv(first.target), cv(second.target));
        return 0;
    }

    private static int subset(Set<MiniType.TypeQualifier> first, Set<MiniType.TypeQualifier> second) {
        if (first.equals(second)) return 0;
        if (second.containsAll(first)) return -1;
        if (first.containsAll(second)) return 1;
        return 0;
    }

    private static int qualificationSubset(MiniType first, MiniType second) {
        var a = decomposition(first); var b = decomposition(second);
        boolean aSubset = true, bSubset = true, different = false;
        // Ignore top-level cv, which is not part of the qualification signature.
        for (int i=1; i<a.size(); i++) {
            aSubset &= b.get(i).containsAll(a.get(i));
            bSubset &= a.get(i).containsAll(b.get(i));
            different |= !a.get(i).equals(b.get(i));
        }
        return !different ? 0 : aSubset ? -1 : bSubset ? 1 : 0;
    }

    private static boolean qualificationConvertible(MiniType source, MiniType target) {
        if (!similar(source, target)) return false;
        var from = decomposition(source); var to = decomposition(target);
        boolean allIntermediateConst = true;
        for (int i=1; i<from.size(); i++) {
            if (!to.get(i).containsAll(from.get(i))) return false;
            if (!from.get(i).equals(to.get(i)) && !allIntermediateConst) return false;
            allIntermediateConst &= to.get(i).contains(CONST);
        }
        return true;
    }

    private static List<Set<MiniType.TypeQualifier>> decomposition(MiniType type) {
        var result = new ArrayList<Set<MiniType.TypeQualifier>>();
        for (;;) {
            result.add(cv(type));
            if (type.isPointer()) type = type.pointee();
            else if (type.isArray()) type = type.elementType();
            else return result;
        }
    }

    private static boolean similar(MiniType first, MiniType second) {
        if (first.isPointer() && second.isPointer()) return similar(first.pointee(), second.pointee());
        if (first.isArray() && second.isArray())
            return first.arrayLength() == second.arrayLength() && similar(first.elementType(), second.elementType());
        return first.unqualified().equals(second.unqualified());
    }

    private static boolean sameUnqualified(MiniType first, MiniType second) {
        return removeTopCv(first).equals(removeTopCv(second));
    }
    private static MiniType removeTopCv(MiniType type) {
        if (type.isArray()) return removeTopCv(type.elementType()).arrayOf(type.arrayLength());
        return type.unqualified();
    }
    private static Set<MiniType.TypeQualifier> cv(MiniType type) {
        return type.isArray() ? cv(type.elementType()) : type.qualifiers();
    }
    private static MiniType expressionType(MiniType type) {
        return canonical(type.isReference() ? type.referent() : type);
    }
    private static MiniType decay(MiniType type) {
        if (type.isArray()) return type.elementType().pointerTo();
        if (type.isFunction()) return type.pointerTo();
        return type;
    }
    /** Canonicalize equivalent array cv spelling and adjusted function parameter types. */
    private static MiniType canonical(MiniType type) {
        MiniType base = type.unqualified();
        if (base instanceof MiniType.ArrayType array)
            return canonical(MiniType.qualified(array.elementType(), type.qualifiers())).arrayOf(array.length());
        MiniType result = switch (base) {
            case MiniType.PointerType pointer -> canonical(pointer.pointee()).pointerTo();
            case MiniType.ReferenceType reference -> canonical(reference.referent()).referenceTo();
            case MiniType.FunctionType function -> MiniType.function(canonical(function.returnType()),
                    function.parameterTypes().stream().map(CppOverloadResolver::canonical)
                            .map(t -> t.isReference() ? t : decay(t).unqualified()).toList(), function.variadic());
            default -> base;
        };
        return MiniType.qualified(result, type.qualifiers());
    }
}
