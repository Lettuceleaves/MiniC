package craken.compiler.semantic.manager;

import craken.compiler.parser.node.Declaration.ClassTemplateDecl;
import craken.compiler.parser.node.Expression;
import craken.compiler.parser.node.Expression.IntegerConstantExpr;
import craken.compiler.type.*;
import craken.compiler.parser.node.Expression.TemplateValueExpr;

import java.util.*;
import java.util.function.UnaryOperator;

/** Call deduction is separate from conversions and from selected function body instantiation. */
public final class FunctionTemplateDeduction {
    private FunctionTemplateDeduction() {}
    public static TemplateDeduction.Bindings deduce(List<ClassTemplateDecl.Parameter> parameters,List<CrakenType> signature,
                                                      List<CrakenType> actual,List<TemplateArgument> explicit,UnaryOperator<CrakenType> expand) {
        return deduce(parameters,signature,actual,explicit,expand,UnaryOperator.identity(),TemplateValues::evaluate);
    }
    public static TemplateDeduction.Bindings deduce(List<ClassTemplateDecl.Parameter> parameters,List<CrakenType> signature,
            List<CrakenType> actual,List<TemplateArgument> explicit,UnaryOperator<CrakenType> expand,UnaryOperator<CrakenType> normalize,
            java.util.function.Function<Expression,TemplateArgument.Integral> evaluate) {
        return deduceShapes(parameters,signature,actual.stream().map(t->t==null?null:
                new OverloadResolver.Argument(t,ValueCategory.PRVALUE,false)).toList(),explicit,expand,normalize,evaluate,t->null);
    }
    /** Braced lists are untyped: only initializer_list<E> and array-reference parameters deduce their elements. */
    public static TemplateDeduction.Bindings deduceShapes(List<ClassTemplateDecl.Parameter> parameters,List<CrakenType> signature,
            List<OverloadResolver.Argument> actual,List<TemplateArgument> explicit,UnaryOperator<CrakenType> expand,UnaryOperator<CrakenType> normalize,
            java.util.function.Function<Expression,TemplateArgument.Integral> evaluate,
            java.util.function.Function<CrakenType,CrakenType> initializerListElement) {
        boolean hasPack=parameters.stream().anyMatch(ClassTemplateDecl.Parameter::pack);
        if(!hasPack&&explicit.size()>parameters.size())return null;
        var types=new LinkedHashMap<CrakenType.TemplateParameterType,CrakenType>();
        var values=new LinkedHashMap<CrakenType.TemplateParameterType,TemplateArgument>();
        var packs=new LinkedHashMap<CrakenType.TemplateParameterType,List<TemplateArgument>>();
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
            // Explicit arguments permit later conversions. Previously deduced arguments
            // must remain in the pattern so each occurrence contributes a constraint.
            var explicitTypes=Map.copyOf(types);
            var explicitValues=Map.copyOf(values);
            var explicitPacks=Map.copyOf(packs);
            int argumentIndex=0;
            for(int signatureIndex=0;signatureIndex<signature.size();signatureIndex++) {
                CrakenType original=signature.get(signatureIndex);
                if(original instanceof CrakenType.PackExpansionType pack) {
                    var identities=new LinkedHashSet<>(TemplatePacks.parameters(pack.pattern()));
                    identities.removeIf(identity->parameters.stream().noneMatch(p->p.pack()&&p.type().equals(identity)));
                    if(identities.isEmpty())return null;
                    int count=signatureIndex+1==signature.size()?actual.size()-argumentIndex:
                            identities.stream().map(packs::get).filter(Objects::nonNull).mapToInt(List::size).findFirst().orElse(0);
                    if(count<0||argumentIndex+count>actual.size())return null;
                    var sequence=new LinkedHashMap<CrakenType.TemplateParameterType,List<TemplateArgument>>();identities.forEach(id->sequence.put(id,new ArrayList<>()));
                    for(int index=0;index<count;index++) {
                        var itemTypes=new LinkedHashMap<>(types);var itemValues=new LinkedHashMap<>(values);
                        var fixedTypes=new LinkedHashMap<>(explicitTypes);var fixedValues=new LinkedHashMap<>(explicitValues);
                        for(var identity:identities)if(packs.containsKey(identity)&&index<packs.get(identity).size()) {
                            var argument=packs.get(identity).get(index);
                            boolean fixed=explicitPacks.containsKey(identity)&&index<explicitPacks.get(identity).size();
                            if(argument instanceof TemplateArgument.Type type){itemTypes.put(identity,type.type());if(fixed)fixedTypes.put(identity,type.type());}
                            else {itemValues.put(identity,argument);if(fixed)fixedValues.put(identity,argument);}
                        }
                        var deduction=deduceOne(pack.pattern(),actual.get(argumentIndex++),parameters,itemTypes,itemValues,fixedTypes,fixedValues,expand,initializerListElement);
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
                    var deduction=deduceOne(original,actual.get(argumentIndex++),parameters,types,values,explicitTypes,explicitValues,expand,initializerListElement);
                    if(deduction==null)return null;types.putAll(deduction.types());values.putAll(deduction.values());
                    if(!mergePacks(packs,deduction.packs()))return null;
                }
            }
            for(var parameter:parameters) {
                if(parameter.pack()){packs.putIfAbsent(parameter.type(),List.of());continue;}
                if(parameter instanceof ClassTemplateDecl.TypeParameter type&&!types.containsKey(type.type())) {
                    if(type.defaultType()==null)return null;
                    var substitution=new TemplateSubstitution(types,expressions(values,parameters),packs,parameters,"<function>","<function>");
                    types.put(type.type(),normalize.apply(substitution.type(type.defaultType())));
                } else if(parameter instanceof ClassTemplateDecl.ValueParameter value) {
                    TemplateArgument argument=values.get(value.type());
                    if(argument==null) {
                        if(value.defaultValue()==null)return null;
                        argument=new TemplateArgument.Value(new TemplateSubstitution(types,expressions(values,parameters),packs,parameters,"<function>","<function>").expression(value.defaultValue()));
                    }
                    var integral=argument instanceof TemplateArgument.Integral i?i:evaluate.apply(((TemplateArgument.Value)argument).expression());
                    values.put(value.type(),TemplateValues.convert(integral,normalize.apply(new TemplateSubstitution(types,expressions(values,parameters),packs,parameters,"<function>","<function>").type(value.valueType())),true));
                }
            }
            return new TemplateDeduction.Bindings(types,values,packs);
        } catch(IllegalArgumentException error){return null;}
    }
    private static boolean mergePacks(Map<CrakenType.TemplateParameterType,List<TemplateArgument>> target,Map<CrakenType.TemplateParameterType,List<TemplateArgument>> source) {
        for(var entry:source.entrySet()){var old=target.putIfAbsent(entry.getKey(),entry.getValue());if(old!=null&&!old.equals(entry.getValue()))return false;}
        return true;
    }
    private static TemplateArgument canonicalArgument(ClassTemplateDecl.Parameter parameter,TemplateArgument argument,
            Map<CrakenType.TemplateParameterType,CrakenType> types,UnaryOperator<CrakenType> normalize,
            java.util.function.Function<Expression,TemplateArgument.Integral> evaluate) {
        if(parameter instanceof ClassTemplateDecl.TypeParameter && argument instanceof TemplateArgument.Type type)return type;
        if(parameter instanceof ClassTemplateDecl.ValueParameter value && !(argument instanceof TemplateArgument.Type)) {
            var constant=argument instanceof TemplateArgument.Integral integral?integral:evaluate.apply(((TemplateArgument.Value)argument).expression());
            return TemplateValues.convert(constant,normalize.apply(value.valueType().substituteTemplateParameters(types)),true);
        }
        throw new IllegalArgumentException("Template argument kind mismatch");
    }
    private static TemplateDeduction.Bindings deduceOne(CrakenType source,OverloadResolver.Argument actual,
            List<ClassTemplateDecl.Parameter> parameters,Map<CrakenType.TemplateParameterType,CrakenType> types,
            Map<CrakenType.TemplateParameterType,TemplateArgument> values,
            Map<CrakenType.TemplateParameterType,CrakenType> explicitTypes,
            Map<CrakenType.TemplateParameterType,TemplateArgument> explicitValues,UnaryOperator<CrakenType> expand,
            java.util.function.Function<CrakenType,CrakenType> initializerListElement) {
        CrakenType pattern=source.substituteTemplateParameters(explicitTypes,expressions(explicitValues,parameters));
        var patterns=new ArrayList<CrakenType>();var arguments=new ArrayList<CrakenType>();
        addConstraints(pattern,actual,patterns,arguments,initializerListElement,parameters);
        return TemplateDeduction.deduce(patterns,arguments,parameters,types,values,expand);
    }
    private static void addConstraints(CrakenType pattern,OverloadResolver.Argument actual,List<CrakenType> patterns,
            List<CrakenType> arguments,java.util.function.Function<CrakenType,CrakenType> initializerListElement,List<ClassTemplateDecl.Parameter> parameters) {
        if(actual==null||!pattern.isDependentTemplate()||nonDeduced(pattern))return;
        if(actual.braced()) {
            CrakenType base=(pattern.isReference()?pattern.referent():pattern).unqualified();
            CrakenType element=initializerListElement.apply(base);
            if(element==null && pattern.isReference()) {
                if(base instanceof CrakenType.ArrayType array)element=array.elementType();
                else if(base instanceof CrakenType.DependentArrayType array) {
                    element=array.elementType();
                    if(!actual.listElements().isEmpty() && array.bound() instanceof craken.compiler.parser.node.Expression.TemplateValueExpr) {
                        patterns.add(new CrakenType.DependentArrayType(CrakenType.INT,array.bound()));
                        arguments.add(CrakenType.INT.arrayOf(actual.listElements().size()));
                    }
                }
            }
            if(element!=null)for(var item:actual.listElements())addConstraints(element,item,patterns,arguments,initializerListElement,parameters);
            return;
        }
        // Array cv can be spelled on the array object (constexpr/typedef) or its elements.
        // Structural deduction must treat both representations as the same source type.
        pattern=canonicalArrayCv(pattern);
        CrakenType argument=canonicalArrayCv(actual.type());
        boolean forwarding=pattern.isRvalueReference()&&pattern.referent() instanceof CrakenType.TemplateParameterType parameter
                && parameters.stream().anyMatch(p->p instanceof ClassTemplateDecl.TypeParameter&&p.type().equals(parameter));
        if(forwarding&&actual.category()==ValueCategory.LVALUE) {
            patterns.add(pattern.referent());arguments.add(argument.referenceTo());return;
        }
        if(argument.isReference())argument=argument.referent();
        if(pattern.isReference()) {
            pattern=pattern.referent();
            var cv=EnumSet.noneOf(CrakenType.TypeQualifier.class);cv.addAll(argument.qualifiers());cv.addAll(pattern.qualifiers());
            argument=CrakenType.qualified(argument.unqualified(),cv);
        } else {
            pattern=pattern.unqualified();argument=argument.unqualified();
            if(argument.isArray())argument=argument.elementType().pointerTo();
            else if(argument.isFunction())argument=argument.pointerTo();
        }
        patterns.add(pattern);arguments.add(qualificationAdjustment(pattern,argument));
    }
    private static CrakenType qualificationAdjustment(CrakenType pattern,CrakenType actual) {
        if(pattern instanceof CrakenType.QualifiedType qualified) {
            var cv=EnumSet.noneOf(CrakenType.TypeQualifier.class);cv.addAll(actual.qualifiers());cv.addAll(qualified.qualifiers());
            return CrakenType.qualified(qualificationAdjustment(qualified.baseType(),actual.unqualified()),cv);
        }
        if(pattern instanceof CrakenType.PointerType p && actual instanceof CrakenType.PointerType a)return qualificationAdjustment(p.pointee(),a.pointee()).pointerTo();
        if(pattern instanceof CrakenType.ArrayType p && actual instanceof CrakenType.ArrayType a)
            return qualificationAdjustment(p.elementType(),a.elementType()).arrayOf(a.length());
        if(pattern instanceof CrakenType.DependentArrayType p && actual instanceof CrakenType.ArrayType a)
            return qualificationAdjustment(p.elementType(),a.elementType()).arrayOf(a.length());
        return actual;
    }
    private static CrakenType canonicalArrayCv(CrakenType type) {
        CrakenType base=type.unqualified();
        if(base instanceof CrakenType.ArrayType array)
            return canonicalArrayCv(CrakenType.qualified(array.elementType(),type.qualifiers())).arrayOf(array.length());
        if(base instanceof CrakenType.DependentArrayType array)
            return new CrakenType.DependentArrayType(canonicalArrayCv(CrakenType.qualified(array.elementType(),type.qualifiers())),array.bound());
        CrakenType result=base instanceof CrakenType.PointerType pointer?canonicalArrayCv(pointer.pointee()).pointerTo()
                :base instanceof CrakenType.ReferenceType reference?canonicalArrayCv(reference.referent()).referenceTo(reference.kind()):base;
        return CrakenType.qualified(result,type.qualifiers());
    }
    private static boolean nonDeduced(CrakenType type) {
        if(type instanceof CrakenType.MemberType || type instanceof CrakenType.DecltypeType)return true;
        if(type instanceof CrakenType.PointerType pointer)return nonDeduced(pointer.pointee());
        if(type instanceof CrakenType.ReferenceType reference)return nonDeduced(reference.referent());
        if(type instanceof CrakenType.QualifiedType qualified)return nonDeduced(qualified.baseType());
        return false;
    }
    public static Map<CrakenType.TemplateParameterType,Expression> expressions(Map<CrakenType.TemplateParameterType,TemplateArgument> values,
                                                                           List<ClassTemplateDecl.Parameter> parameters) {
        var result=new LinkedHashMap<CrakenType.TemplateParameterType,Expression>();
        for(var parameter:parameters) {
            var value=values.get(parameter.type());
            if(value instanceof TemplateArgument.Integral i)result.put(parameter.type(),new IntegerConstantExpr(i.value(),i.type(),i.toString(),parameter.range()));
            else if(value instanceof TemplateArgument.Value v)result.put(parameter.type(),v.expression());
        }
        return result;
    }
}
