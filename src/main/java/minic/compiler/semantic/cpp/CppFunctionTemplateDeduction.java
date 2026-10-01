package minic.compiler.semantic.cpp;

import minic.compiler.parser.node.ClassTemplateDecl;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.IntegerConstantExpr;
import minic.compiler.type.*;
import java.util.*;
import java.util.function.UnaryOperator;

/** Call deduction is separate from conversions and from selected function body instantiation. */
public final class CppFunctionTemplateDeduction {
    private CppFunctionTemplateDeduction() {}
    public static CppTemplateDeduction.Bindings deduce(List<ClassTemplateDecl.Parameter> parameters,List<MiniType> signature,
                                                      List<MiniType> actual,List<TemplateArgument> explicit,UnaryOperator<MiniType> expand) {
        return deduce(parameters,signature,actual,explicit,expand,UnaryOperator.identity(),TemplateValues::evaluate);
    }
    public static CppTemplateDeduction.Bindings deduce(List<ClassTemplateDecl.Parameter> parameters,List<MiniType> signature,
            List<MiniType> actual,List<TemplateArgument> explicit,UnaryOperator<MiniType> expand,UnaryOperator<MiniType> normalize,
            java.util.function.Function<Expression,TemplateArgument.Integral> evaluate) {
        return deduceShapes(parameters,signature,actual.stream().map(t->t==null?null:
                new CppOverloadResolver.Argument(t,CppValueCategory.PRVALUE,false)).toList(),explicit,expand,normalize,evaluate,t->null);
    }
    /** Braced lists are untyped: only initializer_list<E> and array-reference parameters deduce their elements. */
    public static CppTemplateDeduction.Bindings deduceShapes(List<ClassTemplateDecl.Parameter> parameters,List<MiniType> signature,
            List<CppOverloadResolver.Argument> actual,List<TemplateArgument> explicit,UnaryOperator<MiniType> expand,UnaryOperator<MiniType> normalize,
            java.util.function.Function<Expression,TemplateArgument.Integral> evaluate,
            java.util.function.Function<MiniType,MiniType> initializerListElement) {
        boolean hasPack=parameters.stream().anyMatch(ClassTemplateDecl.Parameter::pack);
        if(!hasPack&&explicit.size()>parameters.size())return null;
        var types=new LinkedHashMap<MiniType.TemplateParameterType,MiniType>();
        var values=new LinkedHashMap<MiniType.TemplateParameterType,TemplateArgument>();
        var packs=new LinkedHashMap<MiniType.TemplateParameterType,List<TemplateArgument>>();
        try {
            int explicitIndex=0;
            for(var parameter:parameters) {
                if(explicitIndex>=explicit.size())break;
                if(parameter.pack()) {
                    var sequence=new ArrayList<TemplateArgument>();
                    while(explicitIndex<explicit.size())sequence.add(canonicalArgument(parameter,explicit.get(explicitIndex++),types,normalize,evaluate));
                    packs.put(parameter.type(),sequence);break;
                }
                TemplateArgument argument=canonicalArgument(parameter,explicit.get(explicitIndex++),types,normalize,evaluate);
                if(argument instanceof TemplateArgument.Type type)types.put(parameter.type(),type.type());else values.put(parameter.type(),argument);
            }
            int argumentIndex=0;
            for(int signatureIndex=0;signatureIndex<signature.size();signatureIndex++) {
                MiniType original=signature.get(signatureIndex);
                if(original instanceof MiniType.PackExpansionType pack) {
                    var identities=new LinkedHashSet<>(CppTemplatePacks.parameters(pack.pattern()));
                    identities.removeIf(identity->parameters.stream().noneMatch(p->p.pack()&&p.type().equals(identity)));
                    if(identities.isEmpty())return null;
                    int count=signatureIndex+1==signature.size()?actual.size()-argumentIndex:
                            identities.stream().map(packs::get).filter(Objects::nonNull).mapToInt(List::size).findFirst().orElse(0);
                    if(count<0||argumentIndex+count>actual.size())return null;
                    var sequence=new LinkedHashMap<MiniType.TemplateParameterType,List<TemplateArgument>>();identities.forEach(id->sequence.put(id,new ArrayList<>()));
                    for(int index=0;index<count;index++) {
                        var itemTypes=new LinkedHashMap<>(types);var itemValues=new LinkedHashMap<>(values);
                        for(var identity:identities)if(packs.containsKey(identity)&&index<packs.get(identity).size()) {
                            var argument=packs.get(identity).get(index);
                            if(argument instanceof TemplateArgument.Type type)itemTypes.put(identity,type.type());else itemValues.put(identity,argument);
                        }
                        var deduction=deduceOne(pack.pattern(),actual.get(argumentIndex++),parameters,itemTypes,itemValues,expand,initializerListElement);
                        if(deduction==null)return null;
                        for(var identity:identities) {
                            TemplateArgument argument=deduction.types().containsKey(identity)?new TemplateArgument.Type(deduction.types().get(identity)):deduction.values().get(identity);
                            if(argument==null)return null;sequence.get(identity).add(argument);
                        }
                        deduction.types().forEach((id,value)->{if(!identities.contains(id))types.put(id,value);});
                        deduction.values().forEach((id,value)->{if(!identities.contains(id))values.put(id,value);});
                        if(!mergePacks(packs,deduction.packs()))return null;
                    }
                    for(var entry:sequence.entrySet()) {
                        var prefix=packs.get(entry.getKey());if(prefix!=null&&prefix.size()>entry.getValue().size())return null;
                        packs.put(entry.getKey(),List.copyOf(entry.getValue()));
                    }
                } else if(argumentIndex<actual.size()) {
                    var deduction=deduceOne(original,actual.get(argumentIndex++),parameters,types,values,expand,initializerListElement);
                    if(deduction==null)return null;types.putAll(deduction.types());values.putAll(deduction.values());
                    if(!mergePacks(packs,deduction.packs()))return null;
                }
            }
            for(var parameter:parameters) {
                if(parameter.pack()){packs.putIfAbsent(parameter.type(),List.of());continue;}
                if(parameter instanceof ClassTemplateDecl.TypeParameter type&&!types.containsKey(type.type())) {
                    if(type.defaultType()==null)return null;
                    var substitution=new CppTemplateSubstitution(types,expressions(values,parameters),packs,parameters,"<function>","<function>");
                    types.put(type.type(),normalize.apply(substitution.type(type.defaultType())));
                } else if(parameter instanceof ClassTemplateDecl.ValueParameter value) {
                    TemplateArgument argument=values.get(value.type());
                    if(argument==null) {
                        if(value.defaultValue()==null)return null;
                        argument=new TemplateArgument.Value(new CppTemplateSubstitution(types,expressions(values,parameters),packs,parameters,"<function>","<function>").expression(value.defaultValue()));
                    }
                    var integral=argument instanceof TemplateArgument.Integral i?i:evaluate.apply(((TemplateArgument.Value)argument).expression());
                    values.put(value.type(),TemplateValues.convert(integral,normalize.apply(new CppTemplateSubstitution(types,expressions(values,parameters),packs,parameters,"<function>","<function>").type(value.valueType())),true));
                }
            }
            return new CppTemplateDeduction.Bindings(types,values,packs);
        } catch(IllegalArgumentException error){return null;}
    }
    private static boolean mergePacks(Map<MiniType.TemplateParameterType,List<TemplateArgument>> target,Map<MiniType.TemplateParameterType,List<TemplateArgument>> source) {
        for(var entry:source.entrySet()){var old=target.putIfAbsent(entry.getKey(),entry.getValue());if(old!=null&&!old.equals(entry.getValue()))return false;}
        return true;
    }
    private static TemplateArgument canonicalArgument(ClassTemplateDecl.Parameter parameter,TemplateArgument argument,
            Map<MiniType.TemplateParameterType,MiniType> types,UnaryOperator<MiniType> normalize,
            java.util.function.Function<Expression,TemplateArgument.Integral> evaluate) {
        if(parameter instanceof ClassTemplateDecl.TypeParameter && argument instanceof TemplateArgument.Type type)return type;
        if(parameter instanceof ClassTemplateDecl.ValueParameter value && !(argument instanceof TemplateArgument.Type)) {
            var constant=argument instanceof TemplateArgument.Integral integral?integral:evaluate.apply(((TemplateArgument.Value)argument).expression());
            return TemplateValues.convert(constant,normalize.apply(value.valueType().substituteTemplateParameters(types)),true);
        }
        throw new IllegalArgumentException("Template argument kind mismatch");
    }
    private static CppTemplateDeduction.Bindings deduceOne(MiniType source,CppOverloadResolver.Argument actual,
            List<ClassTemplateDecl.Parameter> parameters,Map<MiniType.TemplateParameterType,MiniType> types,
            Map<MiniType.TemplateParameterType,TemplateArgument> values,UnaryOperator<MiniType> expand,
            java.util.function.Function<MiniType,MiniType> initializerListElement) {
        MiniType pattern=source.substituteTemplateParameters(types,expressions(values,parameters));
        var patterns=new ArrayList<MiniType>();var arguments=new ArrayList<MiniType>();
        addConstraints(pattern,actual,patterns,arguments,initializerListElement,parameters);
        return CppTemplateDeduction.deduce(patterns,arguments,parameters,types,values,expand);
    }
    private static void addConstraints(MiniType pattern,CppOverloadResolver.Argument actual,List<MiniType> patterns,
            List<MiniType> arguments,java.util.function.Function<MiniType,MiniType> initializerListElement,List<ClassTemplateDecl.Parameter> parameters) {
        if(actual==null||!pattern.isDependentTemplate()||nonDeduced(pattern))return;
        if(actual.braced()) {
            MiniType base=(pattern.isReference()?pattern.referent():pattern).unqualified();
            MiniType element=initializerListElement.apply(base);
            if(element==null && pattern.isReference()) {
                if(base instanceof MiniType.ArrayType array)element=array.elementType();
                else if(base instanceof MiniType.DependentArrayType array) {
                    element=array.elementType();
                    if(!actual.listElements().isEmpty() && array.bound() instanceof minic.compiler.parser.node.CppTemplateValueExpr) {
                        patterns.add(new MiniType.DependentArrayType(MiniType.INT,array.bound()));
                        arguments.add(MiniType.INT.arrayOf(actual.listElements().size()));
                    }
                }
            }
            if(element!=null)for(var item:actual.listElements())addConstraints(element,item,patterns,arguments,initializerListElement,parameters);
            return;
        }
        MiniType argument=actual.type();
        boolean forwarding=pattern.isRvalueReference()&&pattern.referent() instanceof MiniType.TemplateParameterType parameter
                && parameters.stream().anyMatch(p->p instanceof ClassTemplateDecl.TypeParameter&&p.type().equals(parameter));
        if(forwarding&&actual.category()==CppValueCategory.LVALUE) {
            patterns.add(pattern.referent());arguments.add(argument.referenceTo());return;
        }
        if(argument.isReference())argument=argument.referent();
        if(pattern.isReference()) {
            pattern=pattern.referent();
            var cv=EnumSet.noneOf(MiniType.TypeQualifier.class);cv.addAll(argument.qualifiers());cv.addAll(pattern.qualifiers());
            argument=MiniType.qualified(argument.unqualified(),cv);
        } else {
            pattern=pattern.unqualified();argument=argument.unqualified();
            if(argument.isArray())argument=argument.elementType().pointerTo();
            else if(argument.isFunction())argument=argument.pointerTo();
        }
        patterns.add(pattern);arguments.add(qualificationAdjustment(pattern,argument));
    }
    private static MiniType qualificationAdjustment(MiniType pattern,MiniType actual) {
        if(pattern instanceof MiniType.QualifiedType qualified) {
            var cv=EnumSet.noneOf(MiniType.TypeQualifier.class);cv.addAll(actual.qualifiers());cv.addAll(qualified.qualifiers());
            return MiniType.qualified(qualificationAdjustment(qualified.baseType(),actual.unqualified()),cv);
        }
        if(pattern instanceof MiniType.PointerType p && actual instanceof MiniType.PointerType a)return qualificationAdjustment(p.pointee(),a.pointee()).pointerTo();
        return actual;
    }
    private static boolean nonDeduced(MiniType type) {
        if(type instanceof MiniType.MemberType)return true;
        if(type instanceof MiniType.PointerType pointer)return nonDeduced(pointer.pointee());
        if(type instanceof MiniType.ReferenceType reference)return nonDeduced(reference.referent());
        if(type instanceof MiniType.QualifiedType qualified)return nonDeduced(qualified.baseType());
        return false;
    }
    public static Map<MiniType.TemplateParameterType,Expression> expressions(Map<MiniType.TemplateParameterType,TemplateArgument> values,
                                                                           List<ClassTemplateDecl.Parameter> parameters) {
        var result=new LinkedHashMap<MiniType.TemplateParameterType,Expression>();
        for(var parameter:parameters) {
            var value=values.get(parameter.type());
            if(value instanceof TemplateArgument.Integral i)result.put(parameter.type(),new IntegerConstantExpr(i.value(),i.type(),i.toString(),parameter.range()));
            else if(value instanceof TemplateArgument.Value v)result.put(parameter.type(),v.expression());
        }
        return result;
    }
}
