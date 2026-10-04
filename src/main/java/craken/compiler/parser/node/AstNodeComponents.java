package craken.compiler.parser.node;

import java.lang.reflect.*;
import java.util.*;

/** Business structure for template copying/scanning, independent of record representation. */
public final class AstNodeComponents {
    private AstNodeComponents() {}

    private static final ClassValue<Shape> SHAPES = new ClassValue<>() {
        @Override protected Shape computeValue(Class<?> type) {
            try {
                if (type.isRecord()) {
                    var components = Arrays.stream(type.getRecordComponents())
                            .map(c -> new Component(c.getName(), c.getType(), c.getAccessor())).toList();
                    return new Shape(type.getConstructor(components.stream().map(Component::type).toArray(Class<?>[]::new)), components);
                }
                if (!AbstractAstNode.class.isAssignableFrom(type))
                    throw new IllegalArgumentException("Not a structural AST class: " + type.getName());
                var constructors = Arrays.stream(type.getConstructors())
                        .filter(c -> c.isAnnotationPresent(AstNodeConstructor.class)).toList();
                if (constructors.size() != 1)
                    throw new IllegalArgumentException("Exactly one business constructor required: " + type.getName());
                var constructor = constructors.getFirst();
                var names = constructor.getAnnotation(AstNodeConstructor.class).value();
                var types = constructor.getParameterTypes();
                if (names.length != types.length || new HashSet<>(Arrays.asList(names)).size() != names.length)
                    throw new IllegalArgumentException("Invalid business component names: " + type.getName());
                var components = new ArrayList<Component>();
                for (int index = 0; index < names.length; index++) {
                    var accessor = type.getMethod(names[index]);
                    if (accessor.getReturnType() != types[index])
                        throw new IllegalArgumentException("Business accessor type mismatch: " + names[index]);
                    components.add(new Component(names[index], types[index], accessor));
                }
                return new Shape(constructor, components);
            } catch (ReflectiveOperationException error) {
                throw new IllegalArgumentException("Cannot describe AST business structure: " + type.getName(), error);
            }
        }
    };

    public static Shape describe(Class<?> type) { return SHAPES.get(type); }
    public record Component(String name, Class<?> type, Method accessor) {}
    public record Shape(Constructor<?> constructor, List<Component> components) {
        public Shape { components = List.copyOf(components); }
    }
}
