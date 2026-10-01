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
        if(explicit.size()>parameters.size())return null;
        var types=new LinkedHashMap<MiniType.TemplateParameterType,MiniType>();
        var values=new LinkedHashMap<MiniType.TemplateParameterType,TemplateArgument>();
        try {
            for(int index=0;index<explicit.size();index++) {
                var parameter=parameters.get(index);var argument=explicit.get(index);
                if(parameter instanceof ClassTemplateDecl.TypeParameter && argument instanceof TemplateArgument.Type t)types.put(parameter.type(),t.type());
                else if(parameter instanceof ClassTemplateDecl.ValueParameter value && !(argument instanceof TemplateArgument.Type)) {
                    var constant=argument instanceof TemplateArgument.Integral i?i:evaluate.apply(((TemplateArgument.Value)argument).expression());
                    values.put(parameter.type(),TemplateValues.convert(constant,normalize.apply(value.valueType().substituteTemplateParameters(types)),true));
                } else return null;
            }
            var patterns=new ArrayList<MiniType>();var arguments=new ArrayList<MiniType>();
            Map<MiniType.TemplateParameterType,Expression> replacements=expressions(values,parameters);
            for(int index=0;index<actual.size()&&index<signature.size();index++) {
                MiniType pattern=signature.get(index).substituteTemplateParameters(types,replacements);
                addConstraints(pattern,actual.get(index),patterns,arguments,initializerListElement);
            }
            var deduction=CppTemplateDeduction.deduce(patterns,arguments,parameters,types,values,expand);
            if(deduction==null)return null;
            types.putAll(deduction.types());values.putAll(deduction.values());
            for(var parameter:parameters) {
                if(parameter instanceof ClassTemplateDecl.TypeParameter type&&!types.containsKey(type.type())) {
                    if(type.defaultType()==null)return null;
                    types.put(type.type(),normalize.apply(type.defaultType().substituteTemplateParameters(types,expressions(values,parameters))));
                } else if(parameter instanceof ClassTemplateDecl.ValueParameter value) {
                    TemplateArgument argument=values.get(value.type());
                    if(argument==null) {
                        if(value.defaultValue()==null)return null;
                        argument=new TemplateArgument.Value(TemplateValues.substitute(value.defaultValue(),types,expressions(values,parameters)));
                    }
                    var integral=argument instanceof TemplateArgument.Integral i?i:evaluate.apply(((TemplateArgument.Value)argument).expression());
                    values.put(value.type(),TemplateValues.convert(integral,normalize.apply(value.valueType().substituteTemplateParameters(types)),true));
                }
            }
            return new CppTemplateDeduction.Bindings(types,values);
        } catch(IllegalArgumentException error){return null;}
    }
    private static void addConstraints(MiniType pattern,CppOverloadResolver.Argument actual,List<MiniType> patterns,
            List<MiniType> arguments,java.util.function.Function<MiniType,MiniType> initializerListElement) {
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
            if(element!=null)for(var item:actual.listElements())addConstraints(element,item,patterns,arguments,initializerListElement);
            return;
        }
        MiniType argument=actual.type();
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
