import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiDebugDataStructureViewDto, UiDebugVisualElementDto, UiDebugVisualStructureDto } from "../translation/uiapi";

export const miniCDebugVisualDiagramRendererMirror = {
  "javaPath": "src/main/java/minic/uilocal/debug/MiniCDebugVisualDiagramRenderer.java",
  "webPath": "uiweb/src/debug/MiniCDebugVisualDiagramRenderer.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCDebugVisualDiagramRenderer",
  "kind": "component",
  "imports": [
    "java.util.ArrayList",
    "java.util.Comparator",
    "java.util.HashSet",
    "java.util.LinkedHashMap",
    "java.util.List",
    "java.util.Map",
    "java.util.function.BiConsumer",
    "java.util.stream.Collectors",
    "javafx.scene.Node",
    "javafx.scene.layout.Pane",
    "javafx.scene.shape.Circle",
    "javafx.scene.shape.Line",
    "javafx.scene.shape.Polygon",
    "javafx.scene.shape.Rectangle",
    "javafx.scene.text.Text",
    "minic.color.ThemeRegistry",
    "minic.uiapi.UiDebugVisualElementDto",
    "minic.uiapi.UiDebugVisualStructureDto"
  ],
  "fields": [
    {
      "name": "nextLeafX",
      "signature": "private double nextLeafX="
    },
    {
      "name": "tooltipInstaller",
      "signature": "private final BiConsumer<Node,UiDebugVisualElementDto>tooltipInstaller"
    },
    {
      "name": "VISUAL_CELL_SIZE",
      "signature": "private static final double VISUAL_CELL_SIZE="
    },
    {
      "name": "VISUAL_GRID_GAP",
      "signature": "private static final double VISUAL_GRID_GAP="
    },
    {
      "name": "VISUAL_MARGIN",
      "signature": "private static final double VISUAL_MARGIN="
    },
    {
      "name": "VISUAL_NODE_RADIUS",
      "signature": "private static final double VISUAL_NODE_RADIUS="
    },
    {
      "name": "VISUAL_NULL_SIZE",
      "signature": "private static final double VISUAL_NULL_SIZE="
    }
  ],
  "methods": [
    {
      "name": "arrayDiagram",
      "signature": "arrayDiagram(List<UiDebugVisualElementDto>cells)"
    },
    {
      "name": "arrow",
      "signature": "arrow(VisualPoint from,VisualPoint to,boolean nullTarget)"
    },
    {
      "name": "bucketedPositions",
      "signature": "bucketedPositions(List<UiDebugVisualElementDto>nodes)"
    },
    {
      "name": "graphDiagram",
      "signature": "graphDiagram(String kind,String layoutHint,List<UiDebugVisualElementDto>nodes,List<UiDebugVisualElementDto>edges)"
    },
    {
      "name": "graphPositions",
      "signature": "graphPositions(String kind,String layoutHint,List<UiDebugVisualElementDto>nodes,List<UiDebugVisualElementDto>edges)"
    },
    {
      "name": "isBucketedLayout",
      "signature": "isBucketedLayout(String kind,String layoutHint)"
    },
    {
      "name": "isNullNode",
      "signature": "isNullNode(Map<String,UiDebugVisualElementDto>nodesById,String id)"
    },
    {
      "name": "isNullNode",
      "signature": "isNullNode(UiDebugVisualElementDto node)"
    },
    {
      "name": "isTreeLayout",
      "signature": "isTreeLayout(String kind,String layoutHint)"
    },
    {
      "name": "layoutTree",
      "signature": "layoutTree(String nodeId,int depth,Map<String,ArrayList<String>>childrenById,Map<String,VisualPoint>positions,java.util.Set<String>visiting,TreeLayoutCursor cursor)"
    },
    {
      "name": "metadataInt",
      "signature": "metadataInt(UiDebugVisualElementDto element,String key,int fallback)"
    },
    {
      "name": "orderedTreeEdges",
      "signature": "orderedTreeEdges(List<UiDebugVisualElementDto>edges)"
    },
    {
      "name": "shortLabel",
      "signature": "shortLabel(String label)"
    },
    {
      "name": "simpleVisualId",
      "signature": "simpleVisualId(UiDebugVisualElementDto element)"
    },
    {
      "name": "treeEdgeOrder",
      "signature": "treeEdgeOrder(String key)"
    },
    {
      "name": "treePositions",
      "signature": "treePositions(List<UiDebugVisualElementDto>nodes,List<UiDebugVisualElementDto>edges)"
    },
    {
      "name": "treeY",
      "signature": "treeY(int depth)"
    },
    {
      "name": "visibleGraphNodes",
      "signature": "visibleGraphNodes(String kind,String layoutHint,List<UiDebugVisualElementDto>nodes,List<UiDebugVisualElementDto>edges)"
    },
    {
      "name": "VisualPoint",
      "signature": "VisualPoint(double x,double y)"
    },
    {
      "name": "visualText",
      "signature": "visualText(String label,double x,double y,double width)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCDebugVisualDiagramRendererProps {
  readonly view: UiDebugDataStructureViewDto | null;
}

export function MiniCDebugVisualDiagramRenderer({ view }: MiniCDebugVisualDiagramRendererProps) {
  return visualDiagram(view);
}

MiniCDebugVisualDiagramRenderer.mirror = miniCDebugVisualDiagramRendererMirror;

export function visualDiagram(view: UiDebugDataStructureViewDto | null) {
  if (!view) {
    return <div className="debug-visual-diagram">暂无数据结构视图</div>;
  }
  return (
    <div className="debug-visuals">
      <h3 className="debug-section-title">visual structures</h3>
      {view.visuals.length === 0 ? (
        <p className="debug-section-line">(empty)</p>
      ) : (
        view.visuals.map((visual) => visualCard(visual))
      )}
    </div>
  );
}

export function visualCard(visual: UiDebugVisualStructureDto) {
  const elementLines = compactVisualElementLines(visual);
  const counts = compactVisualCounts(visual.elements);
  return (
    <article className="debug-visual-card" key={visual.id}>
      <h3 className="debug-visual-title">
        {visual.type} {visual.name} · {visual.kind}
      </h3>
      <p className="debug-section-line">{visual.summary}</p>
      {counts.length > 0 && <p className="debug-section-line">{counts}</p>}
      {visual.explanation.trim().length > 0 && <p className="debug-section-line">{visual.explanation}</p>}
      <div className="debug-visual-diagram">
        {elementLines.length === 0 ? (
          <p className="debug-section-line">(empty)</p>
        ) : (
          elementLines.map((line) => (
            <p className="debug-section-line" key={line}>
              {line}
            </p>
          ))
        )}
      </div>
    </article>
  );
}

export function visualText(element: UiDebugVisualElementDto): string {
  const name = element.metadata.fieldName ?? element.label;
  const value = element.metadata.valueSummary ?? "";
  const pointerTarget = element.metadata.pointerTarget ?? "";
  const type = element.metadata.type ?? element.metadata.typeName ?? "";
  if (pointerTarget.trim().length > 0) {
    return `${name}${type.trim().length === 0 ? "" : ` : ${type}`} -> ${pointerTarget}`;
  }
  if (value.trim().length > 0) {
    return `${name}${type.trim().length === 0 ? "" : ` : ${type}`} = ${value}`;
  }
  return `${name}${type.trim().length === 0 ? "" : ` : ${type}`}`;
}

export function compactVisualCounts(elements: readonly UiDebugVisualElementDto[]): string {
  const cells = elements.filter((element) => element.kind === "ARRAY_CELL").length;
  const nodes = elements.filter((element) => element.kind === "GRAPH_NODE").length;
  const edges = elements.filter((element) => element.kind === "GRAPH_EDGE").length;
  const fields = elements.filter((element) => element.kind === "COMPOSITE_PART").length;
  const parts = [
    cells > 0 ? `cells=${cells}` : "",
    nodes > 0 ? `nodes=${nodes}` : "",
    edges > 0 ? `edges=${edges}` : "",
    fields > 0 ? `fields=${fields}` : "",
  ].filter((part) => part.length > 0);
  return parts.join(" · ");
}

export function compactVisualElementLines(visual: UiDebugVisualStructureDto): readonly string[] {
  return visual.elements
    .filter((element) => element.kind !== "GRAPH_EDGE")
    .map(visualText)
    .filter((line) => line.trim().length > 0)
    .slice(0, 12);
}

export function simpleVisualId(index: number): string {
  return `visual-${index}`;
}

export function shortLabel(label: string): string {
  return label.length <= 20 ? label : `${label.slice(0, 17)}...`;
}

export default MiniCDebugVisualDiagramRenderer;
