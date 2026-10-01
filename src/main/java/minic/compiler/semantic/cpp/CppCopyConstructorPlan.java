package minic.compiler.semantic.cpp;

import minic.compiler.parser.node.Declaration.StructField;
import minic.compiler.type.MiniType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import static minic.compiler.type.MiniType.TypeQualifier.CONST;

/**
 * C++17 [class.copy.ctor] rules for the current non-inheriting, lvalue-reference subset.
 * Inputs use resolved source types, before references are erased to pointers. The caller supplies
 * member constructor/destructor access as viewed from this enclosing class, and recursively plans
 * class members first. This helper neither binds expressions nor generates constructor bodies.
 * Default member initializers are deliberately absent: implicit copying must not execute them.
 * Move operations, bases, virtual functions, mutable fields and default arguments require later
 * extensions; callers must not use this plan to silently accept those unsupported features.
 */
public final class CppCopyConstructorPlan {
    private CppCopyConstructorPlan() {}
    public enum Classification { ORDINARY, COPY, INVALID_BY_VALUE }
    public enum Status { SUPPRESSED, AVAILABLE, DELETED }
    public enum Action { VALUE, REFERENCE, CONSTRUCTOR }
    public enum Destructor { AVAILABLE, DELETED, INACCESSIBLE }
    public enum Failure {
        NO_VIABLE_CONSTRUCTOR, AMBIGUOUS_CONSTRUCTOR, DELETED_CONSTRUCTOR, INACCESSIBLE_CONSTRUCTOR,
        DELETED_DESTRUCTOR, INACCESSIBLE_DESTRUCTOR, NONTRIVIAL_UNION_MEMBER
    }
    /** Includes inaccessible/deleted candidates: access is checked only after overload selection. */
    public record Constructor<T>(T identity, MiniType parameterType, boolean accessible, boolean deleted, boolean trivial) {
        public Constructor {
            Objects.requireNonNull(identity,"identity");Objects.requireNonNull(parameterType,"parameterType");
            if (!parameterType.isReference() || !parameterType.referent().isStruct())
                throw new IllegalArgumentException("Copy constructor requires a source class reference parameter");
        }
    }
    public record Operations<T>(List<Constructor<T>> constructors, Destructor destructor) {
        public Operations { constructors=List.copyOf(constructors);Objects.requireNonNull(destructor,"destructor"); }
    }
    /** One field action; arrays are expanded in increasing index order by the binding/lowering caller. */
    public record Entry<T>(StructField field, List<Integer> arrayDimensions, MiniType sourceType, Action action, T constructor) {
        public Entry {
            Objects.requireNonNull(field,"field");arrayDimensions=List.copyOf(arrayDimensions);
            Objects.requireNonNull(sourceType,"sourceType");Objects.requireNonNull(action,"action");
            if ((action==Action.CONSTRUCTOR)!=(constructor!=null))throw new IllegalArgumentException("Constructor action requires selected identity");
        }
    }
    /** Retains the exact member and selected declaration; emit a diagnostic when the copy is used. */
    public record Problem<T>(StructField field, Failure reason, T constructor) {
        public Problem {Objects.requireNonNull(field,"field");Objects.requireNonNull(reason,"reason");}
    }
    /** SUPPRESSED has no implicit signature. DELETED has a signature, but no executable actions. */
    public record Result<T>(Status status, MiniType parameterType, List<Entry<T>> entries, List<Problem<T>> problems,
                            boolean trivial, boolean objectRepresentation) {
        public Result {
            Objects.requireNonNull(status,"status");entries=List.copyOf(entries);problems=List.copyOf(problems);
            if ((status==Status.SUPPRESSED)!=(parameterType==null)
                    || (status==Status.DELETED)!=!problems.isEmpty()
                    || status!=Status.AVAILABLE && (!entries.isEmpty()||trivial||objectRepresentation)
                    || objectRepresentation&&!entries.isEmpty())throw new IllegalArgumentException("Inconsistent copy plan");
        }
    }

    /** No default arguments in the current source AST; a following required parameter is ordinary. */
    public static Classification classify(MiniType owner,List<MiniType> parameterTypes) {
        requireOwner(owner);parameterTypes=List.copyOf(parameterTypes);
        if(parameterTypes.size()!=1)return Classification.ORDINARY;
        MiniType first=parameterTypes.getFirst();
        if(first.isReference()&&first.referent().unqualified().equals(owner.unqualified()))return Classification.COPY;
        return first.unqualified().equals(owner.unqualified())?Classification.INVALID_BY_VALUE:Classification.ORDINARY;
    }

    /**
     * userDeclaredCopy must include every declared copy regardless of its access/deletion status.
     * operations is keyed by unqualified canonical class identity, never by the field's spelling.
     * Missing operation data is a caller error, never evidence that byte copying is legal.
     */
    public static <T> Result<T> plan(MiniType owner,List<StructField> fields,boolean union,boolean userDeclaredCopy,
                                    Function<MiniType,Operations<T>> operations) {
        requireOwner(owner);fields=List.copyOf(fields);Objects.requireNonNull(operations,"operations");
        if(userDeclaredCopy)return new Result<>(Status.SUPPRESSED,null,List.of(),List.of(),false,false);
        Map<MiniType,Operations<T>> members=new LinkedHashMap<>();
        boolean constant=true;
        for(StructField field:fields){
            MiniType leaf=leaf(field.type());
            if(!leaf.isStruct())continue; // Reference members preserve aliases, not their referent's copyability.
            Operations<T> available=members.computeIfAbsent(leaf.unqualified(),type->checkedOperations(type,operations.apply(type)));
            constant &= available.constructors().stream().anyMatch(c->c.parameterType().referent().isConstQualified());
        }
        MiniType sourceOwner=constant?MiniType.qualified(owner.unqualified(),java.util.Set.of(CONST)):owner.unqualified();
        var entries=new ArrayList<Entry<T>>();var problems=new ArrayList<Problem<T>>();boolean trivial=true;
        for(StructField field:fields){
            MiniType source=field.type();var dimensions=new ArrayList<Integer>();
            while(source.isArray()){
                dimensions.add(((MiniType.ArrayType)source.unqualified()).length());
                source=MiniType.qualified(source.elementType(),source.qualifiers());
            }
            if(source.isReference()){
                entries.add(new Entry<>(field,dimensions,source.referent(),Action.REFERENCE,null));continue;
            }
            if(constant)source=MiniType.qualified(source,java.util.Set.of(CONST));
            if(!source.isStruct()){
                entries.add(new Entry<>(field,dimensions,source,Action.VALUE,null));continue;
            }
            Operations<T> available=members.get(source.unqualified());
            List<CppOverloadResolver.Candidate<Constructor<T>>> candidates=available.constructors().stream()
                    .map(c->new CppOverloadResolver.Candidate<>(c,List.of(c.parameterType()),false)).toList();
            var resolution=CppOverloadResolver.resolve(candidates,List.of(new CppOverloadResolver.Argument(source,CppValueCategory.LVALUE,false)));
            if(resolution.status()!=CppOverloadResolver.Status.SELECTED){
                problems.add(new Problem<>(field,resolution.status()==CppOverloadResolver.Status.AMBIGUOUS
                        ?Failure.AMBIGUOUS_CONSTRUCTOR:Failure.NO_VIABLE_CONSTRUCTOR,null));continue;
            }
            Constructor<T> selected=resolution.winner().identity();
            if(selected.deleted())problems.add(new Problem<>(field,Failure.DELETED_CONSTRUCTOR,selected.identity()));
            else if(!selected.accessible())problems.add(new Problem<>(field,Failure.INACCESSIBLE_CONSTRUCTOR,selected.identity()));
            if(available.destructor()!=Destructor.AVAILABLE)problems.add(new Problem<>(field,
                    available.destructor()==Destructor.DELETED?Failure.DELETED_DESTRUCTOR:Failure.INACCESSIBLE_DESTRUCTOR,selected.identity()));
            if(union&&!selected.trivial())problems.add(new Problem<>(field,Failure.NONTRIVIAL_UNION_MEMBER,selected.identity()));
            trivial &= selected.trivial();
            entries.add(new Entry<>(field,dimensions,source,Action.CONSTRUCTOR,selected.identity()));
        }
        if(!problems.isEmpty())return new Result<>(Status.DELETED,sourceOwner.referenceTo(),List.of(),problems,false,false);
        return new Result<>(Status.AVAILABLE,sourceOwner.referenceTo(),union?List.of():entries,List.of(),trivial,union);
    }
    private static void requireOwner(MiniType owner){
        Objects.requireNonNull(owner,"owner");if(!owner.isStruct())throw new IllegalArgumentException("Copy constructor owner must be a class");
    }
    private static MiniType leaf(MiniType type){
        while(type.isArray())type=MiniType.qualified(type.elementType(),type.qualifiers());return type;
    }
    private static <T> Operations<T> checkedOperations(MiniType owner,Operations<T> operations){
        if(operations==null)throw new IllegalArgumentException("Missing copy/destructor operations for "+owner);
        for(Constructor<T> constructor:operations.constructors())if(classify(owner,List.of(constructor.parameterType()))!=Classification.COPY)
            throw new IllegalArgumentException("Copy candidate belongs to a different class");
        return operations;
    }
}
