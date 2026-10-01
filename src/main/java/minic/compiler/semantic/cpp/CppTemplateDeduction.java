package minic.compiler.semantic.cpp;

import minic.compiler.parser.node.ClassTemplateDecl;
import minic.compiler.parser.node.CppTemplateValueExpr;
import minic.compiler.type.*;
import java.util.*;
import java.util.function.UnaryOperator;

/** Structural class-pattern deduction, shared by partial ordering and concrete instantiation. */
public final class CppTemplateDeduction {
    private CppTemplateDeduction() {}
    public record Bindings(Map<MiniType.TemplateParameterType,MiniType> types,
                           Map<MiniType.TemplateParameterType,TemplateArgument> values,
                           Map<MiniType.TemplateParameterType,List<TemplateArgument>> packs) {
        public Bindings(Map<MiniType.TemplateParameterType,MiniType> types,Map<MiniType.TemplateParameterType,TemplateArgument> values){this(types,values,Map.of());}
        public Bindings {types=Map.copyOf(types);values=Map.copyOf(values);
            var copy=new LinkedHashMap<MiniType.TemplateParameterType,List<TemplateArgument>>();packs.forEach((key,value)->copy.put(key,List.copyOf(value)));packs=Map.copyOf(copy);}
    }
    public static Bindings match(List<TemplateArgument> pattern,List<TemplateArgument> actual,
                                 List<ClassTemplateDecl.Parameter> parameters, UnaryOperator<MiniType> expand) {
        return match(pattern,actual,parameters,expand,UnaryOperator.identity());
    }
    public static Bindings match(List<TemplateArgument> pattern,List<TemplateArgument> actual,
                                 List<ClassTemplateDecl.Parameter> parameters,UnaryOperator<MiniType> expand,UnaryOperator<MiniType> normalize) {
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
    public static Bindings deduce(List<MiniType> pattern,List<MiniType> actual,List<ClassTemplateDecl.Parameter> parameters,
                                  Map<MiniType.TemplateParameterType,MiniType> initialTypes,
                                  Map<MiniType.TemplateParameterType,TemplateArgument> initialValues,UnaryOperator<MiniType> expand) {
        var matcher=new Matcher(parameters,expand);matcher.types.putAll(initialTypes);matcher.values.putAll(initialValues);
        if(!matcher.typeList(pattern,actual)||!matcher.finish())return null;
        return new Bindings(matcher.types,matcher.values,matcher.packs);
    }
    private static final class Matcher {
        final Set<MiniType.TemplateParameterType> parameters=new HashSet<>();
        final Map<MiniType.TemplateParameterType,MiniType> types=new LinkedHashMap<>();
        final Map<MiniType.TemplateParameterType,TemplateArgument> values=new LinkedHashMap<>();
        final Map<MiniType.TemplateParameterType,List<TemplateArgument>> packs=new LinkedHashMap<>();
        final List<ClassTemplateDecl.Parameter> declarations;
        final Set<MiniType.TemplateParameterType> packParameters=new LinkedHashSet<>();
        final UnaryOperator<MiniType> expand;
        final UnaryOperator<MiniType> normalize;
        private record Deferred(MiniType pattern,MiniType actual) {}
        final List<Deferred> deferred=new ArrayList<>();
        Matcher(List<ClassTemplateDecl.Parameter> parameters,UnaryOperator<MiniType> expand){this(parameters,expand,UnaryOperator.identity());}
        Matcher(List<ClassTemplateDecl.Parameter> parameters,UnaryOperator<MiniType> expand,UnaryOperator<MiniType> normalize){this.normalize=normalize;this.declarations=parameters;parameters.forEach(p->{this.parameters.add(p.type());if(p.pack())packParameters.add(p.type());});this.expand=expand;}
        boolean typeList(List<MiniType> pattern,List<MiniType> actual) {
            return argumentList(pattern.stream().map(type->type instanceof MiniType.PackExpansionType pack
                    ?(TemplateArgument)new TemplateArgument.Expansion(new TemplateArgument.Type(pack.pattern())):new TemplateArgument.Type(type)).toList(),
                    actual.stream().map(type->type instanceof MiniType.PackExpansionType pack
                            ?(TemplateArgument)new TemplateArgument.Expansion(new TemplateArgument.Type(pack.pattern())):new TemplateArgument.Type(type)).toList());
        }
        boolean argumentList(List<TemplateArgument> pattern,List<TemplateArgument> actual) {
            int index=0;
            for(int p=0;p<pattern.size();p++) {
                TemplateArgument value=pattern.get(p);
                if(value instanceof TemplateArgument.Expansion expansion) {
                    if(p+1!=pattern.size())return false; // a class argument expansion is trailing
                    var identities=new LinkedHashSet<>(CppTemplatePacks.parameters(expansion.pattern()));identities.retainAll(packParameters);
                    if(identities.isEmpty())return false;
                    var sequences=new LinkedHashMap<MiniType.TemplateParameterType,List<TemplateArgument>>();identities.forEach(id->sequences.put(id,new ArrayList<>()));
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
            if(pattern instanceof TemplateArgument.Value p && p.expression() instanceof CppTemplateValueExpr parameter && parameters.contains(parameter.parameter())) {
                TemplateArgument old=values.putIfAbsent(parameter.parameter(),actual);
                return old==null||sameValue(old,actual);
            }
            return sameValue(pattern,actual);
        }
        boolean sameValue(TemplateArgument first,TemplateArgument second) {
            if(first.equals(second))return true;
            if(first instanceof TemplateArgument.Value a && a.expression() instanceof CppTemplateValueExpr x
                    && second instanceof TemplateArgument.Value b && b.expression() instanceof CppTemplateValueExpr y)return x.parameter().equals(y.parameter());
            try {
                var a=first instanceof TemplateArgument.Integral i?i:TemplateValues.evaluate(((TemplateArgument.Value)first).expression());
                var b=second instanceof TemplateArgument.Integral i?i:TemplateValues.evaluate(((TemplateArgument.Value)second).expression());
                return TemplateValues.convert(a,b.type(),true).equals(b);
            } catch(IllegalArgumentException error){return false;}
        }
        boolean finish() {
            try {
                var substitution=new CppTemplateSubstitution(types,CppFunctionTemplateDeduction.expressions(values,declarations),packs,declarations,"<deduction>","<deduction>");
                for(Deferred constraint:deferred) {
                    MiniType resolved=substitution.type(constraint.pattern());
                    if(resolved.equals(constraint.actual()))continue;
                    if(resolved instanceof MiniType.MemberType&&resolved.isDependentTemplate())return false;
                    if(!expand.apply(normalize.apply(resolved)).equals(expand.apply(constraint.actual())))return false;
                }
                return true;
            } catch(IllegalArgumentException error){return false;}
        }
        boolean type(MiniType pattern,MiniType actual) {
            actual=expand.apply(actual);
            if(pattern instanceof MiniType.MemberType || pattern instanceof MiniType.DecltypeType) {
                deferred.add(new Deferred(pattern,actual));return true;
            }
            if(pattern instanceof MiniType.TemplateParameterType parameter && parameters.contains(parameter)) {
                MiniType old=types.putIfAbsent(parameter,actual);
                return old==null||old.equals(actual);
            }
            if(pattern instanceof MiniType.QualifiedType p) {
                if(!actual.qualifiers().containsAll(p.qualifiers()))return false;
                var remaining=EnumSet.noneOf(MiniType.TypeQualifier.class);remaining.addAll(actual.qualifiers());remaining.removeAll(p.qualifiers());
                return type(p.baseType(),MiniType.qualified(actual.unqualified(),remaining));
            }
            if(actual instanceof MiniType.QualifiedType)return false;
            if(pattern instanceof MiniType.PointerType p && actual instanceof MiniType.PointerType a)return type(p.pointee(),a.pointee());
            if(pattern instanceof MiniType.ReferenceType p && actual instanceof MiniType.ReferenceType a)return p.kind()==a.kind()&&type(p.referent(),a.referent());
            if(pattern instanceof MiniType.ArrayType p && actual instanceof MiniType.ArrayType a)return p.length()==a.length()&&type(p.elementType(),a.elementType());
            if(pattern instanceof MiniType.DependentArrayType p && actual instanceof MiniType.DependentArrayType a)
                return type(p.elementType(),a.elementType())&&argument(new TemplateArgument.Value(p.bound()),new TemplateArgument.Value(a.bound()));
            if(pattern instanceof MiniType.DependentArrayType p && actual instanceof MiniType.ArrayType a)
                return a.length()>0&&type(p.elementType(),a.elementType())&&argument(new TemplateArgument.Value(p.bound()),new TemplateArgument.Integral(a.length(),MiniType.INT));
            if(pattern instanceof MiniType.TemplateIdType p && actual instanceof MiniType.TemplateIdType a) {
                return p.templateName().equals(a.templateName()) && argumentList(p.arguments(),a.arguments());
            }
            if(pattern instanceof MiniType.FunctionType p && actual instanceof MiniType.FunctionType a) {
                if(p.variadic()!=a.variadic() || !type(p.returnType(),a.returnType()) || !typeList(p.parameterTypes(),a.parameterTypes()))return false;
                var specification=p.exceptionSpecification();var actualSpecification=a.exceptionSpecification();
                if(specification.condition() instanceof CppTemplateValueExpr value && parameters.contains(value.parameter())) {
                    TemplateArgument actualException=actualSpecification.condition() instanceof CppTemplateValueExpr other
                            ?new TemplateArgument.Value(other):new TemplateArgument.Integral(actualSpecification.nonThrowing()?1:0,MiniType.BOOL);
                    return argument(new TemplateArgument.Value(value),actualException);
                }
                // C++17 permits deduction followed by the nonthrowing-to-potentially-throwing function-pointer conversion.
                return specification.condition()!=null || !specification.nonThrowing() || actualSpecification.nonThrowing();
            }
            return pattern.equals(actual);
        }
    }
}
