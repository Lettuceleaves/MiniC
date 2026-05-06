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
import java.util.Map;

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
        VisualProjection projection = new VisualProjectionBuilder().build(
                processSpace,
                new VisualAnnotationParser().parse(sourceFile).annotations()
        );
        return new UiDebugDataStructureViewDto(
                state.currentSnapshot().processSpace(),
                projection.structures().stream().map(this::visual).toList(),
                projection.warnings()
        );
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
