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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从虚拟进程空间和 @visual 注释构建可视化投影。
 */
public final class VisualProjectionBuilder {
    private static final int DEFAULT_MAX_DEPTH = 64;

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
        buildTypedSpecs(processSpace, specs, structures, warnings);
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

    private void buildTypedSpecs(
            DebugProcessSpace processSpace,
            List<VisualSpec> specs,
            ArrayList<VisualStructure> structures,
            ArrayList<String> warnings
    ) {
        if (specs.isEmpty()) {
            return;
        }
        TypedMemoryGraph memoryGraph = TypedMemoryGraphBuilder.build(processSpace);
        TypedGraphIndex index = TypedGraphIndex.from(memoryGraph);
        for (VisualSpec spec : specs) {
            TypedMemoryNode root = memoryGraph.findRoot(spec.root()).orElse(null);
            if (root == null) {
                warnings.add("未找到 visual root 变量：" + spec.root());
                continue;
            }
            structures.add(projectTypedSpec(spec, root, index, warnings));
        }
    }

    private VisualStructure projectTypedSpec(
            VisualSpec spec,
            TypedMemoryNode root,
            TypedGraphIndex index,
            ArrayList<String> warnings
    ) {
        VisualKind kind = spec.kind() == VisualKind.AUTO ? inferKind(root) : spec.kind();
        return switch (kind) {
            case MATRIX, STRUCT_MATRIX -> typedArray(spec, root, kind, true, warnings);
            case ARRAY, POINTER_ARRAY, STRUCT_ARRAY, STRING, STACK, QUEUE, DEQUE, CIRCULAR_QUEUE,
                    HEAP, FENWICK_TREE, DSU, RECORD_TABLE -> typedArray(spec, root, kind, false, warnings);
            case STRUCT -> typedComposite(spec, root, kind);
            case POINTER, POINTER_CHAIN, STRUCT_POINTER, STRUCT_POINTER_CHAIN -> typedPointerGraph(spec, root, index, kind, warnings);
            case STRUCT_LIST, SINGLY_LIST, DOUBLY_LIST, LRU_LIST -> typedLinkedList(spec, root, index, kind, warnings);
            case BINARY_TREE -> typedBinaryTree(spec, root, index, warnings);
            case HASH_CHAIN_TABLE -> typedHashChainTable(spec, root, index, warnings);
            case GENERAL_TREE, TRIE, SEGMENT_TREE, ADJACENCY_LIST, GRAPH, PERSISTENT_TREE -> typedComposite(spec, root, kind);
            case SCALAR, AUTO -> typedComposite(spec, root, kind);
        };
    }

    private VisualKind inferKind(TypedMemoryNode root) {
        if (root.shape() == TypeShape.ARRAY) {
            return VisualKind.ARRAY;
        }
        if (root.shape() == TypeShape.STRUCT) {
            return VisualKind.STRUCT;
        }
        if (root.shape() == TypeShape.POINTER) {
            return VisualKind.POINTER;
        }
        return VisualKind.SCALAR;
    }

    private ArrayStructure typedArray(
            VisualSpec spec,
            TypedMemoryNode root,
            VisualKind kind,
            boolean matrix,
            ArrayList<String> warnings
    ) {
        int length = root.elements().size();
        MatrixShape matrixShape = matrix ? matrixShape(spec, length, warnings) : new MatrixShape(1, Math.max(length, 1));
        ArrayShape shape = matrix
                ? new ArrayShape(matrixShape.rows(), matrixShape.columns(), matrixShape.capacity(), length)
                : ArrayShape.oneDimensional(Math.max(length, 1));
        ArrayList<ArrayCell> cells = new ArrayList<>();
        for (int position = 0; position < root.elements().size(); position++) {
            TypedMemoryElement element = root.elements().get(position);
            TypedMemoryNode value = element.value();
            int linearIndex = Math.toIntExact(element.index());
            int row = matrix ? linearIndex / matrixShape.columns() : 0;
            int column = matrix ? linearIndex % matrixShape.columns() : linearIndex;
            String indexPath = matrix ? "[" + row + "][" + column + "]" : "[" + linearIndex + "]";
            LinkedHashMap<String, String> metadata = nodeMetadata(value, spec.root(), value.name());
            metadata.put("indexPath", indexPath);
            metadata.put("linearIndex", Integer.toString(linearIndex));
            metadata.put("row", Integer.toString(row));
            metadata.put("column", Integer.toString(column));
            List<String> fieldNames = fieldNames(spec, value);
            if (!fieldNames.isEmpty()) {
                metadata.put("fieldNames", String.join(",", fieldNames));
            }
            enrichArrayCellMetadata(spec, kind, linearIndex, length, metadata);
            cells.add(new ArrayCell(
                    "array-" + spec.name() + "-cell-" + linearIndex,
                    row,
                    column,
                    linearIndex,
                    arrayCellLabel(value),
                    nullToEmpty(value.address()),
                    metadata
            ));
        }
        return new ArrayStructure(
                "array-" + spec.name(),
                spec.name(),
                kindName(kind),
                spec.attributes().getOrDefault("layout", matrix ? "grid" : "linear"),
                matrix ? 2 : 1,
                shape,
                cells,
                List.of(),
                List.of()
        );
    }

    private CompositeStructure typedComposite(VisualSpec spec, TypedMemoryNode root, VisualKind kind) {
        ArrayList<CompositePart> parts = new ArrayList<>();
        List<TypedMemoryField> fields = selectedFields(spec, root);
        if (fields.isEmpty()) {
            LinkedHashMap<String, String> metadata = nodeMetadata(root, spec.root(), root.name());
            parts.add(new CompositePart(
                    "composite-" + spec.name() + "-part-root",
                    root.id(),
                    kindName(kind),
                    metadata
            ));
        } else {
            for (TypedMemoryField field : fields) {
                TypedMemoryNode value = field.value();
                LinkedHashMap<String, String> metadata = nodeMetadata(value, spec.root(), value.name());
                metadata.put("fieldName", field.name());
                parts.add(new CompositePart(
                        "composite-" + spec.name() + "-part-" + field.name(),
                        value.id(),
                        field.name(),
                        metadata
                ));
            }
        }
        return new CompositeStructure(
                "composite-" + spec.name(),
                spec.name(),
                kindName(kind),
                parts.getFirst().id(),
                parts,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private GraphStructure typedPointerGraph(
            VisualSpec spec,
            TypedMemoryNode root,
            TypedGraphIndex index,
            VisualKind kind,
            ArrayList<String> warnings
    ) {
        GraphBuild graph = new GraphBuild(spec.name(), kindName(kind), "force");
        TypedMemoryNode current = root;
        HashSet<String> visited = new HashSet<>();
        int maxDepth = maxDepth(spec, warnings);
        int depth = 0;
        while (current != null && visited.add(current.id())) {
            if (depth >= maxDepth) {
                warnings.add("visual " + spec.name() + " 达到最大深度 " + maxDepth);
                break;
            }
            GraphNode source = graphNodeFromTyped(spec.name(), current, pointerLabel(current), spec.root(), current.name());
            graph.addNode(source);
            if (current.pointerTarget() == null) {
                break;
            }
            TypedMemoryNode target = index.nodeByAddress(current.pointerTarget());
            GraphNode targetNode = target == null
                    ? unresolvedTargetNode(spec.name(), current.pointerTarget())
                    : graphNodeFromTyped(spec.name(), target, valueLabel(target), spec.root(), target.name());
            graph.addNode(targetNode);
            graph.addEdge(pointerEdge(
                    spec.name(),
                    current,
                    source.id(),
                    targetNode.id(),
                    sanitize(current.id()),
                    target == null ? sanitize(current.pointerTarget()) : sanitize(target.id()),
                    current.pointerTarget(),
                    "points-to"
            ));
            if (kind != VisualKind.POINTER_CHAIN && kind != VisualKind.STRUCT_POINTER_CHAIN) {
                break;
            }
            current = target != null && target.shape() == TypeShape.POINTER ? target : null;
            depth++;
        }
        return graph.build();
    }

    private GraphStructure typedLinkedList(
            VisualSpec spec,
            TypedMemoryNode root,
            TypedGraphIndex index,
            VisualKind kind,
            ArrayList<String> warnings
    ) {
        String nextFieldName = spec.attributes().getOrDefault("next", "next");
        String prevFieldName = spec.attributes().getOrDefault("prev", "prev");
        String labelFieldName = spec.attributes().getOrDefault("label", "");
        GraphBuild graph = new GraphBuild(spec.name(), kindName(kind), "linked");
        TypedMemoryNode current = root.shape() == TypeShape.POINTER && root.pointerTarget() != null
                ? index.nodeByAddress(root.pointerTarget())
                : root;
        HashSet<String> visited = new HashSet<>();
        ArrayList<TypedMemoryNode> orderedNodes = new ArrayList<>();
        int maxDepth = maxDepth(spec, warnings);
        int depth = 0;
        while (current != null && visited.add(current.id())) {
            if (depth >= maxDepth) {
                warnings.add("visual " + spec.name() + " 达到最大深度 " + maxDepth);
                break;
            }
            GraphNode source = graphNodeFromTyped(spec.name(), current, structListLabel(current, labelFieldName), spec.root(), current.name());
            graph.addNode(source);
            orderedNodes.add(current);
            TypedMemoryField nextField = field(current, nextFieldName);
            if (nextField == null || nextField.value().pointerTarget() == null) {
                break;
            }
            TypedMemoryNode target = index.nodeByAddress(nextField.value().pointerTarget());
            if (target == null) {
                break;
            }
            GraphNode targetNode = graphNodeFromTyped(spec.name(), target, structListLabel(target, labelFieldName), spec.root(), target.name());
            graph.addNode(targetNode);
            graph.addEdge(pointerEdge(
                    spec.name(),
                    nextField.value(),
                    source.id(),
                    targetNode.id(),
                    sanitize(current.id()),
                    sanitize(target.id()),
                    nextField.value().pointerTarget(),
                    nextFieldName,
                    Map.of("edge-role", "primary")
            ));
            current = target;
            depth++;
        }
        if (kind == VisualKind.DOUBLY_LIST || kind == VisualKind.LRU_LIST) {
            addAuxiliaryListEdges(spec, graph, index, orderedNodes, visited, prevFieldName);
        }
        if (kind == VisualKind.LRU_LIST && !orderedNodes.isEmpty()) {
            graph.mergeNodeMetadata(
                    spec.name() + "-node-" + sanitize(orderedNodes.getFirst().id()),
                    Map.of("marker", "head")
            );
            graph.mergeNodeMetadata(
                    spec.name() + "-node-" + sanitize(orderedNodes.getLast().id()),
                    Map.of("marker", "tail")
            );
        }
        return graph.build();
    }

    private void addAuxiliaryListEdges(
            VisualSpec spec,
            GraphBuild graph,
            TypedGraphIndex index,
            List<TypedMemoryNode> orderedNodes,
            Set<String> visited,
            String prevFieldName
    ) {
        for (TypedMemoryNode node : orderedNodes) {
            TypedMemoryField prevField = field(node, prevFieldName);
            if (prevField == null || prevField.value().pointerTarget() == null) {
                continue;
            }
            TypedMemoryNode target = index.nodeByAddress(prevField.value().pointerTarget());
            if (target == null || !visited.contains(target.id())) {
                continue;
            }
            graph.addEdge(pointerEdge(
                    spec.name(),
                    prevField.value(),
                    spec.name() + "-node-" + sanitize(node.id()),
                    spec.name() + "-node-" + sanitize(target.id()),
                    sanitize(node.id()),
                    sanitize(target.id()),
                    prevField.value().pointerTarget(),
                    prevFieldName,
                    Map.of("edge-role", "auxiliary")
            ));
        }
    }

    private GraphStructure typedBinaryTree(
            VisualSpec spec,
            TypedMemoryNode root,
            TypedGraphIndex index,
            ArrayList<String> warnings
    ) {
        String leftFieldName = spec.attributes().getOrDefault("left", "left");
        String rightFieldName = spec.attributes().getOrDefault("right", "right");
        String labelFieldName = spec.attributes().getOrDefault("label", "");
        GraphBuild graph = new GraphBuild(spec.name(), kindName(VisualKind.BINARY_TREE), "hierarchical");
        TypedMemoryNode start = root.shape() == TypeShape.POINTER && root.pointerTarget() != null
                ? index.nodeByAddress(root.pointerTarget())
                : root;
        addBinaryTreeNode(
                spec,
                graph,
                index,
                start,
                labelFieldName,
                leftFieldName,
                rightFieldName,
                new HashSet<>(),
                0,
                maxDepth(spec, warnings),
                warnings
        );
        return graph.build();
    }

    private void addBinaryTreeNode(
            VisualSpec spec,
            GraphBuild graph,
            TypedGraphIndex index,
            TypedMemoryNode current,
            String labelFieldName,
            String leftFieldName,
            String rightFieldName,
            Set<String> visited,
            int depth,
            int maxDepth,
            ArrayList<String> warnings
    ) {
        if (current == null) {
            return;
        }
        if (depth >= maxDepth) {
            warnings.add("visual " + spec.name() + " 达到最大深度 " + maxDepth);
            return;
        }
        graph.addNode(graphNodeFromTyped(spec.name(), current, structListLabel(current, labelFieldName), spec.root(), current.name()));
        if (!visited.add(current.id())) {
            return;
        }
        addBinaryTreeEdge(spec, graph, index, current, leftFieldName, labelFieldName, leftFieldName, rightFieldName, visited, depth, maxDepth, warnings);
        addBinaryTreeEdge(spec, graph, index, current, rightFieldName, labelFieldName, leftFieldName, rightFieldName, visited, depth, maxDepth, warnings);
    }

    private void addBinaryTreeEdge(
            VisualSpec spec,
            GraphBuild graph,
            TypedGraphIndex index,
            TypedMemoryNode current,
            String fieldName,
            String labelFieldName,
            String leftFieldName,
            String rightFieldName,
            Set<String> visited,
            int depth,
            int maxDepth,
            ArrayList<String> warnings
    ) {
        TypedMemoryField childField = field(current, fieldName);
        if (childField == null || childField.value().pointerTarget() == null) {
            return;
        }
        TypedMemoryNode target = index.nodeByAddress(childField.value().pointerTarget());
        if (target == null) {
            return;
        }
        graph.addNode(graphNodeFromTyped(spec.name(), target, structListLabel(target, labelFieldName), spec.root(), target.name()));
        graph.addEdge(pointerEdge(
                spec.name(),
                childField.value(),
                spec.name() + "-node-" + sanitize(current.id()),
                spec.name() + "-node-" + sanitize(target.id()),
                sanitize(current.id()),
                sanitize(target.id()),
                childField.value().pointerTarget(),
                fieldName,
                Map.of("edge-role", "primary")
        ));
        addBinaryTreeNode(
                spec,
                graph,
                index,
                target,
                labelFieldName,
                leftFieldName,
                rightFieldName,
                visited,
                depth + 1,
                maxDepth,
                warnings
        );
    }

    private GraphStructure typedHashChainTable(
            VisualSpec spec,
            TypedMemoryNode root,
            TypedGraphIndex index,
            ArrayList<String> warnings
    ) {
        GraphBuild graph = new GraphBuild(spec.name(), kindName(VisualKind.HASH_CHAIN_TABLE), "bucketed");
        if (root.shape() != TypeShape.ARRAY) {
            warnings.add("visual " + spec.name() + " 的 hash-chain-table root 应该是桶数组");
            return graph.build();
        }
        String nextFieldName = spec.attributes().getOrDefault("next", "next");
        String labelFieldName = spec.attributes().getOrDefault("label", "key");
        int maxDepth = maxDepth(spec, warnings);
        for (TypedMemoryElement element : root.elements()) {
            int bucketIndex = Math.toIntExact(element.index());
            String bucketId = "bucket-" + bucketIndex;
            graph.addNode(bucketNode(spec, root, bucketIndex, bucketId));
            TypedMemoryNode pointer = element.value();
            if (pointer.pointerTarget() == null) {
                continue;
            }
            TypedMemoryNode current = index.nodeByAddress(pointer.pointerTarget());
            HashSet<String> visited = new HashSet<>();
            int chainDepth = 0;
            String previousNodeId = spec.name() + "-node-" + bucketId;
            String previousVisualId = bucketId;
            TypedMemoryNode previousPointer = pointer;
            String previousPointerTarget = pointer.pointerTarget();
            while (current != null && visited.add(current.id())) {
                if (chainDepth >= maxDepth) {
                    warnings.add("visual " + spec.name() + " 达到最大深度 " + maxDepth);
                    break;
                }
                String currentVisualId = sanitize(current.id());
                graph.addNode(graphNodeFromTyped(
                        spec.name(),
                        current,
                        structListLabel(current, labelFieldName),
                        spec.root(),
                        current.name(),
                        Map.of(
                                "visual-role", "chain-node",
                                "bucketIndex", Integer.toString(bucketIndex),
                                "chainDepth", Integer.toString(chainDepth)
                        )
                ));
                graph.addEdge(pointerEdge(
                        spec.name(),
                        previousPointer,
                        previousNodeId,
                        spec.name() + "-node-" + currentVisualId,
                        previousVisualId,
                        currentVisualId,
                        previousPointerTarget,
                        chainDepth == 0 ? "bucket" : nextFieldName,
                        Map.of(
                                "edge-role", "primary",
                                "bucketIndex", Integer.toString(bucketIndex),
                                "chainDepth", Integer.toString(chainDepth)
                        )
                ));
                TypedMemoryField nextField = field(current, nextFieldName);
                if (nextField == null || nextField.value().pointerTarget() == null) {
                    break;
                }
                TypedMemoryNode target = index.nodeByAddress(nextField.value().pointerTarget());
                previousNodeId = spec.name() + "-node-" + currentVisualId;
                previousVisualId = currentVisualId;
                previousPointer = nextField.value();
                previousPointerTarget = nextField.value().pointerTarget();
                current = target;
                chainDepth++;
            }
        }
        return graph.build();
    }

    private GraphNode bucketNode(VisualSpec spec, TypedMemoryNode root, int bucketIndex, String bucketId) {
        return new GraphNode(
                spec.name() + "-node-" + bucketId,
                "[" + bucketIndex + "]",
                "",
                componentId(spec.name()),
                Map.of(
                        "id", bucketId,
                        "root", spec.root(),
                        "path", root.name() + "[" + bucketIndex + "]",
                        "indexPath", "[" + bucketIndex + "]",
                        "visual-role", "bucket",
                        "bucketIndex", Integer.toString(bucketIndex)
                )
        );
    }

    private GraphNode graphNodeFromTyped(String graphName, TypedMemoryNode node, String label, String root, String path) {
        return graphNodeFromTyped(graphName, node, label, root, path, Map.of());
    }

    private GraphNode graphNodeFromTyped(
            String graphName,
            TypedMemoryNode node,
            String label,
            String root,
            String path,
            Map<String, String> extraMetadata
    ) {
        LinkedHashMap<String, String> metadata = nodeMetadata(node, root, path);
        metadata.put("id", sanitize(node.id()));
        metadata.putAll(extraMetadata);
        return new GraphNode(
                graphName + "-node-" + sanitize(node.id()),
                label.isBlank() ? node.name() : label,
                nullToEmpty(node.address()),
                componentId(graphName),
                metadata
        );
    }

    private GraphNode unresolvedTargetNode(String graphName, String pointerTarget) {
        return new GraphNode(
                graphName + "-node-unresolved-" + sanitize(pointerTarget),
                pointerTarget,
                pointerTarget,
                componentId(graphName),
                Map.of(
                        "id", sanitize(pointerTarget),
                        "address", pointerTarget,
                        "unresolved", "true"
                )
        );
    }

    private GraphEdge pointerEdge(
            String graphName,
            TypedMemoryNode pointerNode,
            String sourceId,
            String targetId,
            String fromVisualId,
            String toVisualId,
            String pointerTarget,
            String label
    ) {
        return pointerEdge(graphName, pointerNode, sourceId, targetId, fromVisualId, toVisualId, pointerTarget, label, Map.of());
    }

    private GraphEdge pointerEdge(
            String graphName,
            TypedMemoryNode pointerNode,
            String sourceId,
            String targetId,
            String fromVisualId,
            String toVisualId,
            String pointerTarget,
            String label,
            Map<String, String> extraMetadata
    ) {
        LinkedHashMap<String, String> metadata = nodeMetadata(pointerNode, pointerNode.name(), pointerNode.name());
        metadata.put("from", fromVisualId);
        metadata.put("to", toVisualId);
        metadata.put("pointerTarget", pointerTarget);
        metadata.putAll(extraMetadata);
        return new GraphEdge(
                graphName + "-edge-" + sanitize(sourceId) + "-" + sanitize(targetId),
                sourceId,
                targetId,
                label,
                true,
                metadata
        );
    }

    private LinkedHashMap<String, String> nodeMetadata(TypedMemoryNode node, String root, String path) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("root", root);
        metadata.put("path", path);
        metadata.put("type", node.typeName());
        metadata.put("address", nullToEmpty(node.address()));
        metadata.put("valueSummary", node.valueSummary());
        if (node.pointerTarget() != null) {
            metadata.put("pointerTarget", node.pointerTarget());
        }
        if (!node.fields().isEmpty()) {
            metadata.put("fieldNames", node.fields().stream().map(TypedMemoryField::name).collect(Collectors.joining(",")));
        }
        return metadata;
    }

    private List<TypedMemoryField> selectedFields(VisualSpec spec, TypedMemoryNode root) {
        if (spec.fields().isEmpty()) {
            return root.fields();
        }
        Set<String> selected = new HashSet<>(spec.fields());
        return root.fields().stream()
                .filter(field -> selected.contains(field.name()))
                .toList();
    }

    private List<String> fieldNames(VisualSpec spec, TypedMemoryNode value) {
        if (!spec.fields().isEmpty()) {
            return spec.fields();
        }
        return value.fields().stream().map(TypedMemoryField::name).toList();
    }

    private void enrichArrayCellMetadata(
            VisualSpec spec,
            VisualKind kind,
            int linearIndex,
            int length,
            LinkedHashMap<String, String> metadata
    ) {
        if (kind == VisualKind.STACK) {
            metadata.put("template", "stack");
            metadata.put("topVariable", spec.attributes().getOrDefault("top", "top"));
        } else if (kind == VisualKind.QUEUE || kind == VisualKind.DEQUE || kind == VisualKind.CIRCULAR_QUEUE) {
            metadata.put("template", kindName(kind));
            metadata.put("frontVariable", spec.attributes().getOrDefault("front", "front"));
            metadata.put("rearVariable", spec.attributes().getOrDefault("rear", "rear"));
        } else if (kind == VisualKind.HEAP) {
            metadata.put("template", "heap");
            if (linearIndex > 0) {
                metadata.put("parentIndex", Integer.toString((linearIndex - 1) / 2));
            }
            int left = linearIndex * 2 + 1;
            int right = linearIndex * 2 + 2;
            if (left < length) {
                metadata.put("leftIndex", Integer.toString(left));
            }
            if (right < length) {
                metadata.put("rightIndex", Integer.toString(right));
            }
        } else if (kind == VisualKind.FENWICK_TREE) {
            int fenwickIndex = linearIndex + 1;
            int lowbit = fenwickIndex & -fenwickIndex;
            metadata.put("template", "fenwick-tree");
            metadata.put("fenwickIndex", Integer.toString(fenwickIndex));
            metadata.put("rangeStart", Integer.toString(fenwickIndex - lowbit + 1));
            metadata.put("rangeEnd", Integer.toString(fenwickIndex));
        } else if (kind == VisualKind.DSU) {
            metadata.put("template", "dsu");
            metadata.put("parentIndex", metadata.getOrDefault("valueSummary", ""));
        }
    }

    private TypedMemoryField field(TypedMemoryNode node, String fieldName) {
        return node.fields().stream()
                .filter(field -> field.name().equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    private String arrayCellLabel(TypedMemoryNode value) {
        if (value.shape() == TypeShape.STRUCT) {
            return value.name() + " " + value.typeName() + "{" + value.fields().stream()
                    .map(field -> field.name() + "=" + field.value().valueSummary())
                    .collect(Collectors.joining(", ")) + "}";
        }
        return value.valueSummary();
    }

    private String pointerLabel(TypedMemoryNode node) {
        return node.pointerTarget() == null ? valueLabel(node) : node.name() + " -> " + node.pointerTarget();
    }

    private String valueLabel(TypedMemoryNode node) {
        return node.name() + "=" + node.valueSummary();
    }

    private String structListLabel(TypedMemoryNode node, String labelFieldName) {
        if (!labelFieldName.isBlank()) {
            TypedMemoryField labelField = field(node, labelFieldName);
            if (labelField != null) {
                return labelField.value().valueSummary();
            }
        }
        return node.name();
    }

    private MatrixShape matrixShape(VisualSpec spec, int length, ArrayList<String> warnings) {
        int fallbackColumns = Math.max(length, 1);
        int rows = positiveInt(spec.attributes().get("rows"), 1);
        int columns = positiveInt(spec.attributes().get("columns"), fallbackColumns);
        long capacity = (long) rows * columns;
        if (capacity < length || capacity > Integer.MAX_VALUE) {
            warnings.add("visual " + spec.name() + " 的矩阵维度不足或过大，已回退为 1x" + fallbackColumns);
            return new MatrixShape(1, fallbackColumns);
        }
        return new MatrixShape(rows, columns);
    }

    private int maxDepth(VisualSpec spec, ArrayList<String> warnings) {
        int maxDepth = positiveInt(spec.attributes().get("max-depth"), DEFAULT_MAX_DEPTH);
        if (maxDepth <= 0) {
            warnings.add("visual " + spec.name() + " 的 max-depth 非法，已使用 " + DEFAULT_MAX_DEPTH);
            return DEFAULT_MAX_DEPTH;
        }
        return maxDepth;
    }

    private int positiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String kindName(VisualKind kind) {
        return kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private record MatrixShape(int rows, int columns) {
        private int capacity() {
            return rows * columns;
        }
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

    private static final class TypedGraphIndex {
        private final Map<String, TypedMemoryNode> nodesByAddress;

        private TypedGraphIndex(Map<String, TypedMemoryNode> nodesByAddress) {
            this.nodesByAddress = nodesByAddress;
        }

        private static TypedGraphIndex from(TypedMemoryGraph graph) {
            LinkedHashMap<String, TypedMemoryNode> nodesByAddress = new LinkedHashMap<>();
            graph.roots().forEach(root -> collect(root, nodesByAddress));
            return new TypedGraphIndex(nodesByAddress);
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

    private final class GraphBuild {
        private final String name;
        private final String kind;
        private final String layoutHint;
        private final LinkedHashMap<String, GraphNode> nodes = new LinkedHashMap<>();
        private final ArrayList<GraphEdge> edges = new ArrayList<>();

        private GraphBuild(String name, String kind, String layoutHint) {
            this.name = name;
            this.kind = kind;
            this.layoutHint = layoutHint;
        }

        private void addNode(GraphNode node) {
            nodes.putIfAbsent(node.id(), node);
        }

        private void addEdge(GraphEdge edge) {
            edges.add(edge);
        }

        private void mergeNodeMetadata(String nodeId, Map<String, String> metadata) {
            GraphNode existing = nodes.get(nodeId);
            if (existing == null) {
                return;
            }
            LinkedHashMap<String, String> merged = new LinkedHashMap<>(existing.metadata());
            String existingMarker = merged.get("marker");
            String newMarker = metadata.get("marker");
            merged.putAll(metadata);
            if (existingMarker != null && newMarker != null && !existingMarker.equals(newMarker)) {
                merged.put("marker", existingMarker + "," + newMarker);
            }
            nodes.put(nodeId, new GraphNode(
                    existing.id(),
                    existing.label(),
                    existing.valueRef(),
                    existing.componentId(),
                    merged
            ));
        }

        private GraphStructure build() {
            GraphStateResult state = applyGraphStates(nodes.values().stream().toList(), edges);
            GraphComponent component = new GraphComponent(
                    componentId(name),
                    name,
                    state.nodes().stream().map(GraphNode::id).toList()
            );
            return new GraphStructure(
                    "graph-" + name,
                    name,
                    kind,
                    layoutHint,
                    state.nodes(),
                    state.edges(),
                    List.of(component),
                    List.of(),
                    List.of()
            );
        }
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
                    kind.equals("tree") || kind.equals("binary_tree") ? "hierarchical" : "force",
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
}
