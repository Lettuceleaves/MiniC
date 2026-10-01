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
                           Map<MiniType.TemplateParameterType,TemplateArgument> values) {
        public Bindings {types=Map.copyOf(types);values=Map.copyOf(values);}
    }
    public static Bindings match(List<TemplateArgument> pattern,List<TemplateArgument> actual,
                                 List<ClassTemplateDecl.Parameter> parameters, UnaryOperator<MiniType> expand) {
        if(pattern.size()!=actual.size())return null;
        var matcher=new Matcher(parameters,expand);
        for(int index=0;index<pattern.size();index++)if(!matcher.argument(pattern.get(index),actual.get(index)))return null;
        for(var parameter:parameters) {
            if(parameter instanceof ClassTemplateDecl.TypeParameter && !matcher.types.containsKey(parameter.type()))return null;
            if(parameter instanceof ClassTemplateDecl.ValueParameter && !matcher.values.containsKey(parameter.type()))return null;
        }
        return new Bindings(matcher.types,matcher.values);
    }
    /** Function deduction may intentionally leave parameters for explicit/default arguments. */
    public static Bindings deduce(List<MiniType> pattern,List<MiniType> actual,List<ClassTemplateDecl.Parameter> parameters,
                                  Map<MiniType.TemplateParameterType,MiniType> initialTypes,
                                  Map<MiniType.TemplateParameterType,TemplateArgument> initialValues,UnaryOperator<MiniType> expand) {
        if(pattern.size()!=actual.size())return null;
        var matcher=new Matcher(parameters,expand);matcher.types.putAll(initialTypes);matcher.values.putAll(initialValues);
        for(int index=0;index<pattern.size();index++)if(!matcher.type(pattern.get(index),actual.get(index)))return null;
        return new Bindings(matcher.types,matcher.values);
    }
    private static final class Matcher {
        final Set<MiniType.TemplateParameterType> parameters=new HashSet<>();
        final Map<MiniType.TemplateParameterType,MiniType> types=new LinkedHashMap<>();
        final Map<MiniType.TemplateParameterType,TemplateArgument> values=new LinkedHashMap<>();
        final UnaryOperator<MiniType> expand;
        Matcher(List<ClassTemplateDecl.Parameter> parameters,UnaryOperator<MiniType> expand){parameters.forEach(p->this.parameters.add(p.type()));this.expand=expand;}
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
        boolean type(MiniType pattern,MiniType actual) {
            actual=expand.apply(actual);
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
            if(pattern instanceof MiniType.ReferenceType p && actual instanceof MiniType.ReferenceType a)return type(p.referent(),a.referent());
            if(pattern instanceof MiniType.ArrayType p && actual instanceof MiniType.ArrayType a)return p.length()==a.length()&&type(p.elementType(),a.elementType());
            if(pattern instanceof MiniType.DependentArrayType p && actual instanceof MiniType.ArrayType a)
                return type(p.elementType(),a.elementType())&&argument(new TemplateArgument.Value(p.bound()),new TemplateArgument.Integral(a.length(),MiniType.INT));
            if(pattern instanceof MiniType.TemplateIdType p && actual instanceof MiniType.TemplateIdType a) {
                if(!p.templateName().equals(a.templateName())||p.arguments().size()!=a.arguments().size())return false;
                for(int index=0;index<p.arguments().size();index++)if(!argument(p.arguments().get(index),a.arguments().get(index)))return false;
                return true;
            }
            if(pattern instanceof MiniType.FunctionType p && actual instanceof MiniType.FunctionType a) {
                if(p.variadic()!=a.variadic()||p.parameterTypes().size()!=a.parameterTypes().size()||!type(p.returnType(),a.returnType()))return false;
                for(int index=0;index<p.parameterTypes().size();index++)if(!type(p.parameterTypes().get(index),a.parameterTypes().get(index)))return false;
                return true;
            }
            return pattern.equals(actual);
        }
    }
}
