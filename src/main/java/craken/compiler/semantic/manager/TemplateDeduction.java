package craken.compiler.semantic.manager;

import craken.compiler.parser.node.Declaration.ClassTemplateDecl;
import craken.compiler.parser.node.Expression.TemplateValueExpr;
import craken.compiler.type.*;

import java.util.*;
import java.util.function.UnaryOperator;

/** Structural class-pattern deduction, shared by partial ordering and concrete instantiation. */
public final class TemplateDeduction {
    private TemplateDeduction() {}
    public record Bindings(Map<CrakenType.TemplateParameterType,CrakenType> types,
                           Map<CrakenType.TemplateParameterType,TemplateArgument> values,
                           Map<CrakenType.TemplateParameterType,List<TemplateArgument>> packs) {
        public Bindings(Map<CrakenType.TemplateParameterType,CrakenType> types,Map<CrakenType.TemplateParameterType,TemplateArgument> values){this(types,values,Map.of());}
        public Bindings {types=Map.copyOf(types);values=Map.copyOf(values);
            var copy=new LinkedHashMap<CrakenType.TemplateParameterType,List<TemplateArgument>>();packs.forEach((key,value)->copy.put(key,List.copyOf(value)));packs=Map.copyOf(copy);}
    }
    public static Bindings match(List<TemplateArgument> pattern,List<TemplateArgument> actual,
                                 List<ClassTemplateDecl.Parameter> parameters, UnaryOperator<CrakenType> expand) {
        return match(pattern,actual,parameters,expand,UnaryOperator.identity());
    }
    public static Bindings match(List<TemplateArgument> pattern,List<TemplateArgument> actual,
                                 List<ClassTemplateDecl.Parameter> parameters,UnaryOperator<CrakenType> expand,UnaryOperator<CrakenType> normalize) {
        var matcher=new Matcher(parameters,expand,normalize);
        if(!matcher.argumentList(pattern,actual)||!matcher.finish())return null;
        for(var parameter:parameters) {
            if(parameter.pack()){matcher.packs.putIfAbsent(parameter.type(),List.of());continue;}
            if(parameter instanceof ClassTemplateDecl.TypeParameter && !matcher.types.containsKey(parameter.type()))return null;
            if(parameter instanceof ClassTemplateDecl.ValueParameter && !matcher.values.containsKey(parameter.type()))return null;
        }
        return new Bindings(matcher.types,matcher.values,matcher.packs);
    }
    /** Function deduction may intentionally leave parameters for explicit/default arguments. */
    public static Bindings deduce(List<CrakenType> pattern,List<CrakenType> actual,List<ClassTemplateDecl.Parameter> parameters,
                                  Map<CrakenType.TemplateParameterType,CrakenType> initialTypes,
                                  Map<CrakenType.TemplateParameterType,TemplateArgument> initialValues,UnaryOperator<CrakenType> expand) {
        var matcher=new Matcher(parameters,expand);matcher.types.putAll(initialTypes);matcher.values.putAll(initialValues);
        if(!matcher.typeList(pattern,actual)||!matcher.finish())return null;
        return new Bindings(matcher.types,matcher.values,matcher.packs);
    }
    private static final class Matcher {
        final Set<CrakenType.TemplateParameterType> parameters=new HashSet<>();
        final Map<CrakenType.TemplateParameterType,CrakenType> types=new LinkedHashMap<>();
        final Map<CrakenType.TemplateParameterType,TemplateArgument> values=new LinkedHashMap<>();
        final Map<CrakenType.TemplateParameterType,List<TemplateArgument>> packs=new LinkedHashMap<>();
        final List<ClassTemplateDecl.Parameter> declarations;
        final Set<CrakenType.TemplateParameterType> packParameters=new LinkedHashSet<>();
        final UnaryOperator<CrakenType> expand;
        final UnaryOperator<CrakenType> normalize;
        private record Deferred(CrakenType pattern,CrakenType actual) {}
        final List<Deferred> deferred=new ArrayList<>();
        Matcher(List<ClassTemplateDecl.Parameter> parameters,UnaryOperator<CrakenType> expand){this(parameters,expand,UnaryOperator.identity());}
        Matcher(List<ClassTemplateDecl.Parameter> parameters,UnaryOperator<CrakenType> expand,UnaryOperator<CrakenType> normalize){this.normalize=normalize;this.declarations=parameters;parameters.forEach(p->{this.parameters.add(p.type());if(p.pack())packParameters.add(p.type());});this.expand=expand;}
        boolean typeList(List<CrakenType> pattern,List<CrakenType> actual) {
            return argumentList(pattern.stream().map(type->type instanceof CrakenType.PackExpansionType pack
                    ?(TemplateArgument)new TemplateArgument.Expansion(new TemplateArgument.Type(pack.pattern())):new TemplateArgument.Type(type)).toList(),
                    actual.stream().map(type->type instanceof CrakenType.PackExpansionType pack
                            ?(TemplateArgument)new TemplateArgument.Expansion(new TemplateArgument.Type(pack.pattern())):new TemplateArgument.Type(type)).toList());
        }
        boolean argumentList(List<TemplateArgument> pattern,List<TemplateArgument> actual) {
            int index=0;
            for(int p=0;p<pattern.size();p++) {
                TemplateArgument value=pattern.get(p);
                if(value instanceof TemplateArgument.Expansion expansion) {
                    if(p+1!=pattern.size())return false; // a class argument expansion is trailing
                    var identities=new LinkedHashSet<>(TemplatePacks.parameters(expansion.pattern()));identities.retainAll(packParameters);
                    if(identities.isEmpty())return false;
                    var sequences=new LinkedHashMap<CrakenType.TemplateParameterType,List<TemplateArgument>>();identities.forEach(id->sequences.put(id,new ArrayList<>()));
                    while(index<actual.size()) {
                        var child=new Matcher(declarations,expand,normalize);child.types.putAll(types);child.values.putAll(values);
                        TemplateArgument argument=actual.get(index++);if(argument instanceof TemplateArgument.Expansion a)argument=a.pattern();
                        if(!child.argument(expansion.pattern(),argument)||!child.finish())return false;
                        for(var identity:identities) {
                            TemplateArgument found=child.types.containsKey(identity)?new TemplateArgument.Type(child.types.get(identity)):child.values.get(identity);
                            if(found==null)return false;sequences.get(identity).add(found);
                        }
                        child.types.forEach((key,item)->{if(!identities.contains(key))types.put(key,item);});
                        child.values.forEach((key,item)->{if(!identities.contains(key))values.put(key,item);});
                        for(var nested:child.packs.entrySet()){var old=packs.putIfAbsent(nested.getKey(),nested.getValue());if(old!=null&&!old.equals(nested.getValue()))return false;}
                    }
                    for(var sequence:sequences.entrySet()){var old=packs.putIfAbsent(sequence.getKey(),List.copyOf(sequence.getValue()));if(old!=null&&!old.equals(sequence.getValue()))return false;}
                    return true;
                }
                if(index>=actual.size()||!argument(value,actual.get(index++)))return false;
            }
            return index==actual.size();
        }
        boolean argument(TemplateArgument pattern,TemplateArgument actual) {
            if(pattern instanceof TemplateArgument.Type p && actual instanceof TemplateArgument.Type a)return type(p.type(),a.type());
            if(pattern instanceof TemplateArgument.Type || actual instanceof TemplateArgument.Type)return false;
            if(pattern instanceof TemplateArgument.Value p && p.expression() instanceof TemplateValueExpr parameter && parameters.contains(parameter.parameter())) {
                TemplateArgument old=values.putIfAbsent(parameter.parameter(),actual);
                return old==null||sameValue(old,actual);
            }
            return sameValue(pattern,actual);
        }
        boolean sameValue(TemplateArgument first,TemplateArgument second) {
            if(first.equals(second))return true;
            if(first instanceof TemplateArgument.Value a && a.expression() instanceof TemplateValueExpr x
                    && second instanceof TemplateArgument.Value b && b.expression() instanceof TemplateValueExpr y)return x.parameter().equals(y.parameter());
            try {
                var a=first instanceof TemplateArgument.Integral i?i:TemplateValues.evaluate(((TemplateArgument.Value)first).expression());
                var b=second instanceof TemplateArgument.Integral i?i:TemplateValues.evaluate(((TemplateArgument.Value)second).expression());
                return TemplateValues.convert(a,b.type(),true).equals(b);
            } catch(IllegalArgumentException error){return false;}
        }
        boolean finish() {
            try {
                var substitution=new TemplateSubstitution(types,FunctionTemplateDeduction.expressions(values,declarations),packs,declarations,"<deduction>","<deduction>");
                for(Deferred constraint:deferred) {
                    CrakenType resolved=substitution.type(constraint.pattern());
                    if(resolved.equals(constraint.actual()))continue;
                    if(resolved instanceof CrakenType.MemberType&&resolved.isDependentTemplate())return false;
                    if(!expand.apply(normalize.apply(resolved)).equals(expand.apply(constraint.actual())))return false;
                }
                return true;
            } catch(IllegalArgumentException error){return false;}
        }
        boolean type(CrakenType pattern,CrakenType actual) {
            actual=expand.apply(actual);
            // CV on an array type describes its elements ([dcl.array]/1), so both the
            // array-layer and element-layer spellings are equivalent during deduction.
            pattern=pushArrayCv(pattern);
            actual=pushArrayCv(actual);
            if(pattern instanceof CrakenType.MemberType || pattern instanceof CrakenType.DecltypeType) {
                deferred.add(new Deferred(pattern,actual));return true;
            }
            if(pattern instanceof CrakenType.TemplateParameterType parameter && parameters.contains(parameter)) {
                CrakenType old=types.putIfAbsent(parameter,actual);
                return old==null||old.equals(actual);
            }
            if(pattern instanceof CrakenType.QualifiedType p) {
                if(actual.isArray() || actual.unqualified() instanceof CrakenType.DependentArrayType) {
                    // A qualified pattern matches a cv-qualified array by consuming the
                    // qualifiers from its innermost element, leaving the rest for T.
                    CrakenType cursor=actual;var layers=new ArrayList<CrakenType>();
                    for(;;) {
                        CrakenType base=cursor.unqualified();
                        if(base instanceof CrakenType.ArrayType array){layers.add(array);cursor=array.elementType();continue;}
                        if(base instanceof CrakenType.DependentArrayType array){layers.add(array);cursor=array.elementType();continue;}
                        break;
                    }
                    if(!cursor.qualifiers().containsAll(p.qualifiers()))return false;
                    var remaining=EnumSet.noneOf(CrakenType.TypeQualifier.class);
                    remaining.addAll(cursor.qualifiers());remaining.removeAll(p.qualifiers());
                    CrakenType rebuilt=CrakenType.qualified(cursor.unqualified(),remaining);
                    for(int i=layers.size()-1;i>=0;i--) {
                        CrakenType layer=layers.get(i);
                        rebuilt=layer instanceof CrakenType.ArrayType array
                                ?rebuilt.arrayOf(array.length())
                                :new CrakenType.DependentArrayType(rebuilt,((CrakenType.DependentArrayType)layer).bound());
                    }
                    return type(p.baseType(),rebuilt);
                }
                if(!actual.qualifiers().containsAll(p.qualifiers()))return false;
                var remaining=EnumSet.noneOf(CrakenType.TypeQualifier.class);remaining.addAll(actual.qualifiers());remaining.removeAll(p.qualifiers());
                return type(p.baseType(),CrakenType.qualified(actual.unqualified(),remaining));
            }
            if(actual instanceof CrakenType.QualifiedType)return false;
            if(pattern instanceof CrakenType.PointerType p && actual instanceof CrakenType.PointerType a)return type(p.pointee(),a.pointee());
            if(pattern instanceof CrakenType.ReferenceType p && actual instanceof CrakenType.ReferenceType a)return p.kind()==a.kind()&&type(p.referent(),a.referent());
            if(pattern instanceof CrakenType.ArrayType p && actual instanceof CrakenType.ArrayType a)return p.length()==a.length()&&type(p.elementType(),a.elementType());
            if(pattern instanceof CrakenType.DependentArrayType p && actual instanceof CrakenType.DependentArrayType a)
                return type(p.elementType(),a.elementType())&&argument(new TemplateArgument.Value(p.bound()),new TemplateArgument.Value(a.bound()));
            if(pattern instanceof CrakenType.DependentArrayType p && actual instanceof CrakenType.ArrayType a)
                return a.length()>0&&type(p.elementType(),a.elementType())&&argument(new TemplateArgument.Value(p.bound()),new TemplateArgument.Integral(a.length(),CrakenType.INT));
            if(pattern instanceof CrakenType.TemplateIdType p && actual instanceof CrakenType.TemplateIdType a) {
                return p.templateName().equals(a.templateName()) && argumentList(p.arguments(),a.arguments());
            }
            if(pattern instanceof CrakenType.FunctionType p && actual instanceof CrakenType.FunctionType a) {
                if(p.variadic()!=a.variadic() || !type(p.returnType(),a.returnType()) || !typeList(p.parameterTypes(),a.parameterTypes()))return false;
                var specification=p.exceptionSpecification();var actualSpecification=a.exceptionSpecification();
                if(specification.condition() instanceof TemplateValueExpr value && parameters.contains(value.parameter())) {
                    TemplateArgument actualException=actualSpecification.condition() instanceof TemplateValueExpr other
                            ?new TemplateArgument.Value(other):new TemplateArgument.Integral(actualSpecification.nonThrowing()?1:0,CrakenType.BOOL);
                    return argument(new TemplateArgument.Value(value),actualException);
                }
                // C++17 permits deduction followed by the nonthrowing-to-potentially-throwing function-pointer conversion.
                return specification.condition()!=null || !specification.nonThrowing() || actualSpecification.nonThrowing();
            }
            return pattern.equals(actual);
        }
        /** Push qualifiers on array layers into the element type, mirroring canonicalArrayCv. */
        private static CrakenType pushArrayCv(CrakenType type) {
            CrakenType base=type.unqualified();
            if(base instanceof CrakenType.ArrayType array)
                return pushArrayCv(CrakenType.qualified(array.elementType(),type.qualifiers())).arrayOf(array.length());
            if(base instanceof CrakenType.DependentArrayType array)
                return new CrakenType.DependentArrayType(pushArrayCv(CrakenType.qualified(array.elementType(),type.qualifiers())),array.bound());
            CrakenType result=base instanceof CrakenType.PointerType pointer?pushArrayCv(pointer.pointee()).pointerTo()
                    :base instanceof CrakenType.ReferenceType reference?pushArrayCv(reference.referent()).referenceTo(reference.kind()):base;
            return CrakenType.qualified(result,type.qualifiers());
        }
    }
}
