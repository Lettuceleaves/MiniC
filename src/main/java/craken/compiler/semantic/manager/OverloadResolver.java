package craken.compiler.semantic.manager;

import craken.compiler.type.CrakenType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import static craken.compiler.type.CrakenType.TypeQualifier.*;

/**
 * Source-type overload selection, independent of name lookup and core ABI lowering.
 * Implements standard conversions for the current scalar/pointer/lvalue-reference subset.
 * Candidates must already have resolved types and distinct entity identities. This class does
 * not implement inheritance, templates, default arguments, initializer lists,
 * ref-qualified methods, access checks, or deleted/copy constructor checks.
 *
 * Ranking follows C++17 [over.ics.rank], [over.ics.ref], [dcl.init.ref], and [conv.qual].
 * Argument expressions are never evaluated or lowered here.
 */
public final class OverloadResolver {
    private OverloadResolver() {}
    public enum Status { SELECTED, NO_VIABLE, AMBIGUOUS }
    /** implicitObjectType is the cv-qualified owner class, not its lowered pointer ABI type. */
    public record Candidate<T>(T identity, List<CrakenType> parameterTypes, boolean variadic,
                               CrakenType implicitObjectType, boolean staticMember, int requiredParameterCount) {
        public Candidate {
            Objects.requireNonNull(identity, "identity");
            parameterTypes = List.copyOf(parameterTypes);
            if(requiredParameterCount<0||requiredParameterCount>parameterTypes.size())throw new IllegalArgumentException("Invalid required parameter count");
            if (staticMember && implicitObjectType != null) throw new IllegalArgumentException("static member has no implicit object type");
            if (implicitObjectType != null && !implicitObjectType.isStruct())
                throw new IllegalArgumentException("implicit object must have class type");
        }
        public Candidate(T identity,List<CrakenType> parameterTypes,boolean variadic,CrakenType implicitObjectType,boolean staticMember) {
            this(identity,parameterTypes,variadic,implicitObjectType,staticMember,parameterTypes.size());
        }
        public Candidate(T identity, List<CrakenType> parameterTypes, boolean variadic, CrakenType implicitObjectType) {
            this(identity, parameterTypes, variadic, implicitObjectType, false);
        }
        public Candidate(T identity, List<CrakenType> parameterTypes, boolean variadic) {
            this(identity, parameterTypes, variadic, null);
        }
    }
    /** A braced-init-list has no type or value category; its element shapes participate in selection. */
    public record Argument(CrakenType type, ValueCategory category, boolean nullPointerConstant,
                           List<Argument> listElements, boolean stringLiteral) {
        public Argument {
            Objects.requireNonNull(category, "category");
            if (listElements != null) {
                listElements = List.copyOf(listElements);
                if (type != null || nullPointerConstant) throw new IllegalArgumentException("A braced list is untyped");
            } else {
                Objects.requireNonNull(type, "type");
                if (nullPointerConstant && !type.isNullPointer()
                        && (!type.isIntegerScalar() || type.unqualified().equals(CrakenType.BOOL)
                            || category != ValueCategory.PRVALUE))
                    throw new IllegalArgumentException("null pointer constant must be integer literal zero or nullptr");
            }
        }
        public Argument(CrakenType type, ValueCategory category, boolean nullPointerConstant, List<Argument> listElements) {
            this(type, category, nullPointerConstant, listElements, false);
        }
        public Argument(CrakenType type, ValueCategory category, boolean nullPointerConstant) {
            this(type, category, nullPointerConstant, null);
        }
        /** A string literal also accepts the C conversion to a pointer to its unqualified element type. */
        public static Argument literal(CrakenType type, ValueCategory category, boolean nullPointerConstant, boolean stringLiteral) {
            return new Argument(type, category, nullPointerConstant, null, stringLiteral);
        }
        public static Argument braced(List<Argument> elements) {
            return new Argument(null, ValueCategory.PRVALUE, false, elements);
        }
        public boolean braced() { return listElements != null; }
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
    /** The frontend selects one user step without evaluating expressions. An ambiguous step
     * remains viable at user-defined rank, so it cannot incorrectly fall back to ellipsis. */
    public record UserConversion(Object identity, Argument result, boolean ambiguous, boolean exactList) {
        public UserConversion { Objects.requireNonNull(identity); Objects.requireNonNull(result); }
        public UserConversion(Object identity, Argument result, boolean ambiguous) { this(identity, result, ambiguous, false); }
    }
    @FunctionalInterface public interface UserConversionProvider {
        UserConversion find(Object candidateIdentity, Argument source, CrakenType target);
        /** Returns E only for the actual std::initializer_list<E> template identity. */
        default CrakenType initializerListElement(CrakenType target) { return null; }
        /** Positive only for an accessible, unambiguous base subobject. */
        default int baseDistance(CrakenType source,CrakenType target) { return 0; }
    }
    public static boolean standardViable(Argument source, CrakenType target) {
        return convert(source, canonical(target)) != null;
    }
    public static boolean standardViable(Argument source,CrakenType target,UserConversionProvider provider) {
        return convertWithBases(source,canonical(target),provider)!=null;
    }
    /** Qualification-only tail accepted for an explicit conversion function in direct initialization. */
    public static boolean qualificationOnly(CrakenType source, CrakenType target) {
        source = expressionType(source).unqualified(); target = expressionType(target).unqualified();
        return source.equals(target) || source.isPointer() && target.isPointer() && qualificationConvertible(source, target);
    }
    /** Inputs must each have a standard conversion; negative means the first sequence is better. */
    public static int compareStandard(Argument first, CrakenType firstTarget, Argument second, CrakenType secondTarget) {
        return compareStandard(first,firstTarget,second,secondTarget,null);
    }
    /** Compare complete standard sequences, including derived-to-base conversions. */
    public static int compareStandard(Argument first,CrakenType firstTarget,Argument second,CrakenType secondTarget,
                                      UserConversionProvider provider) {
        Conversion a = convertWithBases(first, canonical(firstTarget),provider), b = convertWithBases(second, canonical(secondTarget),provider);
        if (a == null || b == null) throw new IllegalArgumentException("Comparison requires viable standard conversions");
        return compare(a, b);
    }
    public static <T> Resolution<T> resolve(List<Candidate<T>> candidates, List<Argument> arguments) {
        return resolve(candidates, arguments, null);
    }
    public static <T> Resolution<T> resolve(List<Candidate<T>> candidates, List<Argument> arguments,
                                          Argument receiver) {
        return resolve(candidates, arguments, receiver, null);
    }
    public static <T> Resolution<T> resolve(List<Candidate<T>> candidates, List<Argument> arguments,
                                           Argument receiver, UserConversionProvider provider) {
        return resolve(candidates,arguments,receiver,provider,(a,b)->false);
    }
    public static <T> Resolution<T> resolve(List<Candidate<T>> candidates,List<Argument> arguments,Argument receiver,
            UserConversionProvider provider,java.util.function.BiPredicate<T,T> tieBreak) {
        candidates = List.copyOf(candidates);
        arguments = List.copyOf(arguments);
        var viable = new ArrayList<Viable<T>>();
        for (var candidate : candidates) {
            var conversions = conversions(candidate, arguments, receiver, provider);
            if (conversions != null) viable.add(new Viable<>(candidate, conversions));
        }
        return choose(viable,tieBreak);
    }

    /** Operator notation compares member receivers and free-function first arguments together. */
    public static <T> Resolution<T> resolveOperators(List<Candidate<T>> candidates, List<Argument> operands) {
        return resolveOperators(candidates, operands, null);
    }
    public static <T> Resolution<T> resolveOperators(List<Candidate<T>> candidates, List<Argument> operands,
                                                    UserConversionProvider provider) {
        return resolveOperators(candidates,operands,provider,(a,b)->false);
    }
    public static <T> Resolution<T> resolveOperators(List<Candidate<T>> candidates,List<Argument> operands,
            UserConversionProvider provider,java.util.function.BiPredicate<T,T> tieBreak) {
        candidates = List.copyOf(candidates);
        operands = List.copyOf(operands);
        var viable = new ArrayList<Viable<T>>();
        for (var candidate : candidates) {
            if (candidate.implicitObjectType != null && operands.isEmpty()) continue;
            var conversions = candidate.implicitObjectType == null ? conversions(candidate, operands, null, provider)
                    : conversions(candidate, operands.subList(1, operands.size()), operands.getFirst(), provider);
            if (conversions != null) viable.add(new Viable<>(candidate, conversions));
        }
        return choose(viable,tieBreak);
    }

    private static <T> Resolution<T> choose(List<Viable<T>> viable,java.util.function.BiPredicate<T,T> tieBreak) {
        var entities = viable.stream().map(Viable::candidate).toList();
        if (viable.isEmpty()) return new Resolution<>(Status.NO_VIABLE, null, entities);
        for (var candidate : viable) {
            boolean wins = true;
            for (var other : viable) {
                if (candidate != other && !better(candidate.conversions, other.conversions)
                        && !(indistinguishable(candidate.conversions,other.conversions)
                            && tieBreak.test(candidate.candidate.identity,other.candidate.identity))) {
                    wins = false;
                    break;
                }
            }
            if (wins) return new Resolution<>(Status.SELECTED, candidate.candidate, entities);
        }
        return new Resolution<>(Status.AMBIGUOUS, null, entities);
    }

    private enum Rank { EXACT, PROMOTION, CONVERSION, USER_DEFINED, ELLIPSIS }
    // Lvalue/array/function transformations are deliberately excluded from subsequence ranking.
    private enum Step { STATIC_OBJECT, NONE, NUMERIC, NULL_POINTER, POINTER_VOID, C_POINTER, POINTER_BOOL, BASE, FUNCTION_POINTER, ELLIPSIS }
    private record Conversion(Rank rank, Step step, boolean qualification, CrakenType target, boolean reference,
                              Object userIdentity, Conversion trailing, boolean ambiguous, int listKind, int listBound,
                              CrakenType.ReferenceKind referenceKind,int baseDistance) {
        Conversion(Rank rank, Step step, boolean qualification, CrakenType target, boolean reference) {
            this(rank,step,qualification,target,reference,null,null,false);
        }
        Conversion(Rank rank,Step step,boolean qualification,CrakenType target,boolean reference,Object identity,Conversion trailing,boolean ambiguous) {
            this(rank,step,qualification,target,reference,identity,trailing,ambiguous,0,0,reference?CrakenType.ReferenceKind.LVALUE:null,0);
        }
        Conversion list(int kind,int bound) {
            return new Conversion(rank,step,qualification,target,reference,userIdentity,trailing,ambiguous,kind,bound,referenceKind,baseDistance);
        }
        Conversion withReference(CrakenType parameter) {
            return new Conversion(rank,step,qualification,parameter.referent(),true,userIdentity,trailing,ambiguous,listKind,listBound,
                    ((CrakenType.ReferenceType)parameter.unqualified()).kind(),baseDistance);
        }
    }
    private record Viable<T>(Candidate<T> candidate, List<Conversion> conversions) {}

    private static <T> List<Conversion> conversions(Candidate<T> candidate, List<Argument> args, Argument receiver,
                                                    UserConversionProvider provider) {
        int fixed = candidate.parameterTypes.size();
        if (args.size() < candidate.requiredParameterCount || (!candidate.variadic && args.size() > fixed)) return null;
        var result = new ArrayList<Conversion>();
        // A homogeneous lookup set contains either ordinary functions or member functions.
        // Reject absent/extraneous receivers instead of comparing conversion lists of unequal size.
        if (!candidate.staticMember && (candidate.implicitObjectType != null) != (receiver != null)) return null;
        if (candidate.staticMember && receiver != null)
            result.add(new Conversion(Rank.EXACT, Step.STATIC_OBJECT, false, null, false));
        if (receiver != null && !candidate.staticMember) {
            CrakenType source = expressionType(receiver.type), target = canonical(candidate.implicitObjectType);
            int distance=provider==null?0:provider.baseDistance(source.unqualified(),target.unqualified());
            if ((!sameUnqualified(source, target)&&distance<=0) || !cv(target).containsAll(cv(source))) return null;
            // No ref-qualifiers: prvalues also bind to mutable implicit object parameters.
            result.add(distance>0?new Conversion(Rank.CONVERSION,Step.BASE,!cv(source).equals(cv(target)),
                    target,true,null,null,false,0,0,null,distance)
                    // [over.ics.ref]/1: binding to cv X is identity, not a pointer
                    // qualification conversion. The reference cv tiebreak still applies.
                    // null ref-kind marks an implicit object without a ref-qualifier;
                    // [over.ics.rank]/3.2.3 excludes it from the rvalue-reference tiebreak.
                    :new Conversion(Rank.EXACT, Step.NONE, false, target, true,
                            null,null,false,0,0,null,0));
        }
        for (int i=0; i<args.size(); i++) {
            if (!args.get(i).braced() && expressionType(args.get(i).type).isVoid()) return null;
            Conversion converted = i < fixed ? convertArgument(candidate.identity, args.get(i),
                    canonical(candidate.parameterTypes.get(i)), provider)
                    : args.get(i).braced() ? null : new Conversion(Rank.ELLIPSIS, Step.ELLIPSIS, false, null, false);
            if (converted == null) return null;
            result.add(converted);
        }
        return result;
    }

    private static Conversion convertArgument(Object identity, Argument argument, CrakenType parameter,
                                                UserConversionProvider provider) {
        if (argument.braced()) return listConversion(identity, argument, parameter, provider);
        Conversion converted = convertWithBases(argument, parameter,provider);
        if (converted == null && provider != null) {
            UserConversion user = provider.find(identity, argument, parameter);
            if (user != null) {
                Conversion trailing = convertWithBases(user.result, parameter,provider);
                if (trailing != null) converted = new Conversion(Rank.USER_DEFINED, Step.NONE, false,
                        parameter, parameter.isReference(), user.identity, trailing, user.ambiguous);
            }
        }
        return converted;
    }

    private static Conversion listConversion(Object identity, Argument list, CrakenType parameter,
                                              UserConversionProvider provider) {
        CrakenType target = parameter.isReference() ? parameter.referent() : parameter;
        List<Argument> elements = list.listElements;
        if(elements.size()==1&&!elements.getFirst().braced()&&provider!=null
                &&provider.baseDistance(elements.getFirst().type.unqualified(),target.unqualified())>0) {
            Conversion direct=convertWithBases(elements.getFirst(),parameter,provider);
            return direct==null?null:direct.list(1,0);
        }
        if (parameter.isReference() && elements.size() == 1 && !elements.getFirst().braced()
                && sameUnqualified(elements.getFirst().type, target)) {
            Conversion direct = convert(elements.getFirst(), parameter);
            return direct == null ? null : direct.list(1, 0);
        }
        CrakenType element = provider == null ? null : provider.initializerListElement(target);
        if (element != null || target.isArray()) {
            if (parameter.isLvalueReference() && (!cv(target).contains(CONST) || cv(target).contains(VOLATILE))) return null;
            if (element == null) element = target.elementType();
            if (target.isArray() && (target.arrayLength() < elements.size() || target.arrayLength() < 0)) return null;
            Conversion worst = new Conversion(Rank.EXACT, Step.NONE, false, target, parameter.isReference());
            for (Argument item : elements) {
                Conversion conversion = convertArgument(identity, item, element, provider);
                if (conversion == null) return null;
                if (conversion.rank.ordinal() > worst.rank.ordinal()) worst = conversion;
            }
            if (target.isArray() && target.arrayLength() > elements.size()) {
                Conversion omitted = convertArgument(identity, Argument.braced(List.of()), element, provider);
                if (omitted == null) return null;
                if (omitted.rank.ordinal() > worst.rank.ordinal()) worst = omitted;
            }
            if(parameter.isReference())worst=worst.withReference(parameter);
            return worst.list(target.isArray() ? 3 : 2, target.isArray() ? target.arrayLength() : 0);
        }
        if (target.isStruct()) {
            if (parameter.isLvalueReference() && (!cv(target).contains(CONST) || cv(target).contains(VOLATILE))) return null;
            if (provider == null) return null;
            UserConversion user = provider.find(identity, list, parameter);
            if (user == null) return null;
            if (user.exactList && elements.size() == 1) {
                Conversion exact = convert(elements.getFirst(), parameter);
                if (exact != null) return exact.list(1, 0);
            }
            Conversion trailing = convertWithBases(user.result, parameter,provider);
            return trailing == null ? null : new Conversion(Rank.USER_DEFINED, Step.NONE, false,
                    parameter, parameter.isReference(), user.identity, trailing, user.ambiguous).list(1, 0);
        }
        if (elements.size() == 1 && !elements.getFirst().braced()) {
            Conversion converted = convertArgument(identity, elements.getFirst(), parameter, provider);
            return converted == null ? null : converted.list(1, 0);
        }
        if (!elements.isEmpty() || target.isVoid() || target.isFunction()) return null;
        if (parameter.isLvalueReference() && (!cv(target).contains(CONST) || cv(target).contains(VOLATILE))) return null;
        Conversion empty=new Conversion(Rank.EXACT, Step.NONE, false, target, parameter.isReference());
        if(parameter.isReference())empty=empty.withReference(parameter);
        return empty.list(1,0);
    }

    private static Conversion convertWithBases(Argument argument,CrakenType parameter,UserConversionProvider provider) {
        Conversion direct=convert(argument,parameter);
        if(direct!=null||provider==null||argument.braced())return direct;
        CrakenType source=expressionType(argument.type),target=parameter.isReference()?parameter.referent():parameter;
        boolean pointer=!parameter.isReference()&&source.isPointer()&&target.isPointer();
        if(pointer){source=source.pointee();target=target.pointee();}
        if(!source.isStruct()||!target.isStruct())return null;
        int distance=provider.baseDistance(source.unqualified(),target.unqualified());
        if(distance<=0)return null;
        if((pointer||parameter.isReference())&&!cv(target).containsAll(cv(source)))return null;
        if(parameter.isRvalueReference()&&argument.category==ValueCategory.LVALUE)return null;
        if(parameter.isLvalueReference()&&argument.category!=ValueCategory.LVALUE
                &&(!cv(target).contains(CONST)||cv(target).contains(VOLATILE)))return null;
        return new Conversion(Rank.CONVERSION,Step.BASE,!cv(source).equals(cv(target)),
                parameter.isReference()?target:parameter,parameter.isReference(),null,null,false,0,0,
                parameter.isReference()?((CrakenType.ReferenceType)parameter.unqualified()).kind():null,distance);
    }
    private static Conversion convert(Argument argument, CrakenType parameter) {
        if (argument.braced()) return null;
        CrakenType source = expressionType(argument.type);
        if (!parameter.isReference()) return valueConversion(argument, decay(parameter).unqualified());
        CrakenType target = parameter.referent();
        boolean related = sameUnqualified(source, target);
        boolean functionConversion=functionConvertible(source,target);
        boolean compatible = (related||functionConversion) && cv(target).containsAll(cv(source));
        boolean rvalue=parameter.isRvalueReference();
        boolean directCategory=rvalue?argument.category!=ValueCategory.LVALUE||target.isFunction():argument.category==ValueCategory.LVALUE;
        if(directCategory&&compatible)return new Conversion(Rank.EXACT,functionConversion?Step.FUNCTION_POINTER:Step.NONE,false,target,true).withReference(parameter);
        if(rvalue&&related&&argument.category==ValueCategory.LVALUE)return null;
        if(!rvalue&&(!cv(target).contains(CONST)||cv(target).contains(VOLATILE)))return null;
        if (related && !compatible) return null;
        if (compatible) return new Conversion(Rank.EXACT, Step.NONE, false, target, true).withReference(parameter);
        if (target.isArray() || target.isFunction() || source.isStruct() || target.isStruct()) return null;
        Conversion converted = valueConversion(argument, target.unqualified());
        return converted == null ? null : converted.withReference(parameter);
    }

    private static Conversion valueConversion(Argument argument, CrakenType target) {
        CrakenType source = decay(expressionType(argument.type)).unqualified();
        if (source.isVoid() || target.isVoid()) return null;
        if (source.equals(target)) return new Conversion(Rank.EXACT, Step.NONE, false, target, false);
        if (source.isScalar() && target.isScalar()) {
            boolean promotion = (target.equals(CrakenType.INT) && source instanceof CrakenType.ScalarType scalar
                    && scalar.kind().integer() && scalar.kind().integerRank() < CrakenType.ScalarKind.INT.integerRank())
                    || source.equals(CrakenType.FLOAT) && target.equals(CrakenType.DOUBLE);
            return new Conversion(promotion ? Rank.PROMOTION : Rank.CONVERSION, Step.NUMERIC, false, target, false);
        }
        if (target.isPointer() && (source.isNullPointer() || argument.nullPointerConstant))
            return new Conversion(Rank.CONVERSION, Step.NULL_POINTER, false, target, false);
        if (!source.isPointer()) return null;
        if (target.equals(CrakenType.BOOL))
            return new Conversion(Rank.CONVERSION, Step.POINTER_BOOL, false, target, false);
        if (!target.isPointer()) return null;
        if(functionConvertible(source.pointee(),target.pointee()) && cv(target.pointee()).containsAll(cv(source.pointee())))
            return new Conversion(Rank.EXACT,Step.FUNCTION_POINTER,false,target,false);
        if (qualificationConvertible(source, target))
            return new Conversion(Rank.EXACT, Step.NONE, true, target, false);
        if (target.pointee().isVoid() && !source.pointee().isFunction()
                && cv(target.pointee()).containsAll(cv(source.pointee()))) {
            // Pointer conversion first preserves source pointee cv; qualification may then add cv.
            boolean qualification = !cv(target.pointee()).equals(cv(source.pointee()));
            return new Conversion(Rank.CONVERSION, Step.POINTER_VOID, qualification, target, false);
        }
        // C rules: void * converts implicitly to any object pointer that keeps its pointee cv,
        // and a string literal initializes a pointer to its non-const element type.
        if (source.pointee().isVoid() && !target.pointee().isFunction()
                && cv(target.pointee()).containsAll(cv(source.pointee()))) {
            boolean qualification = !cv(target.pointee()).equals(cv(source.pointee()));
            return new Conversion(Rank.CONVERSION, Step.C_POINTER, qualification, target, false);
        }
        if (argument.stringLiteral && target.pointee().unqualified().equals(source.pointee().unqualified()))
            return new Conversion(Rank.CONVERSION, Step.C_POINTER, false, target, false);
        return null;
    }

    private static boolean indistinguishable(List<Conversion> first,List<Conversion> second) {
        if(first.size()!=second.size())return false;
        for(int i=0;i<first.size();i++)if(compare(first.get(i),second.get(i))!=0)return false;
        return true;
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
        // [over.match.funcs]: a static member's notional object parameter has no conversion ranking.
        if (first.step == Step.STATIC_OBJECT || second.step == Step.STATIC_OBJECT) return 0;
        if (first.listKind != 0 && second.listKind != 0) {
            if ((first.listKind == 2) != (second.listKind == 2)) return first.listKind == 2 ? -1 : 1;
            if (first.listKind == 3 && second.listKind == 3 && first.listBound != second.listBound)
                return Integer.compare(first.listBound, second.listBound);
        }
        if (first.rank != second.rank) return first.rank.compareTo(second.rank);
        if (first.rank == Rank.ELLIPSIS) return 0;
        // The C pointer conversions rank below every other standard conversion.
        if ((first.step == Step.C_POINTER) != (second.step == Step.C_POINTER))
            return first.step == Step.C_POINTER ? 1 : -1;
        if(first.step==Step.NONE&&second.step==Step.FUNCTION_POINTER)return -1;
        if(second.step==Step.NONE&&first.step==Step.FUNCTION_POINTER)return 1;
        if(first.step==Step.BASE&&second.step==Step.BASE&&first.baseDistance!=second.baseDistance)
            return Integer.compare(first.baseDistance,second.baseDistance);
        if(first.step==Step.BASE&&second.step==Step.POINTER_VOID)return -1;
        if(second.step==Step.BASE&&first.step==Step.POINTER_VOID)return 1;
        if (first.rank == Rank.USER_DEFINED) {
            if (first.ambiguous || second.ambiguous || first.userIdentity != second.userIdentity) return 0;
            return compare(first.trailing, second.trailing);
        }
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
        if(first.reference&&second.reference&&first.referenceKind!=null&&second.referenceKind!=null
                &&first.referenceKind!=second.referenceKind) {
            boolean function=first.target.isFunction()&&second.target.isFunction();
            return first.referenceKind==(function?CrakenType.ReferenceKind.LVALUE:CrakenType.ReferenceKind.RVALUE)?-1:1;
        }
        if (first.reference && second.reference && sameUnqualified(first.target, second.target))
            return subset(cv(first.target), cv(second.target));
        return 0;
    }

    private static int subset(Set<CrakenType.TypeQualifier> first, Set<CrakenType.TypeQualifier> second) {
        if (first.equals(second)) return 0;
        if (second.containsAll(first)) return -1;
        if (first.containsAll(second)) return 1;
        return 0;
    }

    private static int qualificationSubset(CrakenType first, CrakenType second) {
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

    private static boolean qualificationConvertible(CrakenType source, CrakenType target) {
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

    private static List<Set<CrakenType.TypeQualifier>> decomposition(CrakenType type) {
        var result = new ArrayList<Set<CrakenType.TypeQualifier>>();
        for (;;) {
            result.add(cv(type));
            if (type.isPointer()) type = type.pointee();
            else if (type.isArray()) type = type.elementType();
            else return result;
        }
    }

    /** A noexcept function is reference-compatible with the corresponding throwing function. */
    public static boolean functionConvertible(CrakenType source,CrakenType target) {
        if(!(source.unqualified() instanceof CrakenType.FunctionType from)||!(target.unqualified() instanceof CrakenType.FunctionType to))return false;
        return from.exceptionSpecification().nonThrowing()&&!to.exceptionSpecification().nonThrowing()
                &&from.returnType().equals(to.returnType())&&from.parameterTypes().equals(to.parameterTypes())&&from.variadic()==to.variadic();
    }

    private static boolean similar(CrakenType first, CrakenType second) {
        if (first.isPointer() && second.isPointer()) return similar(first.pointee(), second.pointee());
        if (first.isArray() && second.isArray())
            return first.arrayLength() == second.arrayLength() && similar(first.elementType(), second.elementType());
        return first.unqualified().equals(second.unqualified());
    }

    private static boolean sameUnqualified(CrakenType first, CrakenType second) {
        return removeTopCv(first).equals(removeTopCv(second));
    }
    private static CrakenType removeTopCv(CrakenType type) {
        if (type.isArray()) return removeTopCv(type.elementType()).arrayOf(type.arrayLength());
        return type.unqualified();
    }
    private static Set<CrakenType.TypeQualifier> cv(CrakenType type) {
        if (type.isArray()) return cv(type.elementType());
        // restrict is not a cv-qualifier; misplaced restrict is diagnosed by the semantic pass.
        var qualifiers = java.util.EnumSet.noneOf(CrakenType.TypeQualifier.class);
        qualifiers.addAll(type.qualifiers());
        qualifiers.remove(CrakenType.TypeQualifier.RESTRICT);
        return qualifiers;
    }
    private static CrakenType expressionType(CrakenType type) {
        return canonical(type.isReference() ? type.referent() : type);
    }
    private static CrakenType decay(CrakenType type) {
        if (type.isArray()) return type.elementType().pointerTo();
        if (type.isFunction()) return type.pointerTo();
        return type;
    }
    /** Canonicalize equivalent array cv spelling and adjusted function parameter types. */
    private static CrakenType canonical(CrakenType type) {
        CrakenType base = type.unqualified();
        if (base instanceof CrakenType.ArrayType array)
            return canonical(CrakenType.qualified(array.elementType(), type.qualifiers())).arrayOf(array.length());
        CrakenType result = switch (base) {
            case CrakenType.PointerType pointer -> canonical(pointer.pointee()).pointerTo();
            case CrakenType.ReferenceType reference -> canonical(reference.referent()).referenceTo(reference.kind());
            case CrakenType.FunctionType function -> CrakenType.function(canonical(function.returnType()),
                    function.parameterTypes().stream().map(OverloadResolver::canonical)
                            .map(t -> t.isReference() ? t : decay(t).unqualified()).toList(), function.variadic(), function.exceptionSpecification());
            default -> base;
        };
        return CrakenType.qualified(result, type.qualifiers());
    }
}
