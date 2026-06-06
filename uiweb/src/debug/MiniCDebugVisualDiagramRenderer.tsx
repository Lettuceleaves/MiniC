import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiDebugDataStructureViewDto } from "../translation/uiapi";

export const miniCDebugVisualDiagramRendererMirror = {
  "javaPath": "src/main/java/minic/uilocal/debug/MiniCDebugVisualDiagramRenderer.java",
  "webPath": "uiweb/src/debug/MiniCDebugVisualDiagramRenderer.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCDebugVisualDiagramRenderer",
  "kind": "component",
  "imports": [
    "javafx.scene.Node",
    "javafx.scene.layout.Pane",
    "javafx.scene.shape.Circle",
    "javafx.scene.shape.Line",
    "javafx.scene.shape.Polygon",
    "javafx.scene.shape.Rectangle",
    "javafx.scene.text.Text",
    "minic.color.ThemeRegistry",
    "minic.uiapi.UiDebugVisualElementDto",
    "minic.uiapi.UiDebugVisualStructureDto",
    "java.util.ArrayList",
    "java.util.Comparator",
    "java.util.HashSet",
    "java.util.LinkedHashMap",
    "java.util.List",
    "java.util.Map",
    "java.util.function.BiConsumer",
    "java.util.stream.Collectors"
  ],
  "fields": [
    {
      "name": "VISUAL_CELL_SIZE",
      "signature": "private static final double VISUAL_CELL_SIZE ="
    },
    {
      "name": "VISUAL_NODE_RADIUS",
      "signature": "private static final double VISUAL_NODE_RADIUS ="
    },
    {
      "name": "VISUAL_NULL_SIZE",
      "signature": "private static final double VISUAL_NULL_SIZE ="
    },
    {
      "name": "VISUAL_GRID_GAP",
      "signature": "private static final double VISUAL_GRID_GAP ="
    },
    {
      "name": "VISUAL_MARGIN",
      "signature": "private static final double VISUAL_MARGIN ="
    },
    {
      "name": "tooltipInstaller",
      "signature": "private final BiConsumer<Node, UiDebugVisualElementDto> tooltipInstaller;"
    },
    {
      "name": "nextLeafX",
      "signature": "private double nextLeafX ="
    }
  ],
  "methods": [
    {
      "name": "arrayDiagram",
      "signature": "arrayDiagram(List<UiDebugVisualElementDto> cells)"
    },
    {
      "name": "graphDiagram",
      "signature": "graphDiagram(String kind, String layoutHint, List<UiDebugVisualElementDto> nodes, List<UiDebugVisualElementDto> edges)"
    },
    {
      "name": "isNullNode",
      "signature": "isNullNode(Map<String, UiDebugVisualElementDto> nodesById, String id)"
    },
    {
      "name": "isNullNode",
      "signature": "isNullNode(UiDebugVisualElementDto node)"
    },
    {
      "name": "visibleGraphNodes",
      "signature": "visibleGraphNodes(String kind, String layoutHint, List<UiDebugVisualElementDto> nodes, List<UiDebugVisualElementDto> edges)"
    },
    {
      "name": "graphPositions",
      "signature": "graphPositions(String kind, String layoutHint, List<UiDebugVisualElementDto> nodes, List<UiDebugVisualElementDto> edges)"
    },
    {
      "name": "isTreeLayout",
      "signature": "isTreeLayout(String kind, String layoutHint)"
    },
    {
      "name": "isBucketedLayout",
      "signature": "isBucketedLayout(String kind, String layoutHint)"
    },
    {
      "name": "bucketedPositions",
      "signature": "bucketedPositions(List<UiDebugVisualElementDto> nodes)"
    },
    {
      "name": "metadataInt",
      "signature": "metadataInt(UiDebugVisualElementDto element, String key, int fallback)"
    },
    {
      "name": "treePositions",
      "signature": "treePositions(List<UiDebugVisualElementDto> nodes, List<UiDebugVisualElementDto> edges)"
    },
    {
      "name": "orderedTreeEdges",
      "signature": "orderedTreeEdges(List<UiDebugVisualElementDto> edges)"
    },
    {
      "name": "treeEdgeOrder",
      "signature": "treeEdgeOrder(String key)"
    },
    {
      "name": "layoutTree",
      "signature": "layoutTree(String nodeId, int depth, Map<String, ArrayList<String>> childrenById, Map<String, VisualPoint> positions, java.util.Set<String> visiting, TreeLayoutCursor cursor)"
    },
    {
      "name": "treeY",
      "signature": "treeY(int depth)"
    },
    {
      "name": "arrow",
      "signature": "arrow(VisualPoint from, VisualPoint to, boolean nullTarget)"
    },
    {
      "name": "visualText",
      "signature": "visualText(String label, double x, double y, double width)"
    },
    {
      "name": "simpleVisualId",
      "signature": "simpleVisualId(UiDebugVisualElementDto element)"
    },
    {
      "name": "shortLabel",
      "signature": "shortLabel(String label)"
    },
    {
      "name": "VisualPoint",
      "signature": "VisualPoint(double x, double y)"
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
      <article className="debug-visual-card">
        <h3 className="debug-visual-title">{view.title}</h3>
        <div className="debug-visual-diagram">
          {view.rows.length === 0 ? (
            <p className="debug-section-line">暂无结构项</p>
          ) : (
            view.rows.map((row, index) => (
              <p className="debug-section-line" key={`${row}-${index}`}>
                {visualText(row)}
              </p>
            ))
          )}
        </div>
      </article>
    </div>
  );
}

export function visualText(text: string): string {
  return text.trim().length === 0 ? " " : text;
}

export function simpleVisualId(index: number): string {
  return `visual-${index}`;
}

export function shortLabel(label: string): string {
  return label.length <= 20 ? label : `${label.slice(0, 17)}...`;
}

export default MiniCDebugVisualDiagramRenderer;
