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
        return build(processSpace, annotations, List.of(), Long.MAX_VALUE);
    }

    /**
     * 构建投影，并把运行时可视化事件重放到指定快照。
     *
     * @param processSpace 虚拟进程空间
     * @param annotations visual 注释
     * @param events 可视化事件日志
     * @param snapshotId 当前快照 ID
     * @return 投影
     */
    public VisualProjection build(
            DebugProcessSpace processSpace,
            List<VisualAnnotation> annotations,
            List<VisualEvent> events,
            long snapshotId
    ) {
        Objects.requireNonNull(processSpace, "processSpace");
        Objects.requireNonNull(annotations, "annotations");
        Objects.requireNonNull(events, "events");
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
                case "@visual-map" -> {
                }
                default -> warnings.add("忽略未知 visual 指令：" + annotation.directive());
            }
        }
        replayEvents(events, snapshotId, graphs);

        graphs.values().stream()
                .map(GraphAccumulator::build)
                .forEach(structures::add);
        return new VisualProjection(structures, warnings);
    }

    private void replayEvents(
            List<VisualEvent> events,
            long snapshotId,
            LinkedHashMap<String, GraphAccumulator> graphs
    ) {
        events.stream()
                .filter(event -> event.snapshotId() <= snapshotId)
                .forEach(event -> {
                    GraphAccumulator graph = graph(graphs, event.graphName(), "tree");
                    switch (event.type()) {
                        case NODE_CREATED, NODE_UPDATED -> graph.addNode(nodeFromEvent(event));
                        case EDGE_SET -> {
                            if (isNullId(event.toId())) {
                                graph.addNode(nullNodeFromEvent(event));
                            }
                            graph.addEdge(edgeFromEvent(event));
                        }
                        case EDGE_REMOVED -> graph.removeEdge(event.key(), event.fromId(), event.toId());
                        case META_SET -> graph.mergeMetadata(event.nodeId(), event.metadata());
                    }
                });
    }

    private GraphNode nodeFromEvent(VisualEvent event) {
        String label = event.label().isBlank() ? event.nodeId() : event.label();
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>(event.metadata());
        metadata.putIfAbsent("id", event.nodeId());
        return new GraphNode(
                event.graphName() + "-node-" + event.nodeId(),
                label,
                "",
                componentId(event.graphName()),
                metadata
        );
    }

    private GraphEdge edgeFromEvent(VisualEvent event) {
        String toId = visualToId(event);
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("key", event.key());
        metadata.put("from", event.fromId());
        metadata.put("to", toId);
        if (isNullId(event.toId())) {
            metadata.put("visual-null-edge", "true");
        }
        return new GraphEdge(
                edgeId(event.graphName(), event.key(), event.fromId(), toId),
                event.graphName() + "-node-" + event.fromId(),
                event.graphName() + "-node-" + toId,
                event.label().isBlank() ? event.key() : event.label(),
                true,
                metadata
        );
    }

    private GraphNode nullNodeFromEvent(VisualEvent event) {
        String nodeId = visualNullNodeId(event);
        return new GraphNode(
                event.graphName() + "-node-" + nodeId,
                "null",
                "",
                componentId(event.graphName()),
                Map.of(
                        "id", nodeId,
                        "visual-null", "true",
                        "from", event.fromId(),
                        "key", event.key()
                )
        );
    }

    private void buildRootStructure(
            VisualAnnotation annotation,
            Map<String, DebugMemoryEntry> variables,
            LinkedHashMap<String, GraphAccumulator> graphs,
            ArrayList<VisualStructure> structures,
            ArrayList<String> warnings
    ) {
        if (annotation.attributes().getOrDefault("mode", "").equals("runtime")) {
            graph(graphs, annotation.name(), annotation.attributes().getOrDefault("kind", "graph"));
            return;
        }
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
            edges.entrySet().removeIf(entry ->
                    edgeRole(entry.getValue()).equals(edgeRole(edge))
                            && entry.getValue().metadata().getOrDefault("from", "").equals(edge.metadata().getOrDefault("from", "")));
            edges.put(edge.id(), edge);
        }

        private void removeEdge(String key, String fromId, String toId) {
            edges.remove(edgeId(name, key, fromId, toId));
        }

        private void mergeMetadata(String nodeId, Map<String, String> metadata) {
            String id = name + "-node-" + nodeId;
            GraphNode existing = nodes.get(id);
            if (existing == null) {
                return;
            }
            LinkedHashMap<String, String> merged = new LinkedHashMap<>(existing.metadata());
            merged.putAll(metadata);
            nodes.put(id, new GraphNode(
                    existing.id(),
                    existing.label(),
                    existing.valueRef(),
                    existing.componentId(),
                    merged
            ));
        }

        private GraphStructure build() {
            java.util.HashSet<String> edgeTargetIds = edges.values().stream()
                    .map(GraphEdge::toNodeId)
                    .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
            List<GraphNode> visibleNodes = nodes.values().stream()
                    .filter(node -> !Boolean.parseBoolean(node.metadata().getOrDefault("visual-null", "false"))
                            || edgeTargetIds.contains(node.id()))
                    .toList();
            GraphComponent component = new GraphComponent(
                    componentId(name),
                    name,
                    visibleNodes.stream().map(GraphNode::id).toList()
            );
            return new GraphStructure(
                    "graph-" + name,
                    name,
                    kind,
                    kind.equals("tree") || kind.equals("binary_tree") ? "hierarchical" : "force",
                    visibleNodes,
                    edges.values().stream().toList(),
                    List.of(component),
                    List.of(),
                    List.of()
            );
        }
    }

    private String edgeId(String graphName, String key, String fromId, String toId) {
        return graphName + "-edge-" + key + "-" + fromId + "-" + toId;
    }

    private String edgeRole(GraphEdge edge) {
        return edge.metadata().getOrDefault("key", edge.label());
    }

    private String visualToId(VisualEvent event) {
        return isNullId(event.toId()) ? visualNullNodeId(event) : event.toId();
    }

    private String visualNullNodeId(VisualEvent event) {
        return "null-" + event.fromId() + "-" + event.key();
    }

    private boolean isNullId(String id) {
        return id.equals("0") || id.equals("null");
    }
}
