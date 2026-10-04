package craken.compiler.semantic.manager;

import craken.SourceRange;
import craken.compiler.parser.node.*;
import craken.compiler.parser.node.Declaration.*;
import craken.compiler.type.CrakenType;
import craken.compiler.parser.node.Expression.PackExpansionExpr;
import craken.compiler.parser.node.Expression.SizeofPackExpr;
import craken.compiler.parser.node.Expression.TemplateValueExpr;
import craken.compiler.parser.node.Expression.TypeQueryExpr;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/** Instantiation-time source AST copying. Never rewrites tokens or interprets a library name. */
public final class TemplateSubstitution {
    private final Map<CrakenType.TemplateParameterType, CrakenType> arguments;
    private final Map<CrakenType.TemplateParameterType, Expression> values;
    private final Map<CrakenType.TemplateParameterType,List<craken.compiler.type.TemplateArgument>> packs;
    private final Map<String,CrakenType.TemplateParameterType> templateNames;
    private final Map<String,List<String>> parameterPacks=new LinkedHashMap<>();
    private final Set<String> deferredParameterPacks=new LinkedHashSet<>();
    private final Map<String,String> selectedNames=new LinkedHashMap<>();
    private final String primaryName;
    private final String instanceName;
    private final IdentityHashMap<Object, Object> copies = new IdentityHashMap<>();
    private final IdentityHashMap<AstNode, AstNode> origins = new IdentityHashMap<>();

    public TemplateSubstitution(Map<CrakenType.TemplateParameterType, CrakenType> arguments,
                                   String primaryName, String instanceName) {
        this(arguments,Map.of(),primaryName,instanceName);
    }

    public TemplateSubstitution(Map<CrakenType.TemplateParameterType, CrakenType> arguments,
                                   Map<CrakenType.TemplateParameterType, Expression> values,
                                   String primaryName, String instanceName) {
        this(arguments,values,Map.of(),List.of(),primaryName,instanceName);
    }

    public TemplateSubstitution(Map<CrakenType.TemplateParameterType, CrakenType> arguments,
            Map<CrakenType.TemplateParameterType, Expression> values,
            Map<CrakenType.TemplateParameterType,List<craken.compiler.type.TemplateArgument>> packs,
            List<ClassTemplateDecl.Parameter> parameters,String primaryName,String instanceName) {
        this.packs=new LinkedHashMap<>();packs.forEach((key,value)->this.packs.put(key,List.copyOf(value)));
        this.templateNames=new LinkedHashMap<>();parameters.forEach(p->{if(p.pack())templateNames.put(p.name(),p.type());});
        this.arguments = Map.copyOf(arguments);
        this.values = Map.copyOf(values);
        this.primaryName = Objects.requireNonNull(primaryName);
        this.instanceName = Objects.requireNonNull(instanceName);
    }

    public Declaration instantiate(Declaration source) { return (Declaration)copy(source); }
    public Expression expression(Expression source) { return (Expression)copy(source); }
    public FunctionDecl instantiate(FunctionDecl source) { return (FunctionDecl)copy(source); }
    public ConstructorMember instantiate(ConstructorMember source) { return (ConstructorMember)copy(source); }
    public StructDecl instantiate(StructDecl source) { return (StructDecl) copy(source); }
    public Map<AstNode, AstNode> origins() { return Collections.unmodifiableMap(origins); }

    private CrakenType.ExceptionSpecification exceptionSpecification(CrakenType.ExceptionSpecification source) {
        return source.condition()==null?source:new CrakenType.ExceptionSpecification(true,false,(Expression)copy(source.condition()));
    }

    public CrakenType type(CrakenType source) {
        return switch (source) {
            case CrakenType.DecltypeType query -> new CrakenType.DecltypeType((Expression)copy(query.expression()));
            case CrakenType.TrailingReturnType trailing -> new CrakenType.TrailingReturnType(type(trailing.type()));
            case CrakenType.QualifiedType qualified -> CrakenType.qualified(type(qualified.baseType()),qualified.qualifiers());
            case CrakenType.PointerType pointer -> type(pointer.pointee()).pointerTo();
            case CrakenType.ReferenceType reference -> type(reference.referent()).referenceTo(reference.kind());
            case CrakenType.ArrayType array -> type(array.elementType()).arrayOf(array.length());
            case CrakenType.DependentArrayType array -> new CrakenType.DependentArrayType(type(array.elementType()),(Expression)copy(array.bound()))
                    .substituteTemplateParameters(arguments,values);
            case CrakenType.TemplateIdType id -> new CrakenType.TemplateIdType(id.templateName(),copyList(id.arguments()));
            case CrakenType.PackExpansionType pack -> new CrakenType.PackExpansionType(type(pack.pattern()));
            case CrakenType.MemberType member -> new CrakenType.MemberType(type(member.owner()),member.name());
            case CrakenType.FunctionType function -> CrakenType.function(type(function.returnType()),copyList(function.parameterTypes()),function.variadic(),exceptionSpecification(function.exceptionSpecification()));
            default -> source.substituteTemplateParameters(arguments,values);
        };
    }

    private Object copy(Object source) {
        if (source == null || source instanceof String || source instanceof Number || source instanceof Boolean
                || source instanceof Character || source instanceof Enum<?> || source instanceof SourceRange) return source;
        // Compatibility projections share operands by identity, including transformed AST nodes.
        Object existing = copies.get(source);
        if (existing != null) return existing;
        if (source instanceof CrakenType.ExceptionSpecification specification) return exceptionSpecification(specification);
        if (source instanceof CrakenType type) return type(type);
        if(source instanceof SizeofPackExpr size) {
            Integer count=parameterPacks.containsKey(size.name())?parameterPacks.get(size.name()).size():null;
            var identity=templateNames.get(size.name());if(identity!=null && packs.containsKey(identity))count=packs.get(identity).size();
            if(count==null)return source; // a nested member template can still own this pack
            var result=new Expression.IntegerConstantExpr(count,CrakenType.UNSIGNED_LONG_LONG,Integer.toString(count),size.range());
            copies.put(source,result);origins.put(result,size);return result;
        }
        if(source instanceof Expression.NameExpr name && selectedNames.containsKey(name.name())) {
            var result=new Expression.NameExpr(selectedNames.get(name.name()),name.range());
            copies.put(source,result);origins.put(result,name);return result;
        }
        if(source instanceof Expression.CallExpr call)return copyCall(call);
        if(source instanceof craken.compiler.type.TemplateArgument.Expansion expansion)
            return new craken.compiler.type.TemplateArgument.Expansion((craken.compiler.type.TemplateArgument)copy(expansion.pattern()));
        if(source instanceof craken.compiler.type.TemplateArgument.Type argument)return new craken.compiler.type.TemplateArgument.Type(type(argument.type()));
        if(source instanceof craken.compiler.type.TemplateArgument.Value argument)return new craken.compiler.type.TemplateArgument.Value((Expression)copy(argument.expression()));
        if(source instanceof craken.compiler.type.TemplateArgument.Integral argument)return argument;
        if (source instanceof TemplateValueExpr value) {
            Expression replacement=values.get(value.parameter());
            if(replacement==null) {
                var result=new TemplateValueExpr(value.parameter(),type(value.valueType()),value.range());
                copies.put(source,result);origins.put(result,value);return result;
            }
            Expression result=replacement instanceof Expression.IntegerConstantExpr constant
                    ?new Expression.IntegerConstantExpr(constant.value(),constant.type(),constant.lexeme(),value.range())
                    :craken.compiler.type.TemplateValues.substitute(replacement,arguments,values);
            copies.put(source,result);origins.put(result,value);return result;
        }
        if (source instanceof List<?> list) {
            List<?> result = copyList(list);
            copies.put(source, result);
            return result;
        }
        List<Parameter> parameters=source instanceof FunctionDecl function?function.parameters():source instanceof ConstructorMember constructor?constructor.parameters():null;
        if(parameters!=null) {
            var saved=new LinkedHashMap<>(parameterPacks);
            var savedDeferred=new LinkedHashSet<>(deferredParameterPacks);
            try {prepareParameters(parameters);return copyRecord(source);}
            finally {parameterPacks.clear();parameterPacks.putAll(saved);
                deferredParameterPacks.clear();deferredParameterPacks.addAll(savedDeferred);}
        }
        return copyRecord(source);
    }

    private Object copyRecord(Object source) {
        Class<?> kind = source.getClass();
        if ((!kind.isRecord() && !(source instanceof AbstractAstNode))
                || !kind.getPackageName().equals("craken.compiler.parser.node"))
            throw new IllegalArgumentException("unsupported source component during template substitution: " + kind.getName());
        var shape = AstNodeComponents.describe(kind);
        var components = shape.components();
        Object[] values = new Object[components.size()];
        try {
            for (int index = 0; index < components.size(); index++) {
                var component = components.get(index);
                values[index] = copy(component.accessor().invoke(source));
                if(component.name().equals("qualifiedName")) {
                    String memberName=source instanceof OutOfLineConstructorDecl declaration?((ConstructorMember)copy(declaration.constructor())).name()
                            :source instanceof OutOfLineDestructorDecl declaration?"~"+((DestructorMember)copy(declaration.destructor())).name()
                            :source instanceof OutOfLineMethodDecl declaration&&declaration.method().conversionName()!=null?((FunctionDecl)copy(declaration.method())).name():null;
                    if(memberName!=null) {
                        QualifiedName name=(QualifiedName)values[index];var segments=new ArrayList<>(name.segments());
                        segments.set(segments.size()-1,memberName);
                        values[index]=new QualifiedName(name.global(),segments,name.range());
                    }
                }
                if (component.name().equals("name")) {
                    if (source instanceof StructDecl record && record.name().equals(primaryName)) values[index] = instanceName;
                    else if (source instanceof ConstructorMember || source instanceof DestructorMember)
                        values[index] = simple(instanceName);
                    else if (source instanceof FunctionDecl function && function.conversionName() != null)
                        values[index] = "operator " + type(function.conversionName().targetType());
                }
            }
            Object result = shape.constructor().newInstance(values);
            copies.put(source, result);
            if (source instanceof AstNode original && result instanceof AstNode clone) origins.put(clone, original);
            return result;
        } catch (ReflectiveOperationException error) {
            Throwable cause = error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
            throw new IllegalArgumentException("cannot instantiate source node " + kind.getSimpleName(), cause);
        }
    }

    private void prepareParameters(List<Parameter> parameters) {
        for(Parameter parameter:parameters)if(parameter.type() instanceof CrakenType.PackExpansionType expansion) {
            int count=expansionCount(expansion.pattern());
            if(count<0){deferredParameterPacks.add(parameter.name());continue;}
            deferredParameterPacks.remove(parameter.name());
            var names=new ArrayList<String>();for(int i=0;i<count;i++)names.add(parameter.name()+"$pack"+i);
            parameterPacks.put(parameter.name(),List.copyOf(names));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> copyList(List<T> source) {
        var result=new ArrayList<T>();
        for(Object item:source) {
            Object pattern=expansionPattern(item);
            if(pattern==null){result.add((T)copy(item));continue;}
            int count=expansionCount(pattern);
            if(count<0){result.add((T)copy(item));continue;}
            for(int index=0;index<count;index++) {
                TemplateSubstitution child=at(index,pattern);
                Object expanded;
                if(item instanceof Parameter parameter) {
                    var pack=(CrakenType.PackExpansionType)parameter.type();
                    expanded=new Parameter(parameter.name()+"$pack"+index,child.type(pack.pattern()),(Expression)child.copy(parameter.defaultValue()),parameter.range());
                } else if(item instanceof TypeQueryExpr.TypeArgument argument) {
                    expanded=new TypeQueryExpr.TypeArgument(child.type(argument.type()),false,argument.range());
                } else expanded=child.copy(pattern);
                result.add((T)expanded);origins.putAll(child.origins);
                if(expanded instanceof AstNode clone && item instanceof AstNode original)origins.put(clone,original);
            }
        }
        return List.copyOf(result);
    }
    private Object expansionPattern(Object source) {
        if(source instanceof CrakenType.PackExpansionType pack)return pack.pattern();
        if(source instanceof PackExpansionExpr pack)return pack.pattern();
        if(source instanceof craken.compiler.type.TemplateArgument.Expansion pack)return pack.pattern();
        if(source instanceof Parameter parameter && parameter.type() instanceof CrakenType.PackExpansionType pack)return pack.pattern();
        if(source instanceof TypeQueryExpr.TypeArgument argument && argument.packExpansion())return argument.type();
        return null;
    }
    private int expansionCount(Object pattern) {
        Integer count=null;boolean unknown=false;
        for(var parameter:TemplatePacks.parameters(pattern)) {
            var arguments=packs.get(parameter);
            if(arguments==null){if(!this.arguments.containsKey(parameter)&&!values.containsKey(parameter))unknown=true;continue;}
            if(count!=null && count!=arguments.size())throw new IllegalArgumentException("Simultaneous parameter packs have different lengths");
            count=arguments.size();
        }
        for(String name:TemplatePacks.names(pattern)) {
            if(deferredParameterPacks.contains(name))unknown=true;
            if(parameterPacks.containsKey(name)) {
                int size=parameterPacks.get(name).size();
                if(count!=null && count!=size)throw new IllegalArgumentException("Simultaneous parameter packs have different lengths");
                count=size;
            }
        }
        if(count==null && !unknown)throw new IllegalArgumentException("Pack expansion pattern contains no unexpanded parameter pack");
        return unknown?-1:count;
    }
    private TemplateSubstitution at(int index,Object pattern) {
        var types=new LinkedHashMap<>(arguments);var replacements=new LinkedHashMap<>(values);
        for(var parameter:TemplatePacks.parameters(pattern)) {
            var sequence=packs.get(parameter);if(sequence==null)continue;
            var argument=sequence.get(index);
            if(argument instanceof craken.compiler.type.TemplateArgument.Type type)types.put(parameter,type.type());
            else if(argument instanceof craken.compiler.type.TemplateArgument.Value value)replacements.put(parameter,value.expression());
            else if(argument instanceof craken.compiler.type.TemplateArgument.Integral value)
                replacements.put(parameter,new Expression.IntegerConstantExpr(value.value(),value.type(),value.toString(),new SourceRange(1,0,1,0)));
        }
        var child=new TemplateSubstitution(types,replacements,packs,List.of(),primaryName,instanceName);
        child.templateNames.putAll(templateNames);child.parameterPacks.putAll(parameterPacks);child.selectedNames.putAll(selectedNames);
        child.deferredParameterPacks.addAll(deferredParameterPacks);
        for(String name:TemplatePacks.names(pattern))if(parameterPacks.containsKey(name))child.selectedNames.put(name,parameterPacks.get(name).get(index));
        return child;
    }
    private Expression.CallExpr copyCall(Expression.CallExpr source) {
        Expression callee=(Expression)copy(source.callee());
        var arguments=new ArrayList<Expression>();var groups=new ArrayList<List<Integer>>();
        for(Expression argument:source.arguments()) {
            List<Expression> expanded=copyList(List.of(argument));
            var group=new ArrayList<Integer>();for(Expression item:expanded){group.add(arguments.size());arguments.add(item);}groups.add(group);
        }
        var order=new ArrayList<Integer>();for(int original:source.argumentEvaluationOrder())order.addAll(groups.get(original));
        var result=new Expression.CallExpr(callee,arguments,order,source.range());copies.put(source,result);origins.put(result,source);return result;
    }

    private static String simple(String name) { return name.substring(name.lastIndexOf("::") + 2); }
}
