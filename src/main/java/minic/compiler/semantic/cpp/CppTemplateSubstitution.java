package minic.compiler.semantic.cpp;

import minic.SourceRange;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.type.MiniType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.*;

/** Instantiation-time source AST copying. Never rewrites tokens or interprets a library name. */
public final class CppTemplateSubstitution {
    private final Map<MiniType.TemplateParameterType, MiniType> arguments;
    private final Map<MiniType.TemplateParameterType, Expression> values;
    private final String primaryName;
    private final String instanceName;
    private final IdentityHashMap<Object, Object> copies = new IdentityHashMap<>();
    private final IdentityHashMap<AstNode, AstNode> origins = new IdentityHashMap<>();

    public CppTemplateSubstitution(Map<MiniType.TemplateParameterType, MiniType> arguments,
                                   String primaryName, String instanceName) {
        this(arguments,Map.of(),primaryName,instanceName);
    }

    public CppTemplateSubstitution(Map<MiniType.TemplateParameterType, MiniType> arguments,
                                   Map<MiniType.TemplateParameterType, Expression> values,
                                   String primaryName, String instanceName) {
        this.arguments = Map.copyOf(arguments);
        this.values = Map.copyOf(values);
        this.primaryName = Objects.requireNonNull(primaryName);
        this.instanceName = Objects.requireNonNull(instanceName);
    }

    public FunctionDecl instantiate(FunctionDecl source) { return (FunctionDecl)copy(source); }
    public ConstructorMember instantiate(ConstructorMember source) { return (ConstructorMember)copy(source); }
    public StructDecl instantiate(StructDecl source) { return (StructDecl) copy(source); }
    public Map<AstNode, AstNode> origins() { return Collections.unmodifiableMap(origins); }

    public MiniType type(MiniType source) {
        return source.substituteTemplateParameters(arguments,values);
    }

    private Object copy(Object source) {
        if (source == null || source instanceof String || source instanceof Number || source instanceof Boolean
                || source instanceof Character || source instanceof Enum<?> || source instanceof SourceRange) return source;
        if (source instanceof MiniType type) return type(type);
        if(source instanceof minic.compiler.type.TemplateArgument.Type argument)return new minic.compiler.type.TemplateArgument.Type(type(argument.type()));
        if(source instanceof minic.compiler.type.TemplateArgument.Value argument)return new minic.compiler.type.TemplateArgument.Value((Expression)copy(argument.expression()));
        if(source instanceof minic.compiler.type.TemplateArgument.Integral argument)return argument;
        if (source instanceof CppTemplateValueExpr value) {
            Expression replacement=values.get(value.parameter());
            if(replacement==null)return new CppTemplateValueExpr(value.parameter(),type(value.valueType()),value.range());
            Expression result=replacement instanceof Expression.IntegerConstantExpr constant
                    ?new Expression.IntegerConstantExpr(constant.value(),constant.type(),constant.lexeme(),value.range())
                    :minic.compiler.type.TemplateValues.substitute(replacement,arguments,values);
            copies.put(source,result);origins.put(result,value);return result;
        }
        Object existing = copies.get(source);
        if (existing != null) return existing;
        if (source instanceof List<?> list) {
            List<?> result = list.stream().map(this::copy).toList();
            copies.put(source, result);
            return result;
        }
        Class<?> kind = source.getClass();
        if (!kind.isRecord() || !kind.getPackageName().equals("minic.compiler.parser.node"))
            throw new IllegalArgumentException("unsupported source component during template substitution: " + kind.getName());
        RecordComponent[] components = kind.getRecordComponents();
        Class<?>[] signature = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        try {
            for (int index = 0; index < components.length; index++) {
                RecordComponent component = components[index];
                signature[index] = component.getType();
                values[index] = copy(component.getAccessor().invoke(source));
                if (component.getName().equals("name")) {
                    if (source instanceof StructDecl record && record.name().equals(primaryName)) values[index] = instanceName;
                    else if (source instanceof ConstructorMember || source instanceof DestructorMember)
                        values[index] = simple(instanceName);
                    else if (source instanceof FunctionDecl function && function.conversionName() != null)
                        values[index] = "operator " + type(function.conversionName().targetType());
                }
            }
            Object result = kind.getConstructor(signature).newInstance(values);
            copies.put(source, result);
            if (source instanceof AstNode original && result instanceof AstNode clone) origins.put(clone, original);
            return result;
        } catch (ReflectiveOperationException error) {
            Throwable cause = error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
            throw new IllegalArgumentException("cannot instantiate source node " + kind.getSimpleName(), cause);
        }
    }

    private static String simple(String name) { return name.substring(name.lastIndexOf("::") + 2); }
}
