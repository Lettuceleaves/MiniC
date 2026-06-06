import type { JavaMirrorFile } from "../translation/javaMirror";
import type { ReactElement } from "react";
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
  const diagram = visualStructureDiagram(visual);
  const elementLines = diagram === null ? compactVisualElementLines(visual) : [];
  const counts = compactVisualCounts(visual.elements);
  return (
    <article className="debug-visual-card" key={visual.id}>
      <h3 className="debug-visual-title">
        {visual.type} {visual.name} · {visual.kind}
      </h3>
      <p className="debug-section-line">{visual.summary}</p>
      {counts.length > 0 && <p className="debug-section-line">{counts}</p>}
      {visual.explanation.trim().length > 0 && <p className="debug-section-line">{visual.explanation}</p>}
      {diagram ?? (
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
      )}
    </article>
  );
}

const VISUAL_CELL_SIZE = 44;
const VISUAL_NODE_RADIUS = VISUAL_CELL_SIZE / 2;
const VISUAL_NULL_SIZE = 16;
const VISUAL_GRID_GAP = 34;
const VISUAL_MARGIN = 24;
const MIN_GRAPH_WIDTH = 220;
const MIN_GRAPH_HEIGHT = 150;

interface VisualPoint {
  readonly x: number;
  readonly y: number;
}

interface TreeLayoutCursor {
  nextLeafX: number;
}

export function visualStructureDiagram(visual: UiDebugVisualStructureDto): ReactElement | null {
  const arrayCells = visual.elements.filter((element) => element.kind === "ARRAY_CELL");
  if (arrayCells.length > 0) {
    return arrayDiagram(arrayCells);
  }
  const graphNodes = visual.elements.filter((element) => element.kind === "GRAPH_NODE");
  if (graphNodes.length > 0) {
    const graphEdges = visual.elements.filter((element) => element.kind === "GRAPH_EDGE");
    return graphDiagram(visual.kind, visual.layoutHint, graphNodes, graphEdges);
  }
  return null;
}

export function arrayDiagram(cells: readonly UiDebugVisualElementDto[]): ReactElement {
  const width = VISUAL_MARGIN * 2 + VISUAL_CELL_SIZE * cells.length;
  const height = VISUAL_MARGIN * 2 + VISUAL_CELL_SIZE;
  return (
    <svg className="debug-visual-diagram" height={height} viewBox={`0 0 ${width} ${height}`} width={width} role="img">
      {cells.map((cell, index) => {
        const x = VISUAL_MARGIN + index * VISUAL_CELL_SIZE;
        const y = VISUAL_MARGIN;
        return (
          <g key={cell.id}>
            <title>{visualText(cell)}</title>
            <rect className="debug-array-cell" height={VISUAL_CELL_SIZE} width={VISUAL_CELL_SIZE} x={x} y={y} />
            {visualLabel(shortLabel(cell.label), x + VISUAL_CELL_SIZE / 2, y + VISUAL_CELL_SIZE / 2 + 4)}
          </g>
        );
      })}
    </svg>
  );
}

export function graphDiagram(
  kind: string,
  layoutHint: string,
  nodes: readonly UiDebugVisualElementDto[],
  edges: readonly UiDebugVisualElementDto[],
): ReactElement {
  const visibleNodes = visibleGraphNodes(kind, layoutHint, nodes, edges);
  const nodesById = new Map(visibleNodes.map((node) => [simpleVisualId(node), node]));
  const positions = graphPositions(kind, layoutHint, visibleNodes, edges);
  const width = Math.max(MIN_GRAPH_WIDTH, maxCoordinate(positions, "x", 160) + VISUAL_MARGIN);
  const height = Math.max(MIN_GRAPH_HEIGHT, maxCoordinate(positions, "y", 100) + VISUAL_MARGIN);
  return (
    <svg className="debug-visual-diagram" height={height} viewBox={`0 0 ${width} ${height}`} width={width} role="img">
      {edges.map((edge) => {
        const from = positions.get(metadata(edge, "from"));
        const to = positions.get(metadata(edge, "to"));
        if (!from || !to) {
          return null;
        }
        return arrow(from, to, isNullNodeById(nodesById, metadata(edge, "to")), edge);
      })}
      {visibleNodes.map((node) => {
        const point = positions.get(simpleVisualId(node));
        if (!point) {
          return null;
        }
        if (isNullNode(node)) {
          return (
            <g key={node.id}>
              <title>{visualText(node)}</title>
              <rect
                className="debug-null-node"
                height={VISUAL_NULL_SIZE}
                width={VISUAL_NULL_SIZE}
                x={point.x - VISUAL_NULL_SIZE / 2}
                y={point.y - VISUAL_NULL_SIZE / 2}
              />
            </g>
          );
        }
        return (
          <g key={node.id}>
            <title>{visualText(node)}</title>
            <circle className="debug-graph-node" cx={point.x} cy={point.y} r={VISUAL_NODE_RADIUS} />
            {visualLabel(shortLabel(node.label), point.x, point.y + 4)}
          </g>
        );
      })}
    </svg>
  );
}

function visualLabel(label: string, x: number, y: number): ReactElement {
  return (
    <text className="debug-visual-label" textAnchor="middle" dominantBaseline="middle" x={x} y={y}>
      {label}
    </text>
  );
}

export function arrow(
  from: VisualPoint,
  to: VisualPoint,
  nullTarget: boolean,
  edge: UiDebugVisualElementDto,
): ReactElement {
  const angle = Math.atan2(to.y - from.y, to.x - from.x);
  const startX = from.x + Math.cos(angle) * VISUAL_NODE_RADIUS;
  const startY = from.y + Math.sin(angle) * VISUAL_NODE_RADIUS;
  const targetRadius = nullTarget ? VISUAL_NULL_SIZE / 2 : VISUAL_NODE_RADIUS;
  const endX = to.x - Math.cos(angle) * targetRadius;
  const endY = to.y - Math.sin(angle) * targetRadius;
  const arrowSize = 8;
  const head = [
    [endX, endY],
    [endX - Math.cos(angle - Math.PI / 6) * arrowSize, endY - Math.sin(angle - Math.PI / 6) * arrowSize],
    [endX - Math.cos(angle + Math.PI / 6) * arrowSize, endY - Math.sin(angle + Math.PI / 6) * arrowSize],
  ].map(([x, y]) => `${x},${y}`).join(" ");
  return (
    <g key={edge.id}>
      <title>{visualText(edge)}</title>
      <line className="debug-graph-edge debug-pointer-arrow" x1={startX} x2={endX} y1={startY} y2={endY} />
      <polygon className="debug-graph-edge-head debug-pointer-arrow" points={head} />
    </g>
  );
}

export function visibleGraphNodes(
  kind: string,
  layoutHint: string,
  nodes: readonly UiDebugVisualElementDto[],
  edges: readonly UiDebugVisualElementDto[],
): readonly UiDebugVisualElementDto[] {
  if (!isTreeLayout(kind, layoutHint)) {
    return nodes;
  }
  const nodeIds = new Set(nodes.map(simpleVisualId));
  const edgeNodeIds = new Set<string>();
  edges.forEach((edge) => {
    const from = metadata(edge, "from");
    const to = metadata(edge, "to");
    if (from.length > 0) {
      edgeNodeIds.add(from);
    }
    if (to.length > 0) {
      edgeNodeIds.add(to);
    }
  });
  return nodes.filter((node) => {
    const id = simpleVisualId(node);
    const summary = metadata(node, "summary");
    return edgeNodeIds.has(id) || summary.length === 0 || !nodeIds.has(summary);
  });
}

export function graphPositions(
  kind: string,
  layoutHint: string,
  nodes: readonly UiDebugVisualElementDto[],
  edges: readonly UiDebugVisualElementDto[],
): Map<string, VisualPoint> {
  if (isTreeLayout(kind, layoutHint)) {
    return treePositions(nodes, edges);
  }
  if (isBucketedLayout(kind, layoutHint)) {
    return bucketedPositions(nodes);
  }
  const positions = new Map<string, VisualPoint>();
  nodes.forEach((node, index) => {
    positions.set(simpleVisualId(node), {
      x: VISUAL_MARGIN + VISUAL_NODE_RADIUS + index * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP),
      y: VISUAL_MARGIN + VISUAL_NODE_RADIUS,
    });
  });
  return positions;
}

export function isTreeLayout(kind: string, layoutHint: string): boolean {
  return kind === "tree" || kind === "binary_tree" || kind === "binary-tree" || layoutHint === "hierarchical";
}

export function isBucketedLayout(kind: string, layoutHint: string): boolean {
  return kind === "hash-chain-table" || kind === "adjacency-list" || layoutHint === "bucketed" || layoutHint === "bucket_graph";
}

export function bucketedPositions(nodes: readonly UiDebugVisualElementDto[]): Map<string, VisualPoint> {
  const positions = new Map<string, VisualPoint>();
  const buckets = nodes
    .filter((node) => metadata(node, "visual-role") === "bucket")
    .sort((left, right) => metadataInt(left, "bucketIndex", Number.MAX_SAFE_INTEGER) - metadataInt(right, "bucketIndex", Number.MAX_SAFE_INTEGER));
  const bucketXByIndex = new Map<string, number>();
  const bucketY = VISUAL_MARGIN + VISUAL_NODE_RADIUS;
  buckets.forEach((bucket, index) => {
    const bucketIndex = metadataInt(bucket, "bucketIndex", index);
    const x = VISUAL_MARGIN + VISUAL_NODE_RADIUS + index * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP);
    bucketXByIndex.set(String(bucketIndex), x);
    positions.set(simpleVisualId(bucket), { x, y: bucketY });
  });
  nodes
    .filter((node) => metadata(node, "visual-role") === "chain-node")
    .sort((left, right) => {
      const bucketDelta = metadataInt(left, "bucketIndex", Number.MAX_SAFE_INTEGER) - metadataInt(right, "bucketIndex", Number.MAX_SAFE_INTEGER);
      return bucketDelta !== 0 ? bucketDelta : metadataInt(left, "chainDepth", Number.MAX_SAFE_INTEGER) - metadataInt(right, "chainDepth", Number.MAX_SAFE_INTEGER);
    })
    .forEach((node) => {
      const bucketIndex = metadata(node, "bucketIndex", "0");
      const chainDepth = metadataInt(node, "chainDepth", 0);
      const fallbackX = VISUAL_MARGIN + VISUAL_NODE_RADIUS + bucketXByIndex.size * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP);
      const x = bucketXByIndex.get(bucketIndex) ?? fallbackX;
      positions.set(simpleVisualId(node), { x, y: bucketY + (chainDepth + 1) * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP) });
    });
  let fallbackIndex = 0;
  nodes.forEach((node) => {
    const id = simpleVisualId(node);
    if (positions.has(id)) {
      return;
    }
    positions.set(id, {
      x: VISUAL_MARGIN + VISUAL_NODE_RADIUS + fallbackIndex * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP),
      y: bucketY + (VISUAL_CELL_SIZE + VISUAL_GRID_GAP),
    });
    fallbackIndex += 1;
  });
  return positions;
}

export function metadataInt(element: UiDebugVisualElementDto, key: string, fallback: number): number {
  const parsed = Number.parseInt(metadata(element, key, String(fallback)), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function treePositions(
  nodes: readonly UiDebugVisualElementDto[],
  edges: readonly UiDebugVisualElementDto[],
): Map<string, VisualPoint> {
  const nodeById = new Map(nodes.map((node) => [simpleVisualId(node), node]));
  const childrenById = new Map<string, string[]>();
  const childIds = new Set<string>();
  nodeById.forEach((_node, id) => childrenById.set(id, []));
  orderedTreeEdges(edges).forEach((edge) => {
    const from = metadata(edge, "from");
    const to = metadata(edge, "to");
    if (nodeById.has(from) && nodeById.has(to)) {
      childrenById.get(from)?.push(to);
      childIds.add(to);
    }
  });
  const roots = [...nodeById.keys()].filter((id) => !childIds.has(id));
  const effectiveRoots = roots.length > 0 ? roots : [...nodeById.keys()];
  const positions = new Map<string, VisualPoint>();
  const cursor: TreeLayoutCursor = { nextLeafX: VISUAL_MARGIN + VISUAL_NODE_RADIUS };
  effectiveRoots.forEach((root) => {
    layoutTree(root, 0, childrenById, positions, new Set(), cursor);
    cursor.nextLeafX += VISUAL_CELL_SIZE + VISUAL_GRID_GAP;
  });
  return positions;
}

export function orderedTreeEdges(edges: readonly UiDebugVisualElementDto[]): readonly UiDebugVisualElementDto[] {
  return [...edges].sort((left, right) => {
    const fromOrder = metadata(left, "from").localeCompare(metadata(right, "from"));
    return fromOrder !== 0 ? fromOrder : treeEdgeOrder(metadata(left, "key", left.label)) - treeEdgeOrder(metadata(right, "key", right.label));
  });
}

export function treeEdgeOrder(key: string): number {
  if (key === "left") {
    return 0;
  }
  if (key === "right") {
    return 1;
  }
  return 2;
}

export function layoutTree(
  nodeId: string,
  depth: number,
  childrenById: ReadonlyMap<string, readonly string[]>,
  positions: Map<string, VisualPoint>,
  visiting: Set<string>,
  cursor: TreeLayoutCursor,
): number {
  if (visiting.has(nodeId)) {
    const x = cursor.nextLeafX;
    cursor.nextLeafX += VISUAL_CELL_SIZE + VISUAL_GRID_GAP;
    positions.set(nodeId, { x, y: treeY(depth) });
    return x;
  }
  visiting.add(nodeId);
  const children = childrenById.get(nodeId) ?? [];
  if (children.length === 0) {
    const x = cursor.nextLeafX;
    cursor.nextLeafX += VISUAL_CELL_SIZE + VISUAL_GRID_GAP;
    positions.set(nodeId, { x, y: treeY(depth) });
    visiting.delete(nodeId);
    return x;
  }
  const childXs = children.map((child) => layoutTree(child, depth + 1, childrenById, positions, visiting, cursor));
  const x = (childXs[0] + childXs[childXs.length - 1]) / 2;
  positions.set(nodeId, { x, y: treeY(depth) });
  visiting.delete(nodeId);
  return x;
}

export function treeY(depth: number): number {
  return VISUAL_MARGIN + VISUAL_NODE_RADIUS + depth * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP);
}

function isNullNodeById(nodesById: ReadonlyMap<string, UiDebugVisualElementDto>, id: string): boolean {
  const node = nodesById.get(id);
  return node !== undefined && isNullNode(node);
}

export function isNullNode(node: UiDebugVisualElementDto): boolean {
  return metadata(node, "visual-null", "false") === "true";
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

export function simpleVisualId(element: UiDebugVisualElementDto): string {
  const metadataId = metadata(element, "id");
  if (metadataId.trim().length > 0) {
    return metadataId;
  }
  const lastDash = element.id.lastIndexOf("-");
  return lastDash < 0 ? element.id : element.id.slice(lastDash + 1);
}

export function shortLabel(label: string): string {
  const compact = label.replaceAll("\n", " ").trim();
  return compact.length <= 10 ? compact : `${compact.slice(0, 9)}...`;
}

function metadata(element: UiDebugVisualElementDto, key: string, fallback = ""): string {
  return element.metadata[key] ?? fallback;
}

function maxCoordinate(positions: ReadonlyMap<string, VisualPoint>, axis: keyof VisualPoint, fallback: number): number {
  return [...positions.values()].reduce((max, point) => Math.max(max, point[axis]), fallback);
}

export default MiniCDebugVisualDiagramRenderer;
