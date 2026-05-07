package minic.runtime.debug.visual;

import minic.runtime.debug.DebugMemoryEntry;
import minic.runtime.debug.DebugProcessSpace;
import minic.runtime.debug.DebugStackFrame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从虚拟进程空间和 @visual 注释构建可视化投影。
 */
public final class VisualProjectionBuilder {
    /**
     * 构建投影。
     *
     * @param processSpace 虚拟进程空间
     * @param annotations visual 注释
     * @return 投影
     */
    public VisualProjection build(DebugProcessSpace processSpace, List<VisualAnnotation> annotations) {
        Objects.requireNonNull(processSpace, "processSpace");
        Objects.requireNonNull(annotations, "annotations");
        LinkedHashMap<String, GraphAccumulator> graphs = new LinkedHashMap<>();
        ArrayList<VisualStructure> structures = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        Map<String, DebugMemoryEntry> variables = variables(processSpace);

        for (VisualAnnotation annotation : annotations) {
            switch (annotation.directive()) {
                case "@visual" -> buildRootStructure(annotation, variables, graphs, structures, warnings);
                case "@visual-node" -> graph(graphs, annotation.name(), annotation.attributes().getOrDefault("kind", "graph"))
                        .addNode(nodeFromAnnotation(annotation));
                case "@visual-edge" -> graph(graphs, annotation.name(), annotation.attributes().getOrDefault("kind", "graph"))
                        .addEdge(edgeFromAnnotation(annotation));
                default -> warnings.add("忽略未知 visual 指令：" + annotation.directive());
            }
        }

        graphs.values().stream()
                .map(GraphAccumulator::build)
                .forEach(structures::add);
        return new VisualProjection(structures, warnings);
    }

    private void buildRootStructure(
            VisualAnnotation annotation,
            Map<String, DebugMemoryEntry> variables,
            LinkedHashMap<String, GraphAccumulator> graphs,
            ArrayList<VisualStructure> structures,
            ArrayList<String> warnings
    ) {
        String root = annotation.attributes().get("root");
        DebugMemoryEntry rootEntry = root == null ? null : variables.get(root);
        if (root != null && rootEntry == null) {
            warnings.add("未找到 visual root 变量：" + root);
        }
        switch (annotation.structureType()) {
            case "graph" -> {
                GraphAccumulator accumulator = graph(graphs, annotation.name(), annotation.attributes().getOrDefault("kind", "graph"));
                if (!annotation.attributes().getOrDefault("reveal", "").equals("recursive")) {
                    accumulator.addNode(rootEntry == null
                            ? new GraphNode(annotation.name() + "-root", annotation.name(), "", componentId(annotation.name()), Map.of())
                            : graphNode(rootEntry, annotation.name()));
                }
            }
            case "array" -> structures.add(array(annotation, rootEntry));
            case "composite" -> structures.add(composite(annotation));
            default -> warnings.add("忽略未知 visual 类型：" + annotation.structureType());
        }
    }

    private ArrayStructure array(VisualAnnotation annotation, DebugMemoryEntry rootEntry) {
        String name = annotation.name();
        ArrayCell cell = new ArrayCell(
                name + "-cell-0",
                0,
                0,
                0,
                rootEntry == null ? name : rootEntry.valueSummary(),
                rootEntry == null ? "" : address(rootEntry),
                Map.of("root", annotation.attributes().getOrDefault("root", ""))
        );
        return new ArrayStructure(
                "array-" + name,
                name,
                annotation.attributes().getOrDefault("kind", "array"),
                annotation.attributes().getOrDefault("layout", "linear"),
                1,
                ArrayShape.oneDimensional(1),
                List.of(cell),
                List.of(),
                List.of()
        );
    }

    private CompositeStructure composite(VisualAnnotation annotation) {
        String name = annotation.name();
        CompositePart part = new CompositePart(
                "part-" + name,
                "visual-" + name,
                annotation.attributes().getOrDefault("kind", "composite"),
                Map.of("source", "@visual composite")
        );
        return new CompositeStructure(
                "composite-" + name,
                name,
                annotation.attributes().getOrDefault("kind", "composite"),
                part.id(),
                List.of(part),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private GraphNode graphNode(DebugMemoryEntry entry, String graphName) {
        return new GraphNode(
                graphName + "-node-" + entry.name(),
                entry.name() + "=" + entry.valueSummary(),
                address(entry),
                componentId(graphName),
                Map.of("type", entry.typeName(), "summary", entry.valueSummary())
        );
    }

    private GraphNode nodeFromAnnotation(VisualAnnotation annotation) {
        String id = annotation.attributes().get("id");
        return new GraphNode(
                annotation.name() + "-node-" + id,
                annotation.attributes().getOrDefault("label", id),
                "",
                componentId(annotation.name()),
                Map.copyOf(annotation.attributes())
        );
    }

    private GraphEdge edgeFromAnnotation(VisualAnnotation annotation) {
        String from = annotation.attributes().get("from");
        String to = annotation.attributes().get("to");
        return new GraphEdge(
                annotation.name() + "-edge-" + from + "-" + to,
                annotation.name() + "-node-" + from,
                annotation.name() + "-node-" + to,
                annotation.attributes().getOrDefault("label", ""),
                Boolean.parseBoolean(annotation.attributes().getOrDefault("directed", "true")),
                Map.copyOf(annotation.attributes())
        );
    }

    private Map<String, DebugMemoryEntry> variables(DebugProcessSpace processSpace) {
        LinkedHashMap<String, DebugMemoryEntry> variables = new LinkedHashMap<>();
        for (DebugStackFrame frame : processSpace.stack().frames()) {
            frame.parameters().forEach(entry -> variables.put(entry.name(), entry));
            frame.locals().forEach(entry -> variables.put(entry.name(), entry));
        }
        processSpace.staticData().globals().forEach(entry -> variables.put(entry.name(), entry));
        return variables;
    }

    private GraphAccumulator graph(LinkedHashMap<String, GraphAccumulator> graphs, String name, String kind) {
        return graphs.computeIfAbsent(name, key -> new GraphAccumulator(name, kind));
    }

    private String componentId(String name) {
        return "component-" + name;
    }

    private String address(DebugMemoryEntry entry) {
        return entry.addressOptional()
                .map(minic.runtime.debug.DebugVirtualAddress::display)
                .orElse("");
    }

    private final class GraphAccumulator {
        private final String name;
        private final String kind;
        private final LinkedHashMap<String, GraphNode> nodes = new LinkedHashMap<>();
        private final LinkedHashMap<String, GraphEdge> edges = new LinkedHashMap<>();

        private GraphAccumulator(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }

        private void addNode(GraphNode node) {
            nodes.put(node.id(), node);
        }

        private void addEdge(GraphEdge edge) {
            edges.put(edge.id(), edge);
        }

        private GraphStructure build() {
            GraphComponent component = new GraphComponent(
                    componentId(name),
                    name,
                    nodes.keySet().stream().toList()
            );
            return new GraphStructure(
                    "graph-" + name,
                    name,
                    kind,
                    kind.equals("tree") || kind.equals("binary_tree") ? "hierarchical" : "force",
                    nodes.values().stream().toList(),
                    edges.values().stream().toList(),
                    List.of(component),
                    List.of(),
                    List.of()
            );
        }
    }
}
