package minic.runtime.debug.visual;

import minic.runtime.debug.DebugMemoryEntry;
import minic.runtime.debug.DebugProcessSpace;
import minic.runtime.debug.DebugStackFrame;
import minic.runtime.debug.memory.TypedMemoryElement;
import minic.runtime.debug.memory.TypedMemoryField;
import minic.runtime.debug.memory.TypedMemoryGraph;
import minic.runtime.debug.memory.TypedMemoryGraphBuilder;
import minic.runtime.debug.memory.TypedMemoryNode;
import minic.runtime.debug.memory.TypeShape;
import minic.runtime.debug.visual.grid.GridScene;
import minic.runtime.debug.visual.grid.GridSceneEdge;
import minic.runtime.debug.visual.grid.GridSceneGraphAdapter;
import minic.runtime.debug.visual.grid.GridSceneNode;
import minic.runtime.debug.visual.layout.LayoutInput;
import minic.runtime.debug.visual.layout.LayoutPlan;
import minic.runtime.debug.visual.layout.NaturalLayoutStrategy;
import minic.runtime.debug.visual.layout.PlacedEdge;
import minic.runtime.debug.visual.layout.PlacedNode;
import minic.runtime.debug.visual.layout.UnidirectionalLayoutStrategy;
import minic.runtime.debug.visual.layout.VisualMemoryEdge;
import minic.runtime.debug.visual.layout.VisualMemoryMirror;
import minic.runtime.debug.visual.layout.VisualMemoryNode;
import minic.runtime.debug.visual.layout.VisualMemoryNodeRole;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从虚拟进程空间和 @visual 注释构建可视化投影。
 */
public final class VisualProjectionBuilder {
    private static final int DEFAULT_MAX_DEPTH = 64;
    private static final int STRUCT_FIELD_TEXT_CAPACITY = 12;
    private final VisualStyleRuleResolver styleRuleResolver = new VisualStyleRuleResolver();

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
        return build(processSpace, annotations, List.of(), events, snapshotId);
    }

    /**
     * 构建投影，并合并旧注释、typed visual specs 和运行时事件。
     *
     * @param processSpace 虚拟进程空间
     * @param annotations legacy visual 注释
     * @param specs typed visual specs
     * @param events 可视化事件日志
     * @param snapshotId 当前快照 ID
     * @return 投影
     */
    public VisualProjection build(
            DebugProcessSpace processSpace,
            List<VisualAnnotation> annotations,
            List<VisualSpec> specs,
            List<VisualEvent> events,
            long snapshotId
    ) {
        Objects.requireNonNull(processSpace, "processSpace");
        Objects.requireNonNull(annotations, "annotations");
        Objects.requireNonNull(specs, "specs");
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
        for (VisualSpec spec : specs) {
            if (spec.layout().equals("natural")) {
                buildNaturalLayout(processSpace, spec, structures, warnings);
            } else if (spec.layout().equals("unidirectional")) {
                buildUnidirectionalLayout(processSpace, spec, structures, warnings);
            } else {
                warnings.add("visual " + spec.name() + " layout 不支持：" + spec.layout());
            }
        }
        replayEvents(events, snapshotId, graphs);

        graphs.values().stream()
                .map(GraphAccumulator::build)
                .forEach(structures::add);
        return new VisualProjection(structures, warnings);
    }

    private void buildUnidirectionalLayout(
            DebugProcessSpace processSpace,
            VisualSpec spec,
            ArrayList<VisualStructure> structures,
            ArrayList<String> warnings
    ) {
        TypedMemoryGraph memoryGraph = TypedMemoryGraphBuilder.build(processSpace);
        TypedNodeIndex index = TypedNodeIndex.from(memoryGraph);
        ArrayList<TypedMemoryNode> rootNodes = new ArrayList<>();
        for (String rootName : spec.roots()) {
            TypedMemoryNode root = memoryGraph.findRoot(rootName).orElse(null);
            TypedMemoryNode objectRoot = objectNode(root, index);
            if (objectRoot == null || objectRoot.address() == null) {
                warnings.add("未找到 visual root 变量：" + rootName);
                continue;
            }
            rootNodes.add(objectRoot);
        }
        if (rootNodes.isEmpty()) {
            return;
        }
        LayoutProjection projection = layoutProjection(rootNodes, index);
        LayoutPlan plan = new UnidirectionalLayoutStrategy().build(new LayoutInput(
                rootNodes.stream().map(TypedMemoryNode::address).toList(),
                projection.mirror(),
                spec.style()
        ));
        structures.add(GridSceneGraphAdapter.toGraphStructure(gridScene(spec, plan, projection)));
    }

    private void buildNaturalLayout(
            DebugProcessSpace processSpace,
            VisualSpec spec,
            ArrayList<VisualStructure> structures,
            ArrayList<String> warnings
    ) {
        TypedMemoryGraph memoryGraph = TypedMemoryGraphBuilder.build(processSpace);
        TypedNodeIndex index = TypedNodeIndex.from(memoryGraph);
        NaturalMirrorBuild mirrorBuild = naturalMirror(spec, memoryGraph, index, warnings);
        if (mirrorBuild.rootAddresses().isEmpty()) {
            return;
        }
        LayoutProjection projection = new LayoutProjection(
                new VisualMemoryMirror(
                        mirrorBuild.nodes().values().stream().toList(),
                        mirrorBuild.edges()
                ),
                mirrorBuild.typedByAddress(),
                mirrorBuild.edgeLabels()
        );
        LayoutPlan plan = new NaturalLayoutStrategy().build(new LayoutInput(
                mirrorBuild.rootAddresses(),
                projection.mirror(),
                spec.style()
        ));
        structures.add(GridSceneGraphAdapter.toGraphStructure(gridScene(spec, "natural", plan, projection)));
    }

    private TypedMemoryNode objectNode(TypedMemoryNode root, TypedNodeIndex index) {
        if (root == null) {
            return null;
        }
        if (root.shape() == TypeShape.POINTER && root.pointerTarget() != null) {
            return index.nodeByAddress(root.pointerTarget());
        }
        return root.address() == null ? null : root;
    }

    private NaturalMirrorBuild naturalMirror(
            VisualSpec spec,
            TypedMemoryGraph memoryGraph,
            TypedNodeIndex index,
            ArrayList<String> warnings
    ) {
        LinkedHashMap<String, VisualMemoryNode> nodes = new LinkedHashMap<>();
        ArrayList<VisualMemoryEdge> edges = new ArrayList<>();
        LinkedHashMap<String, TypedMemoryNode> typedByAddress = new LinkedHashMap<>();
        LinkedHashMap<String, String> edgeLabels = new LinkedHashMap<>();
        ArrayList<String> rootAddresses = new ArrayList<>();
        LinkedHashSet<String> capturedTypes = spec.captureTypes().stream()
                .map(this::canonicalType)
                .filter(type -> !type.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String rootName : spec.roots()) {
            TypedMemoryNode root = memoryGraph.findRoot(rootName).orElse(null);
            if (root == null) {
                warnings.add("未找到 visual root 变量：" + rootName);
                continue;
            }
            if (root.shape() == TypeShape.ARRAY) {
                addArrayCells(spec, root, nodes, typedByAddress, rootAddresses, warnings);
                continue;
            }
            TypedMemoryNode objectRoot = objectNode(root, index);
            if (objectRoot == null || objectRoot.address() == null) {
                warnings.add("未找到 visual root 变量：" + rootName);
                continue;
            }
            capturedTypes.add(canonicalType(objectRoot.typeName()));
            addObjectNode(spec, objectRoot, nodes, typedByAddress, capturedTypeName(objectRoot, capturedTypes), warnings);
            rootAddresses.add(objectRoot.address());
        }

        ArrayDeque<String> queue = new ArrayDeque<>(rootAddresses);
        HashSet<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String address = queue.removeFirst();
            if (!visited.add(address)) {
                continue;
            }
            TypedMemoryNode current = typedByAddress.get(address);
            if (current == null) {
                continue;
            }
            for (PointerTarget target : pointerTargets(current, index)) {
                TypedMemoryNode targetNode = target.node();
                String capturedType = capturedTypeName(targetNode, capturedTypes);
                if (capturedType.isBlank()) {
                    continue;
                }
                addObjectNode(spec, targetNode, nodes, typedByAddress, capturedType, warnings);
                edges.add(new VisualMemoryEdge(address, targetNode.address()));
                edgeLabels.putIfAbsent(edgeKey(address, targetNode.address()), target.label());
                queue.add(targetNode.address());
            }
        }
        return new NaturalMirrorBuild(nodes, edges, typedByAddress, edgeLabels, List.copyOf(rootAddresses));
    }

    private void addObjectNode(
            VisualSpec spec,
            TypedMemoryNode node,
            LinkedHashMap<String, VisualMemoryNode> nodes,
            LinkedHashMap<String, TypedMemoryNode> typedByAddress,
            ArrayList<String> warnings
    ) {
        addObjectNode(spec, node, nodes, typedByAddress, "", warnings);
    }

    private void addObjectNode(
            VisualSpec spec,
            TypedMemoryNode node,
            LinkedHashMap<String, VisualMemoryNode> nodes,
            LinkedHashMap<String, TypedMemoryNode> typedByAddress,
            String capturedType,
            ArrayList<String> warnings
    ) {
        if (node.address() == null) {
            return;
        }
        String displayType = capturedType == null || capturedType.isBlank() ? node.typeName() : capturedType;
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("visual-shape", "RECT");
        metadata.put("path", node.name());
        metadata.put("type", displayType);
        metadata.put("runtimeType", node.typeName());
        metadata.put("address", node.address());
        metadata.put("valueSummary", node.valueSummary());
        addStructRows(node, metadata);
        node.fields().forEach(field -> {
            String value = field.value().valueSummary();
            if (value != null && !value.isBlank()) {
                metadata.put("field." + field.name(), value);
            }
            metadata.put("fieldType." + field.name(), field.value().typeName());
        });
        styleRuleResolver.apply(spec, node, displayType, metadata, warnings);
        nodes.putIfAbsent(node.address(), new VisualMemoryNode(
                node.address(),
                estimatedByteCount(node),
                VisualMemoryNodeRole.OBJECT,
                metadata
        ));
        typedByAddress.putIfAbsent(node.address(), node);
    }

    private void addStructRows(TypedMemoryNode node, LinkedHashMap<String, String> metadata) {
        if (node.fields().isEmpty()) {
            return;
        }
        metadata.put("visual-content", "STRUCT_TABLE");
        metadata.put("visual-row-count", Integer.toString(node.fields().size()));
        metadata.put("visual-row-capacity", Integer.toString(STRUCT_FIELD_TEXT_CAPACITY));
        metadata.put("fieldNames", node.fields().stream()
                .map(TypedMemoryField::name)
                .collect(Collectors.joining(",")));
        for (int index = 0; index < node.fields().size(); index++) {
            TypedMemoryField field = node.fields().get(index);
            metadata.put("visual-row-name." + index, field.name());
            metadata.put("visual-row-type." + index, field.value().typeName());
            metadata.put("visual-row." + index, fitText(field.value().valueSummary(), STRUCT_FIELD_TEXT_CAPACITY));
        }
    }

    private String fitText(String value, int capacity) {
        if (capacity < 3) {
            throw new IllegalArgumentException("capacity must be at least 3");
        }
        String compact = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= capacity) {
            return compact;
        }
        if (capacity == 3) {
            return "...";
        }
        return compact.substring(0, capacity - 3) + "...";
    }

    private void addArrayCells(
            VisualSpec spec,
            TypedMemoryNode root,
            LinkedHashMap<String, VisualMemoryNode> nodes,
            LinkedHashMap<String, TypedMemoryNode> typedByAddress,
            ArrayList<String> rootAddresses,
            ArrayList<String> warnings
    ) {
        if (root.elements().stream().anyMatch(element -> element.value().shape() == TypeShape.ARRAY
                && element.value().elements().stream().anyMatch(inner -> inner.value().shape() == TypeShape.ARRAY))) {
            warnings.add("visual " + spec.name() + " 不支持连续第三维数组");
            return;
        }
        if (root.elements().stream().anyMatch(element -> element.value().shape() == TypeShape.ARRAY)) {
            int rows = root.elements().size();
            int columns = root.elements().stream()
                    .map(TypedMemoryElement::value)
                    .mapToInt(value -> value.elements().size())
                    .max()
                    .orElse(0);
            for (TypedMemoryElement rowElement : root.elements()) {
                int row = Math.toIntExact(rowElement.index());
                for (TypedMemoryElement columnElement : rowElement.value().elements()) {
                    addArrayCell(root, columnElement.value(), row, Math.toIntExact(columnElement.index()), rows, columns,
                            nodes, typedByAddress, rootAddresses);
                }
            }
            return;
        }
        int size = root.elements().size();
        int columns = Math.max(1, metadataInt(spec.attributes(), "columns", size));
        int rows = Math.max(1, metadataInt(spec.attributes(), "rows", (int) Math.ceil((double) size / columns)));
        for (TypedMemoryElement element : root.elements()) {
            int index = Math.toIntExact(element.index());
            addArrayCell(root, element.value(), index / columns, index % columns, rows, columns,
                    nodes, typedByAddress, rootAddresses);
        }
    }

    private void addArrayCell(
            TypedMemoryNode arrayRoot,
            TypedMemoryNode value,
            int row,
            int column,
            int rows,
            int columns,
            LinkedHashMap<String, VisualMemoryNode> nodes,
            LinkedHashMap<String, TypedMemoryNode> typedByAddress,
            ArrayList<String> rootAddresses
    ) {
        String address = value.address() == null
                ? arrayRoot.address() + "[" + row + "," + column + "]"
                : value.address();
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("visual-shape", "SQUARE");
        metadata.put("path", value.name());
        metadata.put("type", value.typeName());
        metadata.put("address", address);
        metadata.put("valueSummary", value.valueSummary());
        metadata.put("row", Integer.toString(row));
        metadata.put("column", Integer.toString(column));
        metadata.put("rows", Integer.toString(rows));
        metadata.put("columns", Integer.toString(columns));
        nodes.putIfAbsent(address, new VisualMemoryNode(
                address,
                estimatedByteCount(value),
                VisualMemoryNodeRole.ARRAY_CELL,
                metadata
        ));
        typedByAddress.putIfAbsent(address, value);
        rootAddresses.add(address);
    }

    private LayoutProjection layoutProjection(List<TypedMemoryNode> roots, TypedNodeIndex index) {
        LinkedHashMap<String, VisualMemoryNode> nodes = new LinkedHashMap<>();
        ArrayList<VisualMemoryEdge> edges = new ArrayList<>();
        LinkedHashMap<String, TypedMemoryNode> typedByAddress = new LinkedHashMap<>();
        LinkedHashMap<String, String> edgeLabels = new LinkedHashMap<>();
        ArrayDeque<TypedMemoryNode> queue = new ArrayDeque<>(roots);
        HashSet<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            TypedMemoryNode current = queue.removeFirst();
            if (current.address() == null || !visited.add(current.address())) {
                continue;
            }
            typedByAddress.put(current.address(), current);
            nodes.putIfAbsent(current.address(), new VisualMemoryNode(current.address(), estimatedByteCount(current)));
            for (PointerTarget target : pointerTargets(current, index)) {
                edges.add(new VisualMemoryEdge(current.address(), target.node().address()));
                edgeLabels.putIfAbsent(edgeKey(current.address(), target.node().address()), target.label());
                queue.add(target.node());
            }
        }
        return new LayoutProjection(
                new VisualMemoryMirror(nodes.values().stream().toList(), edges),
                typedByAddress,
                edgeLabels
        );
    }

    private List<PointerTarget> pointerTargets(TypedMemoryNode node, TypedNodeIndex index) {
        ArrayList<PointerTarget> targets = new ArrayList<>();
        if (node.shape() == TypeShape.POINTER) {
            addPointerTarget(targets, node.name(), node, index);
        }
        for (TypedMemoryField field : node.fields()) {
            addPointerTarget(targets, field.name(), field.value(), index);
        }
        for (TypedMemoryElement element : node.elements()) {
            addPointerTarget(targets, "[" + element.index() + "]", element.value(), index);
        }
        return targets;
    }

    private void addPointerTarget(
            ArrayList<PointerTarget> targets,
            String label,
            TypedMemoryNode pointer,
            TypedNodeIndex index
    ) {
        String pointerTarget = pointer.pointerTarget();
        if (pointerTarget == null && pointer.shape() == TypeShape.POINTER && isAddressLike(pointer.valueSummary())) {
            pointerTarget = pointer.valueSummary();
        }
        if (pointerTarget == null) {
            return;
        }
        TypedMemoryNode target = index.nodeByAddress(pointerTarget);
        if (target == null || target.address() == null) {
            return;
        }
        targets.add(new PointerTarget(label, target));
    }

    private boolean isAddressLike(String value) {
        return value != null && !value.isBlank() && !value.equals("null") && !value.equals("0");
    }

    private int estimatedByteCount(TypedMemoryNode node) {
        return 16;
    }

    private GridScene gridScene(VisualSpec spec, LayoutPlan plan, LayoutProjection projection) {
        return gridScene(spec, "unidirectional", plan, projection);
    }

    private GridScene gridScene(VisualSpec spec, String kind, LayoutPlan plan, LayoutProjection projection) {
        LinkedHashMap<String, PlacedNode> placedNodes = new LinkedHashMap<>();
        for (PlacedNode node : plan.nodes()) {
            placedNodes.put(node.address(), node);
        }
        List<GridSceneNode> nodes = plan.nodes().stream()
                .map(placed -> gridNode(spec, placed, projection))
                .toList();
        List<GridSceneEdge> edges = plan.edges().stream()
                .map(edge -> gridEdge(spec, edge, placedNodes, projection))
                .toList();
        return new GridScene("grid-" + spec.name(), spec.name(), kind, nodes, edges);
    }

    private GridSceneNode gridNode(VisualSpec spec, PlacedNode placed, LayoutProjection projection) {
        TypedMemoryNode typed = projection.typedByAddress().get(placed.address());
        VisualMemoryNode visualNode = projection.mirror().node(placed.address()).orElse(null);
        String visualId = sanitize(placed.address());
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>(visualNode == null ? Map.of() : visualNode.metadata());
        metadata.put("id", visualId);
        metadata.put("root", spec.root());
        metadata.putIfAbsent("path", typed == null ? placed.address() : typed.name());
        metadata.putIfAbsent("type", typed == null ? "" : typed.typeName());
        metadata.put("address", placed.address());
        metadata.putIfAbsent("valueSummary", typed == null ? "" : typed.valueSummary());
        String label = typed == null ? placed.address() : typed.name();
        if (metadata.getOrDefault("visual-shape", "").equals("SQUARE")
                && !metadata.getOrDefault("valueSummary", "").isBlank()) {
            label = metadata.get("valueSummary");
        }
        return new GridSceneNode(
                spec.name() + "-node-" + visualId,
                label,
                placed.address(),
                placed.bounds(),
                metadata
        );
    }

    private GridSceneEdge gridEdge(
            VisualSpec spec,
            PlacedEdge edge,
            Map<String, PlacedNode> placedNodes,
            LayoutProjection projection
    ) {
        String fromId = sanitize(edge.fromAddress());
        String toId = sanitize(edge.toAddress());
        String label = projection.edgeLabels().getOrDefault(edgeKey(edge.fromAddress(), edge.toAddress()), "");
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("from", fromId);
        metadata.put("to", toId);
        metadata.put("key", label);
        metadata.put("edge-role", "primary");
        metadata.put("fromAddress", edge.fromAddress());
        metadata.put("toAddress", edge.toAddress());
        metadata.put("fromPath", nodePath(projection.typedByAddress().get(edge.fromAddress()), edge.fromAddress()));
        metadata.put("toPath", nodePath(projection.typedByAddress().get(edge.toAddress()), edge.toAddress()));
        return new GridSceneEdge(
                spec.name() + "-edge-" + fromId + "-" + toId,
                placedNodes.get(edge.fromAddress()).address().equals(edge.fromAddress()) ? spec.name() + "-node-" + fromId : fromId,
                placedNodes.get(edge.toAddress()).address().equals(edge.toAddress()) ? spec.name() + "-node-" + toId : toId,
                label,
                edge.fromAnchor(),
                edge.toAnchor(),
                edge.start(),
                edge.end(),
                metadata
        );
    }

    private String nodePath(TypedMemoryNode node, String fallback) {
        return node == null ? fallback : node.name();
    }

    private String edgeKey(String fromAddress, String toAddress) {
        return fromAddress + "->" + toAddress;
    }

    private int metadataInt(Map<String, String> metadata, String key, int fallback) {
        try {
            return Integer.parseInt(metadata.getOrDefault(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String canonicalType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return "";
        }
        String normalized = typeName.trim()
                .replaceAll("\\bstruct\\s+", "")
                .replaceAll("\\[[^]]*]", "")
                .replace("*", "")
                .replaceAll("\\s+", " ")
                .trim();
        int space = normalized.indexOf(' ');
        return space < 0 ? normalized : normalized.substring(space + 1).trim().toLowerCase(Locale.ROOT).equals("const")
                ? normalized.substring(0, space)
                : normalized;
    }

    private String capturedTypeName(TypedMemoryNode target, Set<String> capturedTypes) {
        String canonical = canonicalType(target.typeName());
        if (target.shape() == TypeShape.STRUCT && (canonical.isBlank() || canonical.equals("struct"))) {
            return capturedTypes.stream()
                    .filter(type -> !type.equals("struct") && !isPrimitiveType(type))
                    .findFirst()
                    .orElse(capturedTypes.contains(canonical) ? canonical : "");
        }
        if (capturedTypes.contains(canonical)) {
            return canonical;
        }
        return "";
    }

    private boolean isPrimitiveType(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        return normalized.equals("int")
                || normalized.equals("long")
                || normalized.equals("char")
                || normalized.equals("bool")
                || normalized.equals("float")
                || normalized.equals("double")
                || normalized.equals("pointer");
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
            boolean[] rewired = {false};
            edges.entrySet().removeIf(entry -> {
                boolean sameRoleAndSource = edgeRole(entry.getValue()).equals(edgeRole(edge))
                        && entry.getValue().metadata().getOrDefault("from", "").equals(edge.metadata().getOrDefault("from", ""));
                if (sameRoleAndSource
                        && !entry.getValue().metadata().getOrDefault("to", "").equals(edge.metadata().getOrDefault("to", ""))) {
                    rewired[0] = true;
                }
                return sameRoleAndSource;
            });
            edges.put(edge.id(), rewired[0] ? edgeWithVisualState(edge, "rewire") : edge);
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
            GraphStateResult state = applyGraphStates(visibleNodes, edges.values().stream().toList());
            GraphComponent component = new GraphComponent(
                    componentId(name),
                    name,
                    state.nodes().stream().map(GraphNode::id).toList()
            );
            return new GraphStructure(
                    "graph-" + name,
                    name,
                    kind,
                    "force",
                    state.nodes(),
                    state.edges(),
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

    private GraphStateResult applyGraphStates(List<GraphNode> nodes, List<GraphEdge> edges) {
        LinkedHashMap<String, GraphNode> nodeByVisualId = new LinkedHashMap<>();
        for (GraphNode node : nodes) {
            nodeByVisualId.put(nodeVisualId(node), node);
        }
        LinkedHashMap<String, Integer> incomingCounts = new LinkedHashMap<>();
        LinkedHashMap<String, ArrayList<String>> adjacency = new LinkedHashMap<>();
        for (GraphEdge edge : edges) {
            String from = edgeFromVisualId(edge);
            String to = edgeToVisualId(edge);
            if (from.isBlank() || to.isBlank() || isVisualNullNode(to, nodeByVisualId)) {
                continue;
            }
            incomingCounts.merge(to, 1, Integer::sum);
            adjacency.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
        }
        List<GraphEdge> statefulEdges = edges.stream()
                .map(edge -> edgeWithInferredState(edge, adjacency, nodeByVisualId))
                .toList();
        List<GraphNode> statefulNodes = nodes.stream()
                .map(node -> incomingCounts.getOrDefault(nodeVisualId(node), 0) > 1
                        ? nodeWithVisualState(node, "shared-node")
                        : node)
                .toList();
        return new GraphStateResult(statefulNodes, statefulEdges);
    }

    private GraphEdge edgeWithInferredState(
            GraphEdge edge,
            Map<String, ArrayList<String>> adjacency,
            Map<String, GraphNode> nodeByVisualId
    ) {
        String from = edgeFromVisualId(edge);
        String to = edgeToVisualId(edge);
        if (from.isBlank() || to.isBlank() || isVisualNullNode(to, nodeByVisualId)) {
            return edge;
        }
        if (from.equals(to)) {
            return edgeWithVisualState(edge, "self-loop");
        }
        if (reaches(to, from, adjacency, new HashSet<>())) {
            return edgeWithVisualState(edge, "cycle");
        }
        return edge;
    }

    private boolean reaches(
            String current,
            String target,
            Map<String, ArrayList<String>> adjacency,
            Set<String> visited
    ) {
        if (!visited.add(current)) {
            return false;
        }
        for (String next : adjacency.getOrDefault(current, new ArrayList<>())) {
            if (next.equals(target) || reaches(next, target, adjacency, visited)) {
                return true;
            }
        }
        return false;
    }

    private String nodeVisualId(GraphNode node) {
        return node.metadata().getOrDefault("id", simpleGraphId(node.id()));
    }

    private String edgeFromVisualId(GraphEdge edge) {
        return edge.metadata().getOrDefault("from", simpleGraphId(edge.fromNodeId()));
    }

    private String edgeToVisualId(GraphEdge edge) {
        return edge.metadata().getOrDefault("to", simpleGraphId(edge.toNodeId()));
    }

    private boolean isVisualNullNode(String visualId, Map<String, GraphNode> nodeByVisualId) {
        GraphNode node = nodeByVisualId.get(visualId);
        return node != null && Boolean.parseBoolean(node.metadata().getOrDefault("visual-null", "false"));
    }

    private String simpleGraphId(String id) {
        int index = id.lastIndexOf('-');
        return index < 0 ? id : id.substring(index + 1);
    }

    private String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private GraphNode nodeWithVisualState(GraphNode node, String state) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>(node.metadata());
        putVisualState(metadata, state);
        return new GraphNode(node.id(), node.label(), node.valueRef(), node.componentId(), metadata);
    }

    private GraphEdge edgeWithVisualState(GraphEdge edge, String state) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>(edge.metadata());
        putVisualState(metadata, state);
        return new GraphEdge(edge.id(), edge.fromNodeId(), edge.toNodeId(), edge.label(), edge.directed(), metadata);
    }

    private void putVisualState(LinkedHashMap<String, String> metadata, String state) {
        String existing = metadata.getOrDefault("visual-state", "");
        if (existing.isBlank()) {
            metadata.put("visual-state", state);
            return;
        }
        List<String> states = java.util.Arrays.asList(existing.split(","));
        if (!states.contains(state)) {
            metadata.put("visual-state", existing + "," + state);
        }
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

    private record GraphStateResult(List<GraphNode> nodes, List<GraphEdge> edges) {
    }

    private record PointerTarget(String label, TypedMemoryNode node) {
    }

    private record LayoutProjection(
            VisualMemoryMirror mirror,
            Map<String, TypedMemoryNode> typedByAddress,
            Map<String, String> edgeLabels
    ) {
    }

    private record NaturalMirrorBuild(
            LinkedHashMap<String, VisualMemoryNode> nodes,
            ArrayList<VisualMemoryEdge> edges,
            LinkedHashMap<String, TypedMemoryNode> typedByAddress,
            LinkedHashMap<String, String> edgeLabels,
            List<String> rootAddresses
    ) {
    }

    private static final class TypedNodeIndex {
        private final Map<String, TypedMemoryNode> nodesByAddress;

        private TypedNodeIndex(Map<String, TypedMemoryNode> nodesByAddress) {
            this.nodesByAddress = nodesByAddress;
        }

        private static TypedNodeIndex from(TypedMemoryGraph graph) {
            LinkedHashMap<String, TypedMemoryNode> nodesByAddress = new LinkedHashMap<>();
            for (TypedMemoryNode root : graph.roots()) {
                collect(root, nodesByAddress);
            }
            return new TypedNodeIndex(nodesByAddress);
        }

        private static void collect(TypedMemoryNode node, Map<String, TypedMemoryNode> nodesByAddress) {
            if (node.address() != null) {
                nodesByAddress.putIfAbsent(node.address(), node);
            }
            node.fields().forEach(field -> collect(field.value(), nodesByAddress));
            node.elements().forEach(element -> collect(element.value(), nodesByAddress));
        }

        private TypedMemoryNode nodeByAddress(String address) {
            return nodesByAddress.get(address);
        }
    }
}
