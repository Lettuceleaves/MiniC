package minic.uiapi;

import minic.runtime.debug.DebugProcessSpace;
import minic.runtime.debug.visual.ArrayStructure;
import minic.runtime.debug.visual.CompositeStructure;
import minic.runtime.debug.visual.GraphStructure;
import minic.runtime.debug.visual.VisualAnnotationParser;
import minic.runtime.debug.visual.VisualProjection;
import minic.runtime.debug.visual.VisualProjectionBuilder;
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
        List<minic.runtime.debug.visual.VisualAnnotation> annotations =
                new VisualAnnotationParser().parse(sourceFile).annotations();
        VisualProjection projection = new VisualProjectionBuilder().build(
                processSpace,
                visibleAnnotations(annotations, state)
        );
        return new UiDebugDataStructureViewDto(
                state.currentSnapshot().processSpace(),
                projection.structures().stream().map(this::visual).toList(),
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

    private UiDebugVisualStructureDto visual(VisualStructure structure) {
        return new UiDebugVisualStructureDto(
                structure.id(),
                structure.name(),
                structure.type().name(),
                structure.kind(),
                structure.summary(),
                elements(structure)
        );
    }

    private java.util.List<UiDebugVisualElementDto> elements(VisualStructure structure) {
        ArrayList<UiDebugVisualElementDto> elements = new ArrayList<>();
        if (structure instanceof GraphStructure graph) {
            graph.nodes().forEach(node -> elements.add(new UiDebugVisualElementDto(
                    node.id(),
                    "GRAPH_NODE",
                    node.label(),
                    node.metadata()
            )));
            graph.edges().forEach(edge -> elements.add(new UiDebugVisualElementDto(
                    edge.id(),
                    "GRAPH_EDGE",
                    edge.label(),
                    edge.metadata()
            )));
        } else if (structure instanceof ArrayStructure array) {
            array.cells().forEach(cell -> elements.add(new UiDebugVisualElementDto(
                    cell.id(),
                    "ARRAY_CELL",
                    cell.label(),
                    cell.metadata()
            )));
        } else if (structure instanceof CompositeStructure composite) {
            composite.parts().forEach(part -> elements.add(new UiDebugVisualElementDto(
                    part.id(),
                    "COMPOSITE_PART",
                    part.role(),
                    part.metadata()
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
}
