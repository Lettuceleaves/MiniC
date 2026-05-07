package minic.uiapi;

import minic.compiler.ast.decl.Program;
import minic.source.SourceRange;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 将内部 AST record 转换为 UI 专用树 DTO。
 */
final class UiAstVisualBuilder {
    private int nextId;

    UiAstNodeVisualDto buildProgram(Program program, Object activeNode) {
        return buildProgram(program, activeNode, null);
    }

    UiAstNodeVisualDto buildProgram(Program program, Object activeNode, List<Object> visibleNodes) {
        Set<Object> visibleNodeSet = null;
        if (visibleNodes != null) {
            visibleNodeSet = Collections.newSetFromMap(new IdentityHashMap<>());
            visibleNodeSet.addAll(visibleNodes);
        }
        ArrayList<UiAstNodeVisualDto> children = new ArrayList<>();
        Set<Object> finalVisibleNodeSet = visibleNodeSet;
        program.structs().forEach(struct -> addVisibleChild(children, struct, activeNode, finalVisibleNodeSet));
        program.functions().forEach(function -> addVisibleChild(children, function, activeNode, finalVisibleNodeSet));
        return new UiAstNodeVisualDto(
                "ast-root",
                "Program",
                "Program",
                UiSourceSpanDto.from(program.range()),
                program.equals(activeNode),
                children
        );
    }

    private void addVisibleChild(
            ArrayList<UiAstNodeVisualDto> children,
            Object node,
            Object activeNode,
            Set<Object> visibleNodeSet
    ) {
        if (visibleNodeSet == null || visibleNodeSet.contains(node)) {
            children.add(buildNode(node, activeNode, visibleNodeSet));
        }
    }

    private UiAstNodeVisualDto buildNode(Object node, Object activeNode, Set<Object> visibleNodeSet) {
        String id = "ast-" + nextId++;
        ArrayList<UiAstNodeVisualDto> children = new ArrayList<>();
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            Object value = read(component, node);
            if (value == null || value instanceof SourceRange) {
                continue;
            }
            if (isAstNode(value)) {
                addVisibleChild(children, value, activeNode, visibleNodeSet);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (isAstNode(item)) {
                        addVisibleChild(children, item, activeNode, visibleNodeSet);
                    }
                }
            }
        }
        return new UiAstNodeVisualDto(
                id,
                label(node),
                kind(node),
                range(node),
                node.equals(activeNode),
                children
        );
    }

    private Object read(RecordComponent component, Object node) {
        try {
            return component.getAccessor().invoke(node);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("cannot read AST component: " + component.getName(), exception);
        }
    }

    private String label(Object node) {
        String kind = kind(node);
        for (String componentName : List.of("name", "operator", "value", "literalValue")) {
            Object value = componentValue(node, componentName);
            if (value != null) {
                return kind + " " + value;
            }
        }
        return kind;
    }

    private Object componentValue(Object node, String name) {
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

    private String kind(Object node) {
        return node.getClass().getSimpleName();
    }

    private UiSourceSpanDto range(Object node) {
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            if (component.getName().equals("range")) {
                Object value = read(component, node);
                if (value instanceof SourceRange sourceRange) {
                    return UiSourceSpanDto.from(sourceRange);
                }
            }
        }
        return null;
    }

    private boolean isAstNode(Object value) {
        return value != null && value.getClass().getPackageName().startsWith("minic.compiler.ast.");
    }
}
