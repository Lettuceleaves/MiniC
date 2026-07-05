import type { JavaMirrorFile } from "../translation/javaMirror";
import type { CSSProperties, ReactElement } from "react";
import type { UiDebugDataStructureViewDto, UiDebugVisualElementDto, UiDebugVisualStructureDto } from "../translation/uiapi";

export const miniCDebugVisualDiagramRendererMirror = {
  "javaPath": "src/main/java/minic/uilocal/debug/MiniCDebugVisualDiagramRenderer.java",
  "webPath": "uiweb/src/debug/MiniCDebugVisualDiagramRenderer.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCDebugVisualDiagramRenderer",
  "kind": "component",
  "imports": [
    "java.util.ArrayList",
    "java.util.LinkedHashMap",
    "java.util.List",
    "java.util.Map",
    "java.util.function.BiConsumer",
    "java.util.stream.Collectors",
    "javafx.scene.Node",
    "javafx.scene.layout.Pane",
    "javafx.scene.paint.Color",
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
      "name": "VISUAL_GRID_UNIT",
      "signature": "private static final double VISUAL_GRID_UNIT="
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
      "name": "addVisualStyleClasses",
      "signature": "addVisualStyleClasses(Node node,UiDebugVisualElementDto element)"
    },
    {
      "name": "applyVisualShapeStyle",
      "signature": "applyVisualShapeStyle(Node shape,UiDebugVisualElementDto element)"
    },
    {
      "name": "applyVisualTextStyle",
      "signature": "applyVisualTextStyle(Text text,UiDebugVisualElementDto element)"
    },
    {
      "name": "arrayDiagram",
      "signature": "arrayDiagram(String layoutHint,List<UiDebugVisualElementDto>cells)"
    },
    {
      "name": "arrow",
      "signature": "arrow(VisualPoint from,VisualPoint to,boolean nullTarget)"
    },
    {
      "name": "fitText",
      "signature": "fitText(String value,int capacity)"
    },
    {
      "name": "graphDiagram",
      "signature": "graphDiagram(String kind,String layoutHint,List<UiDebugVisualElementDto>nodes,List<UiDebugVisualElementDto>edges)"
    },
    {
      "name": "GridBounds",
      "signature": "GridBounds(double minX,double minY,double maxX,double maxY)"
    },
    {
      "name": "GridRect",
      "signature": "GridRect(double x,double y,double width,double height)"
    },
    {
      "name": "gridArrow",
      "signature": "gridArrow(VisualPoint from,VisualPoint to,UiDebugVisualElementDto edge)"
    },
    {
      "name": "gridBounds",
      "signature": "gridBounds(List<GridRect>rects,List<VisualPoint>points)"
    },
    {
      "name": "gridGraphDiagram",
      "signature": "gridGraphDiagram(List<UiDebugVisualElementDto>nodes,List<UiDebugVisualElementDto>edges)"
    },
    {
      "name": "gridNodeContent",
      "signature": "gridNodeContent(UiDebugVisualElementDto node,boolean square,double x,double y,double width,double height)"
    },
    {
      "name": "gridVisualDiagram",
      "signature": "gridVisualDiagram(List<UiDebugVisualElementDto>cells,List<UiDebugVisualElementDto>nodes,List<UiDebugVisualElementDto>edges)"
    },
    {
      "name": "gridPoint",
      "signature": "gridPoint(UiDebugVisualElementDto edge,String xKey,String yKey)"
    },
    {
      "name": "gridRect",
      "signature": "gridRect(UiDebugVisualElementDto node)"
    },
    {
      "name": "gridStructTable",
      "signature": "gridStructTable(UiDebugVisualElementDto node,double x,double y,double width,double height)"
    },
    {
      "name": "graphPositions",
      "signature": "graphPositions(List<UiDebugVisualElementDto>nodes)"
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
      "name": "metadataInt",
      "signature": "metadataInt(UiDebugVisualElementDto element,String key,int fallback)"
    },
    {
      "name": "metadataNumber",
      "signature": "metadataNumber(UiDebugVisualElementDto element,String key)"
    },
    {
      "name": "shortLabel",
      "signature": "shortLabel(String label)"
    },
    {
      "name": "shortArrayLabel",
      "signature": "shortArrayLabel(String label)"
    },
    {
      "name": "simpleVisualId",
      "signature": "simpleVisualId(UiDebugVisualElementDto element)"
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
const VISUAL_GRID_UNIT = 22;
const MIN_GRAPH_WIDTH = 220;
const MIN_GRAPH_HEIGHT = 150;

interface VisualPoint {
  readonly x: number;
  readonly y: number;
}

export function visualStructureDiagram(visual: UiDebugVisualStructureDto): ReactElement | null {
  const arrayCells = visual.elements.filter((element) => element.kind === "ARRAY_CELL");
  const graphNodes = visual.elements.filter((element) => element.kind === "GRAPH_NODE");
  const graphEdges = visual.elements.filter((element) => element.kind === "GRAPH_EDGE");
  if (visual.layoutHint === "grid") {
    return gridVisualDiagram(arrayCells, graphNodes, graphEdges);
  }
  if (arrayCells.length > 0) {
    return arrayDiagram(visual.layoutHint, arrayCells);
  }
  if (graphNodes.length > 0) {
    return graphDiagram(visual.kind, visual.layoutHint, graphNodes, graphEdges);
  }
  return null;
}

export function arrayDiagram(layoutHint: string, cells: readonly UiDebugVisualElementDto[]): ReactElement {
  const matrixLayout = layoutHint === "grid" || layoutHint === "matrix" || cells.some((cell) => metadataInt(cell, "row", 0) > 0);
  const rows = matrixLayout ? Math.max(1, ...cells.map((cell) => metadataInt(cell, "row", 0) + 1)) : 1;
  const columns = matrixLayout ? Math.max(1, ...cells.map((cell) => metadataInt(cell, "column", 0) + 1)) : cells.length;
  const width = VISUAL_MARGIN * 2 + VISUAL_CELL_SIZE * Math.max(columns, 1);
  const height = VISUAL_MARGIN * 2 + VISUAL_CELL_SIZE * Math.max(rows, 1);
  return (
    <svg className="debug-visual-diagram" height={height} viewBox={`0 0 ${width} ${height}`} width={width} role="img">
      {cells.map((cell, index) => {
        const row = matrixLayout ? metadataInt(cell, "row", 0) : 0;
        const column = matrixLayout ? metadataInt(cell, "column", index) : index;
        const x = VISUAL_MARGIN + column * VISUAL_CELL_SIZE;
        const y = VISUAL_MARGIN + row * VISUAL_CELL_SIZE;
        return (
          <g key={cell.id}>
            <title>{visualText(cell)}</title>
            <rect className="debug-array-cell" height={VISUAL_CELL_SIZE} width={VISUAL_CELL_SIZE} x={x} y={y} />
            {visualLabel(shortArrayLabel(cell.label), x + VISUAL_CELL_SIZE / 2, y + VISUAL_CELL_SIZE / 2 + 4)}
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
  if (layoutHint === "grid") {
    return gridGraphDiagram(nodes, edges);
  }
  const visibleNodes = nodes;
  const nodesById = new Map(visibleNodes.map((node) => [simpleVisualId(node), node]));
  const positions = graphPositions(visibleNodes);
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
            <circle className={graphNodeClass(node)} cx={point.x} cy={point.y} r={VISUAL_NODE_RADIUS} style={visualShapeStyle(node)} />
            {visualLabel(shortLabel(node.label), point.x, point.y + 4, undefined, visualTextStyle(node))}
          </g>
        );
      })}
    </svg>
  );
}

export function gridGraphDiagram(
  nodes: readonly UiDebugVisualElementDto[],
  edges: readonly UiDebugVisualElementDto[],
): ReactElement {
  return gridVisualDiagram([], nodes, edges);
}

export function gridVisualDiagram(
  cells: readonly UiDebugVisualElementDto[],
  nodes: readonly UiDebugVisualElementDto[],
  edges: readonly UiDebugVisualElementDto[],
): ReactElement {
  const rects = [...cells, ...nodes]
    .map((node) => ({ node, rect: gridRect(node) }))
    .filter((entry): entry is { readonly node: UiDebugVisualElementDto; readonly rect: GridRect } => entry.rect !== null);
  const lines = edges
    .map((edge) => ({ edge, start: gridPoint(edge, "gridStartX", "gridStartY"), end: gridPoint(edge, "gridEndX", "gridEndY") }))
    .filter((entry): entry is { readonly edge: UiDebugVisualElementDto; readonly start: VisualPoint; readonly end: VisualPoint } => entry.start !== null && entry.end !== null);
  const bounds = gridBounds(rects.map((entry) => entry.rect), lines.flatMap((line) => [line.start, line.end]));
  const width = Math.max(MIN_GRAPH_WIDTH, VISUAL_MARGIN * 2 + (bounds.maxX - bounds.minX) * VISUAL_GRID_UNIT);
  const height = Math.max(MIN_GRAPH_HEIGHT, VISUAL_MARGIN * 2 + (bounds.maxY - bounds.minY) * VISUAL_GRID_UNIT);
  const toSvgX = (x: number) => VISUAL_MARGIN + (x - bounds.minX) * VISUAL_GRID_UNIT;
  const toSvgY = (y: number) => VISUAL_MARGIN + (y - bounds.minY) * VISUAL_GRID_UNIT;
  return (
    <svg className="debug-visual-diagram" height={height} viewBox={`0 0 ${width} ${height}`} width={width} role="img">
      {lines.map(({ edge, start, end }) => gridArrow(
        { x: toSvgX(start.x), y: toSvgY(start.y) },
        { x: toSvgX(end.x), y: toSvgY(end.y) },
        edge,
      ))}
      {rects.map(({ node, rect }) => {
        const x = toSvgX(rect.x);
        const y = toSvgY(rect.y);
        const width = rect.width * VISUAL_GRID_UNIT;
        const height = rect.height * VISUAL_GRID_UNIT;
        return (
          <g key={node.id}>
            <title>{visualText(node)}</title>
            <rect className={gridNodeClass(node)} height={height} width={width} x={x} y={y} style={visualShapeStyle(node)} />
            {gridNodeContent(node, node.kind === "ARRAY_CELL" || metadata(node, "visual-shape") === "SQUARE", x, y, width, height)}
          </g>
        );
      })}
    </svg>
  );
}

function gridNodeContent(
  node: UiDebugVisualElementDto,
  square: boolean,
  x: number,
  y: number,
  width: number,
  height: number,
): readonly ReactElement[] {
  if (!square && metadata(node, "visual-content") === "STRUCT_TABLE") {
    return gridStructTable(node, x, y, width, height);
  }
  return [
    visualLabel(
      square ? shortArrayLabel(node.label) : shortLabel(node.label),
      x + width / 2,
      y + height / 2 + 4,
      `${node.id}-label`,
      visualTextStyle(node),
    ),
  ];
}

function gridStructTable(
  node: UiDebugVisualElementDto,
  x: number,
  y: number,
  width: number,
  height: number,
): readonly ReactElement[] {
  const rowCount = Math.max(1, metadataInt(node, "visual-row-count", 1));
  const rowHeight = height / rowCount;
  const capacity = metadataInt(node, "visual-row-capacity", 12);
  const content: ReactElement[] = [];
  for (let row = 1; row < rowCount; row += 1) {
    const lineY = y + row * rowHeight;
    content.push(
      <line
        className="debug-grid-table-line"
        key={`${node.id}-table-line-${row}`}
        x1={x}
        x2={x + width}
        y1={lineY}
        y2={lineY}
      />,
    );
  }
  for (let row = 0; row < rowCount; row += 1) {
    content.push(visualLabel(
      fitText(metadata(node, `visual-row.${row}`), capacity),
      x + width / 2,
      y + row * rowHeight + rowHeight / 2 + 4,
      `${node.id}-table-label-${row}`,
      visualTextStyle(node),
    ));
  }
  return content;
}

interface GridRect {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

interface GridBounds {
  readonly minX: number;
  readonly minY: number;
  readonly maxX: number;
  readonly maxY: number;
}

function gridRect(node: UiDebugVisualElementDto): GridRect | null {
  const x = metadataNumber(node, "gridX");
  const y = metadataNumber(node, "gridY");
  const width = metadataNumber(node, "gridWidth");
  const height = metadataNumber(node, "gridHeight");
  if (x === null || y === null || width === null || height === null) {
    return null;
  }
  return { x, y, width, height };
}

function gridPoint(edge: UiDebugVisualElementDto, xKey: string, yKey: string): VisualPoint | null {
  const x = metadataNumber(edge, xKey);
  const y = metadataNumber(edge, yKey);
  return x === null || y === null ? null : { x, y };
}

function gridBounds(rects: readonly GridRect[], points: readonly VisualPoint[]): GridBounds {
  const minX = Math.min(0, ...rects.map((rect) => rect.x), ...points.map((point) => point.x));
  const minY = Math.min(0, ...rects.map((rect) => rect.y), ...points.map((point) => point.y));
  const maxX = Math.max(1, ...rects.map((rect) => rect.x + rect.width), ...points.map((point) => point.x));
  const maxY = Math.max(1, ...rects.map((rect) => rect.y + rect.height), ...points.map((point) => point.y));
  return { minX, minY, maxX, maxY };
}

function metadataNumber(element: UiDebugVisualElementDto, key: string): number | null {
  const value = metadata(element, key);
  if (value.trim().length === 0) {
    return null;
  }
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : null;
}

export function gridArrow(from: VisualPoint, to: VisualPoint, edge: UiDebugVisualElementDto): ReactElement {
  const angle = Math.atan2(to.y - from.y, to.x - from.x);
  const arrowSize = 8;
  const head = [
    [to.x, to.y],
    [to.x - Math.cos(angle - Math.PI / 6) * arrowSize, to.y - Math.sin(angle - Math.PI / 6) * arrowSize],
    [to.x - Math.cos(angle + Math.PI / 6) * arrowSize, to.y - Math.sin(angle + Math.PI / 6) * arrowSize],
  ].map(([x, y]) => `${x},${y}`).join(" ");
  return (
    <g key={edge.id}>
      <title>{visualText(edge)}</title>
      <line className="debug-graph-edge debug-pointer-arrow" x1={from.x} x2={to.x} y1={from.y} y2={to.y} />
      <polygon className="debug-graph-edge-head debug-pointer-arrow" points={head} />
    </g>
  );
}

function visualLabel(label: string, x: number, y: number, key?: string, style?: CSSProperties): ReactElement {
  return (
    <text className="debug-visual-label" textAnchor="middle" dominantBaseline="middle" x={x} y={y} key={key} style={style}>
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

export function graphPositions(nodes: readonly UiDebugVisualElementDto[]): Map<string, VisualPoint> {
  const positions = new Map<string, VisualPoint>();
  nodes.forEach((node, index) => {
    positions.set(simpleVisualId(node), {
      x: VISUAL_MARGIN + VISUAL_NODE_RADIUS + index * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP),
      y: VISUAL_MARGIN + VISUAL_NODE_RADIUS,
    });
  });
  return positions;
}

export function metadataInt(element: UiDebugVisualElementDto, key: string, fallback: number): number {
  const parsed = Number.parseInt(metadata(element, key, String(fallback)), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function isNullNodeById(nodesById: ReadonlyMap<string, UiDebugVisualElementDto>, id: string): boolean {
  const node = nodesById.get(id);
  return node !== undefined && isNullNode(node);
}

export function isNullNode(node: UiDebugVisualElementDto): boolean {
  return metadata(node, "visual-null", "false") === "true";
}

function graphNodeClass(node: UiDebugVisualElementDto): string {
  return withVisualStyleClass("debug-graph-node", node);
}

function gridNodeClass(node: UiDebugVisualElementDto): string {
  if (node.kind === "ARRAY_CELL" || metadata(node, "visual-shape") === "SQUARE") {
    return withVisualStyleClass("debug-grid-square", node);
  }
  return withVisualStyleClass("debug-grid-node", node);
}

function withVisualStyleClass(baseClass: string, node: UiDebugVisualElementDto): string {
  const extraClass = metadata(node, "visual-style-class").trim();
  return extraClass.length === 0 ? baseClass : `${baseClass} ${extraClass}`;
}

function visualShapeStyle(node: UiDebugVisualElementDto): CSSProperties | undefined {
  const fill = metadata(node, "visual-fill").trim();
  const stroke = metadata(node, "visual-stroke").trim();
  if (fill.length === 0 && stroke.length === 0) {
    return undefined;
  }
  return {
    ...(fill.length > 0 ? { fill } : {}),
    ...(stroke.length > 0 ? { stroke } : {}),
  };
}

function visualTextStyle(node: UiDebugVisualElementDto): CSSProperties | undefined {
  const fill = metadata(node, "visual-text-fill").trim();
  return fill.length === 0 ? undefined : { fill };
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

export function shortArrayLabel(label: string): string {
  const compact = label.replaceAll("\n", " ").trim();
  const capacity = 4;
  if (compact.length <= capacity) {
    return compact;
  }
  return `${compact.slice(0, capacity - 3)}...`;
}

export function fitText(value: string, requestedCapacity: number): string {
  const capacity = Math.max(3, requestedCapacity);
  const compact = value.replaceAll("\n", " ").trim();
  if (compact.length <= capacity) {
    return compact;
  }
  if (capacity === 3) {
    return "...";
  }
  return `${compact.slice(0, capacity - 3)}...`;
}

function metadata(element: UiDebugVisualElementDto, key: string, fallback = ""): string {
  return element.metadata[key] ?? fallback;
}

function maxCoordinate(positions: ReadonlyMap<string, VisualPoint>, axis: keyof VisualPoint, fallback: number): number {
  return [...positions.values()].reduce((max, point) => Math.max(max, point[axis]), fallback);
}

export default MiniCDebugVisualDiagramRenderer;
