import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiAstNodeVisualDto, UiDebugAstViewDto } from "../translation/uiapi";
import { MiniCAstGraphModelFactory } from "../visual/MiniCAstGraphModelFactory";

export const miniCDebugAstGraphRendererMirror = {
  "javaPath": "src/main/java/minic/uilocal/debug/MiniCDebugAstGraphRenderer.java",
  "webPath": "uiweb/src/debug/MiniCDebugAstGraphRenderer.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCDebugAstGraphRenderer",
  "kind": "component",
  "imports": [
    "javafx.scene.Group",
    "javafx.scene.Node",
    "javafx.scene.control.Label",
    "javafx.scene.control.Slider",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Pane",
    "javafx.scene.layout.VBox",
    "javafx.scene.shape.Circle",
    "javafx.scene.shape.Line",
    "javafx.scene.text.Text",
    "minic.color.ThemeRegistry",
    "minic.uiapi.UiAstNodeVisualDto",
    "minic.uiapi.UiDebugAstViewDto",
    "minic.uiapi.UiSourceSpanDto"
  ],
  "fields": [
    {
      "name": "astGraphModelFactory",
      "signature": "private final MiniCAstGraphModelFactory astGraphModelFactory="
    },
    {
      "name": "astZoom",
      "signature": "private final Slider astZoom"
    },
    {
      "name": "viewportController",
      "signature": "private final MiniCDebugViewportController viewportController"
    }
  ],
  "methods": [
    {
      "name": "astNodeById",
      "signature": "astNodeById(UiAstNodeVisualDto node,String id)"
    },
    {
      "name": "emptyAstPane",
      "signature": "emptyAstPane(String message)"
    },
    {
      "name": "label",
      "signature": "label(String text,String styleClass)"
    },
    {
      "name": "rangeText",
      "signature": "rangeText(UiSourceSpanDto range)"
    },
    {
      "name": "shortLabel",
      "signature": "shortLabel(String label)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCDebugAstGraphRendererProps {
  readonly view: UiDebugAstViewDto | null;
  readonly zoom?: number;
}

export function MiniCDebugAstGraphRenderer({ view, zoom = 1 }: MiniCDebugAstGraphRendererProps) {
  return debugAstGraph(view, zoom);
}

MiniCDebugAstGraphRenderer.mirror = miniCDebugAstGraphRendererMirror;

export function debugAstGraph(view: UiDebugAstViewDto | null, zoom = 1) {
  if (!view?.root) {
    return emptyAstPane();
  }
  const model = new MiniCAstGraphModelFactory().create(view.root);
  return (
    <div className="ast-graph-viewport">
      <svg
        className="ast-graph"
        height={model.height * zoom}
        viewBox={`0 0 ${model.width} ${model.height}`}
        width={model.width * zoom}
        role="img"
        aria-label="MiniC debug AST graph"
      >
        {model.edges.map((edge) => (
          <line
            className={`ast-edge${edge.hot ? " active" : ""}`}
            key={`${edge.fromId}-${edge.toId}`}
            x1={edge.fromX}
            x2={edge.toX}
            y1={edge.fromY}
            y2={edge.toY}
          />
        ))}
        {model.nodes.map((node) => (
          <g key={node.id}>
            <circle
              className={`ast-graph-node${node.root ? " root" : ""}${node.leaf ? " leaf" : ""}${node.active ? " active" : ""}`}
              cx={node.x}
              cy={node.y}
              r={28}
            />
            <text className="ast-graph-label" textAnchor="middle" x={node.x} y={node.y + 4}>
              {shortLabel(node.label)}
            </text>
          </g>
        ))}
      </svg>
    </div>
  );
}

export function emptyAstPane() {
  return <div className="visual-canvas">暂无调试 AST</div>;
}

export function shortLabel(label: string): string {
  return label.length <= 18 ? label : `${label.slice(0, 15)}...`;
}

export function rangeText(startOffset: number, endOffset: number): string {
  return `${startOffset}-${endOffset}`;
}

export function label(text: string) {
  return <span className="debug-section-line">{text}</span>;
}

export function astNodeById(node: UiAstNodeVisualDto, id: string): UiAstNodeVisualDto | null {
  if (node.id === id) {
    return node;
  }
  for (const child of node.children) {
    const found = astNodeById(child, id);
    if (found !== null) {
      return found;
    }
  }
  return null;
}

export default MiniCDebugAstGraphRenderer;
