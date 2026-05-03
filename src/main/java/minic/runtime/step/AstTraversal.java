package minic.runtime.step;

import minic.compiler.ast.decl.Program;
import minic.source.SourceRange;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * AST 观察遍历工具。
 */
final class AstTraversal {
    private AstTraversal() {
    }

    static List<Object> preorder(Program program) {
        ArrayList<Object> nodes = new ArrayList<>();
        nodes.add(program);
        program.structs().forEach(node -> append(node, nodes));
        program.functions().forEach(node -> append(node, nodes));
        return List.copyOf(nodes);
    }

    static String summary(Object node) {
        if (node instanceof Program program) {
            return "Program structs=" + program.structs().size() + " functions=" + program.functions().size();
        }
        String kind = node.getClass().getSimpleName();
        for (String componentName : List.of("name", "operator", "value", "literalValue")) {
            Object value = componentValue(node, componentName);
            if (value != null) {
                return kind + " " + value;
            }
        }
        return kind;
    }

    static SourceRange range(Object node) {
        if (node instanceof Program program) {
            return program.range();
        }
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            if (component.getName().equals("range")) {
                Object value = read(component, node);
                if (value instanceof SourceRange sourceRange) {
                    return sourceRange;
                }
            }
        }
        return null;
    }

    private static void append(Object node, ArrayList<Object> nodes) {
        nodes.add(node);
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            Object value = read(component, node);
            if (value == null || value instanceof SourceRange) {
                continue;
            }
            if (isAstNode(value)) {
                append(value, nodes);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (isAstNode(item)) {
                        append(item, nodes);
                    }
                }
            }
        }
    }

    private static Object componentValue(Object node, String name) {
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            if (component.getName().equals(name)) {
                Object value = read(component, node);
                if (!(value instanceof SourceRange) && !isAstNode(value) && !(value instanceof List<?>)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Object read(RecordComponent component, Object node) {
        try {
            return component.getAccessor().invoke(node);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("cannot read AST component: " + component.getName(), exception);
        }
    }

    private static boolean isAstNode(Object value) {
        return value != null && value.getClass().getPackageName().startsWith("minic.compiler.ast.");
    }
}
