package minic.uiapi;

import minic.runtime.debug.DebugProcessSpace;
import minic.runtime.debug.DebugHeapBlock;
import minic.runtime.debug.DebugMemoryEntry;
import minic.runtime.debug.dataflow.DataFlowEvent;
import minic.runtime.debug.visual.VisualEvent;
import minic.runtime.debug.visual.ArrayStructure;
import minic.runtime.debug.visual.CompositeStructure;
import minic.runtime.debug.visual.GraphStructure;
import minic.runtime.debug.visual.VisualAnnotationParseResult;
import minic.runtime.debug.visual.VisualAnnotationParser;
import minic.runtime.debug.visual.VisualProjection;
import minic.runtime.debug.visual.VisualProjectionBuilder;
import minic.runtime.debug.visual.VisualKind;
import minic.runtime.debug.visual.VisualStructure;
import minic.source.SourceFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Debug 数据结构视图模型构建器。
 */
public final class UiDebugDataStructureViewBuilder {
    /**
     * 构建数据结构视图。
     *
     * @param sourceFile 源码文件
     * @param state Debug 状态
     * @param processSpace runtime 虚拟进程空间
     * @return 数据结构视图
     */
    public UiDebugDataStructureViewDto build(SourceFile sourceFile, UiDebugStateDto state, DebugProcessSpace processSpace) {
        return build(sourceFile, state, processSpace, List.of(), List.of());
    }

    /**
     * 构建数据结构视图。
     *
     * @param sourceFile 源码文件
     * @param state Debug 状态
     * @param processSpace runtime 虚拟进程空间
     * @param visualEvents 可视化事件日志
     * @return 数据结构视图
     */
    public UiDebugDataStructureViewDto build(
            SourceFile sourceFile,
            UiDebugStateDto state,
            DebugProcessSpace processSpace,
            List<VisualEvent> visualEvents
    ) {
        return build(sourceFile, state, processSpace, visualEvents, List.of());
    }

    /**
     * 构建数据结构视图。
     *
     * @param sourceFile 源码文件
     * @param state Debug 状态
     * @param processSpace runtime 虚拟进程空间
     * @param visualEvents 可视化事件日志
     * @param dataFlowEvents 数据流事件日志
     * @return 数据结构视图
     */
    public UiDebugDataStructureViewDto build(
            SourceFile sourceFile,
            UiDebugStateDto state,
            DebugProcessSpace processSpace,
            List<VisualEvent> visualEvents,
            List<DataFlowEvent> dataFlowEvents
    ) {
        VisualAnnotationParseResult parseResult = new VisualAnnotationParser().parse(sourceFile);
        List<minic.runtime.debug.visual.VisualAnnotation> annotations = parseResult.annotations();
        VisualProjection projection = new VisualProjectionBuilder().build(
                processSpace,
                visibleAnnotations(annotations, state),
                parseResult.specs(),
                visualEvents,
                state.currentSnapshot().snapshotId()
        );
        Map<String, String> rootTypeNames = rootTypeNames(processSpace);
        List<DataFlowEvent> visibleDataFlowEvents = visibleDataFlowEvents(dataFlowEvents, state.currentSnapshot().snapshotId());
        return new UiDebugDataStructureViewDto(
                UiDebugDtoMapper.processSpace(processSpace),
                projection.structures().stream()
                        .map(structure -> visual(structure, rootTypeNames, visibleDataFlowEvents))
                        .toList(),
                projection.warnings()
        );
    }

    private List<minic.runtime.debug.visual.VisualAnnotation> visibleAnnotations(
            List<minic.runtime.debug.visual.VisualAnnotation> annotations,
            UiDebugStateDto state
    ) {
        Set<String> recursiveGraphs = annotations.stream()
                .filter(annotation -> annotation.directive().equals("@visual"))
                .filter(annotation -> annotation.attributes().getOrDefault("reveal", "").equals("recursive"))
                .map(minic.runtime.debug.visual.VisualAnnotation::name)
                .collect(Collectors.toSet());
        if (recursiveGraphs.isEmpty()) {
            return annotations;
        }
        Map<String, Set<String>> revealedRecursiveValues = revealedRecursiveValues(annotations, state);
        return annotations.stream()
                .filter(annotation -> {
                    if (!recursiveGraphs.contains(annotation.name())) {
                        return true;
                    }
                    if (annotation.directive().equals("@visual")) {
                        return true;
                    }
                    return isRevealedRecursiveElement(annotation, revealedRecursiveValues.get(annotation.name()));
                })
                .toList();
    }

    private Map<String, Set<String>> revealedRecursiveValues(
            List<minic.runtime.debug.visual.VisualAnnotation> annotations,
            UiDebugStateDto state
    ) {
        LinkedHashMap<String, Set<String>> values = new LinkedHashMap<>();
        for (minic.runtime.debug.visual.VisualAnnotation annotation : annotations) {
            if (!annotation.directive().equals("@visual")
                    || !annotation.attributes().getOrDefault("reveal", "").equals("recursive")) {
                continue;
            }
            String function = annotation.attributes().get("function");
            String visit = annotation.attributes().get("visit");
            if (function == null || visit == null) {
                continue;
            }
            values.put(annotation.name(), state.snapshots().stream()
                    .filter(snapshot -> snapshot.snapshotId() <= state.currentSnapshot().snapshotId())
                    .flatMap(snapshot -> snapshot.processSpace().stackFrames().stream())
                    .filter(frame -> frame.functionName().equals(function))
                    .flatMap(frame -> frame.parameters().stream())
                    .filter(variable -> variable.name().equals(visit))
                    .map(UiDebugVariableDto::valueSummary)
                    .filter(value -> !value.equals("0"))
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new)));
        }
        return values;
    }

    private boolean isRevealedRecursiveElement(
            minic.runtime.debug.visual.VisualAnnotation annotation,
            Set<String> revealedValues
    ) {
        if (revealedValues == null || revealedValues.isEmpty()) {
            return false;
        }
        if (annotation.directive().equals("@visual-node")) {
            return revealedValues.contains(annotation.attributes().get("id"));
        }
        if (annotation.directive().equals("@visual-edge")) {
            return revealedValues.contains(annotation.attributes().get("from"))
                    && revealedValues.contains(annotation.attributes().get("to"));
        }
        return true;
    }

    private UiDebugVisualStructureDto visual(
            VisualStructure structure,
            Map<String, String> rootTypeNames,
            List<DataFlowEvent> dataFlowEvents
    ) {
        return new UiDebugVisualStructureDto(
                structure.id(),
                structure.name(),
                structure.type().name(),
                structure.kind(),
                layoutHint(structure),
                structure.summary(),
                structureExplanation(structure, rootTypeNames),
                elements(structure, dataFlowEvents)
        );
    }

    private String layoutHint(VisualStructure structure) {
        if (structure instanceof GraphStructure graph) {
            return graph.layoutHint();
        }
        if (structure instanceof ArrayStructure array) {
            return array.layoutHint();
        }
        return "";
    }

    private java.util.List<UiDebugVisualElementDto> elements(VisualStructure structure, List<DataFlowEvent> dataFlowEvents) {
        ArrayList<UiDebugVisualElementDto> elements = new ArrayList<>();
        if (structure instanceof GraphStructure graph) {
            graph.nodes().forEach(node -> elements.add(new UiDebugVisualElementDto(
                    node.id(),
                    "GRAPH_NODE",
                    node.label(),
                    explainedMetadata(structure, "GRAPH_NODE", node.label(), node.metadata(), dataFlowEvents)
            )));
            graph.edges().forEach(edge -> elements.add(new UiDebugVisualElementDto(
                    edge.id(),
                    "GRAPH_EDGE",
                    edge.label(),
                    explainedMetadata(structure, "GRAPH_EDGE", edge.label(), edge.metadata(), dataFlowEvents)
            )));
        } else if (structure instanceof ArrayStructure array) {
            array.cells().forEach(cell -> elements.add(new UiDebugVisualElementDto(
                    cell.id(),
                    "ARRAY_CELL",
                    cell.label(),
                    explainedMetadata(structure, "ARRAY_CELL", cell.label(), cell.metadata(), dataFlowEvents)
            )));
        } else if (structure instanceof CompositeStructure composite) {
            composite.parts().forEach(part -> elements.add(new UiDebugVisualElementDto(
                    part.id(),
                    "COMPOSITE_PART",
                    part.role(),
                    explainedMetadata(structure, "COMPOSITE_PART", part.role(), part.metadata(), dataFlowEvents)
            )));
            composite.links().forEach(link -> elements.add(new UiDebugVisualElementDto(
                    link.id(),
                    "COMPOSITE_LINK",
                    link.relation(),
                    Map.of("explanation", link.explanation())
            )));
        }
        return elements;
    }

    private Map<String, String> rootTypeNames(DebugProcessSpace processSpace) {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        processSpace.staticData().globals().forEach(entry -> addRootTypeName(names, entry));
        processSpace.stack().frames().forEach(frame -> {
            frame.parameters().forEach(entry -> addRootTypeName(names, entry));
            frame.locals().forEach(entry -> addRootTypeName(names, entry));
        });
        processSpace.heap().blocks().forEach(block -> {
            addRootTypeName(names, block);
            block.entries().forEach(entry -> addRootTypeName(names, entry));
        });
        return names;
    }

    private void addRootTypeName(Map<String, String> names, DebugMemoryEntry entry) {
        names.putIfAbsent(entry.name(), entry.typeName());
    }

    private void addRootTypeName(Map<String, String> names, DebugHeapBlock block) {
        names.putIfAbsent(block.address().display(), block.typeName());
    }

    private String structureExplanation(VisualStructure structure, Map<String, String> rootTypeNames) {
        Map<String, String> variables = variablesForStructure(structure, rootTypeNames);
        return ExplanationTemplates.render("debug-visual", visualKindKey(structure.kind()), variables);
    }

    private Map<String, String> explainedMetadata(
            VisualStructure structure,
            String elementKind,
            String label,
            Map<String, String> metadata,
            List<DataFlowEvent> dataFlowEvents
    ) {
        LinkedHashMap<String, String> explained = new LinkedHashMap<>(metadata);
        explained.putIfAbsent("explanation", elementExplanation(structure, elementKind, label, metadata, dataFlowEvents));
        return explained;
    }

    private String elementExplanation(
            VisualStructure structure,
            String elementKind,
            String label,
            Map<String, String> metadata,
            List<DataFlowEvent> dataFlowEvents
    ) {
        Map<String, String> variables = variablesForElement(structure, elementKind, label, metadata, dataFlowEvents);
        return ExplanationTemplates.render("debug-visual", elementTemplateKey(structure, elementKind, metadata), variables);
    }

    private Map<String, String> variablesForStructure(VisualStructure structure, Map<String, String> rootTypeNames) {
        LinkedHashMap<String, String> variables = baseVariables();
        variables.put("root", structure.name());
        variables.put("typeName", rootTypeNames.getOrDefault(structure.name(), structure.kind()));
        variables.put("cExpression", structure.name());
        variables.put("lvaluePath", structure.name());
        variables.put("indexPath", "");
        return variables;
    }

    private Map<String, String> variablesForElement(
            VisualStructure structure,
            String elementKind,
            String label,
            Map<String, String> metadata,
            List<DataFlowEvent> dataFlowEvents
    ) {
        LinkedHashMap<String, String> variables = baseVariables();
        String root = metadata.getOrDefault("root", structure.name());
        String typeName = metadata.getOrDefault("typeName", metadata.getOrDefault("type", structure.kind()));
        String indexPath = metadata.getOrDefault("indexPath", "");
        String fieldName = metadata.getOrDefault("fieldName", "");
        String path = metadata.getOrDefault("path", "");
        String expression = cExpression(root, path, indexPath, fieldName, elementKind);
        String value = metadata.getOrDefault("valueSummary", label);
        DataFlowEvent dataFlowEvent = matchingDataFlowEvent(dataFlowEvents, root, expression, indexPath, fieldName, path, metadata);
        variables.put("root", root);
        variables.put("typeName", typeName);
        variables.put("cExpression", dataFlowEvent == null ? expression : dataFlowEvent.cExpression());
        variables.put("lvaluePath", dataFlowEvent == null ? expression : dataFlowEvent.lvaluePath());
        variables.put("oldValue", dataFlowEvent == null ? metadata.getOrDefault("oldValue", value) : dataFlowEvent.oldValue());
        variables.put("newValue", dataFlowEvent == null ? metadata.getOrDefault("newValue", value) : dataFlowEvent.newValue());
        variables.put("address", eventValueOrMetadata(dataFlowEvent == null ? "" : dataFlowEvent.address(), metadata, "address"));
        variables.put("pointerTarget", eventValueOrMetadata(dataFlowEvent == null ? "" : dataFlowEvent.pointerTarget(), metadata, "pointerTarget"));
        variables.put("fieldName", fieldName);
        variables.put("fieldNames", metadata.getOrDefault("fieldNames", ""));
        variables.put("indexPath", indexPath);
        variables.put("row", metadata.getOrDefault("row", ""));
        variables.put("column", metadata.getOrDefault("column", ""));
        variables.put("elementLabel", label);
        return variables;
    }

    private LinkedHashMap<String, String> baseVariables() {
        LinkedHashMap<String, String> variables = new LinkedHashMap<>();
        variables.put("root", "");
        variables.put("typeName", "");
        variables.put("cExpression", "");
        variables.put("lvaluePath", "");
        variables.put("oldValue", "");
        variables.put("newValue", "");
        variables.put("address", "");
        variables.put("pointerTarget", "");
        variables.put("fieldName", "");
        variables.put("fieldNames", "");
        variables.put("indexPath", "");
        variables.put("row", "");
        variables.put("column", "");
        variables.put("elementLabel", "");
        return variables;
    }

    private String cExpression(String root, String path, String indexPath, String fieldName, String elementKind) {
        if (!indexPath.isBlank()) {
            return root + indexPath;
        }
        if (!fieldName.isBlank()) {
            return root + "." + fieldName;
        }
        if (!path.isBlank() && !path.equals(root)) {
            return path;
        }
        if (elementKind.equals("GRAPH_EDGE")) {
            return "*" + root;
        }
        return root;
    }

    private List<DataFlowEvent> visibleDataFlowEvents(List<DataFlowEvent> dataFlowEvents, long currentSnapshotId) {
        return dataFlowEvents.stream()
                .filter(event -> event.snapshotId() <= currentSnapshotId)
                .toList();
    }

    private DataFlowEvent matchingDataFlowEvent(
            List<DataFlowEvent> dataFlowEvents,
            String root,
            String expression,
            String indexPath,
            String fieldName,
            String path,
            Map<String, String> metadata
    ) {
        DataFlowEvent bestEvent = null;
        int bestScore = 0;
        for (DataFlowEvent event : dataFlowEvents) {
            int score = dataFlowScore(event, root, expression, indexPath, fieldName, path, metadata);
            if (score > bestScore || (score == bestScore && bestEvent != null && event.snapshotId() >= bestEvent.snapshotId())) {
                bestEvent = event;
                bestScore = score;
            }
        }
        return bestScore <= 0 ? null : bestEvent;
    }

    private int dataFlowScore(
            DataFlowEvent event,
            String root,
            String expression,
            String indexPath,
            String fieldName,
            String path,
            Map<String, String> metadata
    ) {
        int semanticScore = 0;
        String lvaluePath = event.lvaluePath();
        if (lvaluePath.equals(expression)) {
            semanticScore = Math.max(semanticScore, 100);
        }
        if (event.cExpression().equals(expression)) {
            semanticScore = Math.max(semanticScore, 95);
        }
        if (!path.isBlank() && lvaluePath.equals(path)) {
            semanticScore = Math.max(semanticScore, 95);
        }
        if (!indexPath.isBlank()) {
            String indexedPath = root + indexPath;
            if (lvaluePath.equals(indexedPath)) {
                semanticScore = Math.max(semanticScore, 100);
            }
            if (lvaluePath.startsWith(indexedPath + ".")) {
                semanticScore = Math.max(semanticScore, 90);
            }
        }
        if (!fieldName.isBlank() && lvaluePath.equals(root + "." + fieldName)) {
            semanticScore = Math.max(semanticScore, 100);
        }
        if (semanticScore <= 0) {
            return 0;
        }
        int score = semanticScore;
        String address = metadata.getOrDefault("address", "");
        if (!address.isBlank() && address.equals(event.address())) {
            score += 5;
        }
        String pointerTarget = metadata.getOrDefault("pointerTarget", "");
        if (!pointerTarget.isBlank() && pointerTarget.equals(event.pointerTarget())) {
            score += 5;
        }
        return score;
    }

    private String eventValueOrMetadata(String eventValue, Map<String, String> metadata, String key) {
        return eventValue == null || eventValue.isBlank() ? metadata.getOrDefault(key, "") : eventValue;
    }

    private String elementTemplateKey(VisualStructure structure, String elementKind, Map<String, String> metadata) {
        if (elementKind.equals("ARRAY_CELL")) {
            if (isKind(structure.kind(), VisualKind.POINTER_ARRAY)) {
                return "POINTER_ARRAY_ELEMENT";
            }
            if (isKind(structure.kind(), VisualKind.STRUCT_ARRAY)) {
                return "STRUCT_ARRAY_ELEMENT";
            }
            if (isKind(structure.kind(), VisualKind.STRUCT_MATRIX)) {
                return "STRUCT_MATRIX_ELEMENT";
            }
            return isMatrixKind(structure.kind()) ? "MATRIX_ELEMENT" : "ARRAY_ELEMENT";
        }
        if (elementKind.equals("COMPOSITE_PART")) {
            return "STRUCT_FIELD";
        }
        if (elementKind.equals("GRAPH_NODE") && isKind(structure.kind(), VisualKind.STRUCT_LIST)) {
            return "STRUCT_LIST_NODE";
        }
        if (elementKind.equals("GRAPH_EDGE") && isKind(structure.kind(), VisualKind.STRUCT_LIST)) {
            return "STRUCT_LIST_EDGE";
        }
        if (elementKind.equals("GRAPH_EDGE")
                && (isKind(structure.kind(), VisualKind.STRUCT_POINTER)
                || isKind(structure.kind(), VisualKind.STRUCT_POINTER_CHAIN))) {
            return "STRUCT_POINTER_EDGE";
        }
        if (elementKind.equals("GRAPH_EDGE") && metadata.containsKey("pointerTarget")) {
            return "POINTER_DEREFERENCE";
        }
        if (metadata.containsKey("pointerTarget")) {
            return "POINTER_ELEMENT";
        }
        return "ELEMENT";
    }

    private boolean isMatrixKind(String kind) {
        return kind.contains("matrix");
    }

    private boolean isKind(String kind, VisualKind expected) {
        return VisualKind.parse(kind)
                .filter(parsed -> parsed == expected)
                .isPresent();
    }

    private String visualKindKey(String kind) {
        return VisualKind.parse(kind)
                .map(VisualKind::name)
                .orElse("default");
    }
}
