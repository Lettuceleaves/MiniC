import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiAstNodeVisualDto, UiSemanticScopeVisualDto, UiStageVisualDto } from "../translation/uiapi";
import { MiniCAstGraphModelFactory } from "./MiniCAstGraphModelFactory";

export const miniCVisualAstGraphRendererMirror = {
  "javaPath": "src/main/java/minic/uilocal/visual/MiniCVisualAstGraphRenderer.java",
  "webPath": "uiweb/src/visual/MiniCVisualAstGraphRenderer.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCVisualAstGraphRenderer",
  "kind": "component",
  "imports": [
    "javafx.application.Platform",
    "javafx.geometry.BoundingBox",
    "javafx.geometry.Bounds",
    "javafx.geometry.Point2D",
    "javafx.scene.Group",
    "javafx.scene.Node",
    "javafx.scene.Parent",
    "javafx.scene.control.Label",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.control.Slider",
    "javafx.scene.control.SplitPane",
    "javafx.scene.input.MouseButton",
    "javafx.scene.input.MouseEvent",
    "javafx.scene.input.ScrollEvent",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Pane",
    "javafx.scene.layout.VBox",
    "javafx.scene.shape.Circle",
    "javafx.scene.shape.Line",
    "javafx.scene.shape.Rectangle",
    "javafx.scene.text.Text",
    "minic.color.ThemeRegistry",
    "minic.settings.MiniCSettings",
    "minic.uilocal.control.MiniCGraphViewportAdapter",
    "minic.uilocal.control.MiniCViewportAdapter",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "minic.uiapi.UiAstNodeVisualDto",
    "minic.uiapi.UiSemanticScopeVisualDto",
    "minic.uiapi.UiSourceSpanDto",
    "minic.uiapi.UiStageVisualDto",
    "java.util.ArrayList",
    "java.util.List",
    "java.util.function.BiConsumer",
    "java.util.function.BiFunction",
    "java.util.function.Consumer",
    "java.util.function.Supplier"
  ],
  "fields": [
    {
      "name": "ACTIVE_CENTER_Y_KEY",
      "signature": "private static final String ACTIVE_CENTER_Y_KEY ="
    },
    {
      "name": "AST_DRAG_START_X_KEY",
      "signature": "private static final String AST_DRAG_START_X_KEY ="
    },
    {
      "name": "AST_DRAG_START_Y_KEY",
      "signature": "private static final String AST_DRAG_START_Y_KEY ="
    },
    {
      "name": "AST_DRAG_START_H_KEY",
      "signature": "private static final String AST_DRAG_START_H_KEY ="
    },
    {
      "name": "AST_DRAG_START_V_KEY",
      "signature": "private static final String AST_DRAG_START_V_KEY ="
    },
    {
      "name": "AST_GRAPH_ZOOM_CONTENT_KEY",
      "signature": "private static final String AST_GRAPH_ZOOM_CONTENT_KEY ="
    },
    {
      "name": "astGraphModelFactory",
      "signature": "private final MiniCAstGraphModelFactory astGraphModelFactory ="
    },
    {
      "name": "astZoom",
      "signature": "private final Slider astZoom;"
    },
    {
      "name": "controlHubSupplier",
      "signature": "private final Supplier<MiniCWorkbenchControlHub> controlHubSupplier;"
    },
    {
      "name": "selectedSemanticScopeId",
      "signature": "private final Supplier<String> selectedSemanticScopeId;"
    },
    {
      "name": "semanticScopeSelector",
      "signature": "private final Consumer<String> semanticScopeSelector;"
    },
    {
      "name": "refreshAction",
      "signature": "private final Runnable refreshAction;"
    },
    {
      "name": "astContentFactory",
      "signature": "private final BiFunction<UiAstNodeVisualDto, UiStageVisualDto, MiniCHoverInspectorContent> astContentFactory;"
    },
    {
      "name": "semanticScopeContentFactory",
      "signature": "private final SemanticScopeContentFactory semanticScopeContentFactory;"
    },
    {
      "name": "inspectorAttacher",
      "signature": "private final BiConsumer<Node, MiniCHoverInspectorContent> inspectorAttacher;"
    }
  ],
  "methods": [
    {
      "name": "astGraph",
      "signature": "astGraph(UiStageVisualDto visual)"
    },
    {
      "name": "semanticAstGraph",
      "signature": "semanticAstGraph(UiStageVisualDto visual)"
    },
    {
      "name": "addAstNode",
      "signature": "addAstNode(Pane pane, UiStageVisualDto visual, MiniCAstGraphNode node)"
    },
    {
      "name": "addSemanticScopeMasks",
      "signature": "addSemanticScopeMasks(Pane pane, MiniCAstGraphModel graph, UiStageVisualDto visual)"
    },
    {
      "name": "scopeBounds",
      "signature": "scopeBounds(UiSourceSpanDto scopeRange, MiniCAstGraphModel graph, UiAstNodeVisualDto root)"
    },
    {
      "name": "collectCoveredGraphNodes",
      "signature": "collectCoveredGraphNodes(UiSourceSpanDto scopeRange, UiAstNodeVisualDto astNode, MiniCAstGraphModel graph, ArrayList<MiniCAstGraphNode> covered)"
    },
    {
      "name": "zoomableAstGraph",
      "signature": "zoomableAstGraph(UiStageVisualDto visual, boolean semanticMasks)"
    },
    {
      "name": "configureAstGraphWheelZoom",
      "signature": "configureAstGraphWheelZoom(Pane graphViewport)"
    },
    {
      "name": "configureAstGraphDrag",
      "signature": "configureAstGraphDrag(Pane graphViewport)"
    },
    {
      "name": "installGraphAdapterLater",
      "signature": "installGraphAdapterLater(Pane graphViewport)"
    },
    {
      "name": "graphViewportAdapter",
      "signature": "graphViewportAdapter(Pane graphViewport)"
    },
    {
      "name": "nearestScrollPane",
      "signature": "nearestScrollPane(Node node)"
    },
    {
      "name": "graphZoomPoint",
      "signature": "graphZoomPoint(Pane graphViewport, double localX, double localY)"
    },
    {
      "name": "graphLocalPointFromViewportPoint",
      "signature": "graphLocalPointFromViewportPoint(Node zoomContent, ScrollPane scrollPane, Point2D viewportPoint)"
    },
    {
      "name": "graphZoomContent",
      "signature": "graphZoomContent(Pane graphViewport)"
    },
    {
      "name": "resizeGraphViewport",
      "signature": "resizeGraphViewport(Pane graphViewport, double baseWidth, double baseHeight, double zoom)"
    },
    {
      "name": "setAstZoom",
      "signature": "setAstZoom(double value)"
    },
    {
      "name": "visibleMin",
      "signature": "visibleMin(double value, double min, double max, double contentMin, double contentSize, double viewportSize)"
    },
    {
      "name": "clamp",
      "signature": "clamp(double value)"
    },
    {
      "name": "updateZoomedActiveMarker",
      "signature": "updateZoomedActiveMarker(VBox box, Pane graph, double zoom)"
    },
    {
      "name": "emptyPane",
      "signature": "emptyPane(String message)"
    },
    {
      "name": "shortLabel",
      "signature": "shortLabel(String label)"
    },
    {
      "name": "flattenScopes",
      "signature": "flattenScopes(UiSemanticScopeVisualDto root)"
    },
    {
      "name": "flattenScopes",
      "signature": "flattenScopes(UiSemanticScopeVisualDto scope, int depth, ArrayList<ScopeEntry> scopes)"
    },
    {
      "name": "contains",
      "signature": "contains(UiSourceSpanDto outer, UiSourceSpanDto inner)"
    },
    {
      "name": "astNodeById",
      "signature": "astNodeById(UiAstNodeVisualDto node, String id)"
    },
    {
      "name": "ScopeEntry",
      "signature": "ScopeEntry(UiSemanticScopeVisualDto scope, int depth)"
    },
    {
      "name": "BoundsBox",
      "signature": "BoundsBox(double x, double y, double width, double height)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCVisualAstGraphRendererProps {
  readonly visual: UiStageVisualDto | null;
  readonly semanticMasks?: boolean;
  readonly zoom?: number;
  readonly onAstNodeSelect?: (node: UiAstNodeVisualDto) => void;
  readonly selectedSemanticScopeId?: string;
  readonly onSemanticScopeSelect?: (scopeId: string) => void;
}

const astGraphModelFactory = new MiniCAstGraphModelFactory();

export function MiniCVisualAstGraphRenderer({
  visual,
  semanticMasks = false,
  zoom = 1,
  onAstNodeSelect,
}: MiniCVisualAstGraphRendererProps) {
  if (!visual?.astRoot) {
    return emptyPane("暂无 AST 图");
  }
  const root = visual.astRoot;
  const model = astGraphModelFactory.create(visual);
  return (
    <div className="ast-graph-viewport">
      <svg
        className="ast-graph"
        height={model.height * zoom}
        viewBox={`0 0 ${model.width} ${model.height}`}
        width={model.width * zoom}
        role="img"
        aria-label="MiniC AST graph"
      >
        {semanticMasks && visual.semanticRoot ? addSemanticScopeMasks(visual.semanticRoot) : null}
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
        {model.nodes.map((node) => {
          const astNode = astNodeById(root, node.id);
          return (
            <g key={node.id} onClick={() => astNode && onAstNodeSelect?.(astNode)} role="button" tabIndex={0}>
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
          );
        })}
      </svg>
    </div>
  );
}

MiniCVisualAstGraphRenderer.mirror = miniCVisualAstGraphRendererMirror;

export function astGraph(visual: UiStageVisualDto | null) {
  return <MiniCVisualAstGraphRenderer visual={visual} />;
}

export function semanticAstGraph(visual: UiStageVisualDto | null) {
  return <MiniCVisualAstGraphRenderer semanticMasks visual={visual} />;
}

export function addSemanticScopeMasks(root: UiSemanticScopeVisualDto) {
  const scopes = flattenScopes(root).slice(0, 4);
  return scopes.map((entry, index) => (
    <rect
      className={`semantic-graph-scope-mask-${index % 4}${entry.scope.active ? " active-scope-mask" : ""}`}
      height={42 + entry.depth * 16}
      key={entry.scope.id}
      width={160 + entry.depth * 24}
      x={24 + index * 26}
      y={24 + index * 20}
    />
  ));
}

export function emptyPane(message: string) {
  return <div className="visual-canvas">{message}</div>;
}

export function shortLabel(label: string): string {
  return label.length <= 18 ? label : `${label.slice(0, 15)}...`;
}

interface ScopeEntry {
  readonly scope: UiSemanticScopeVisualDto;
  readonly depth: number;
}

export function flattenScopes(root: UiSemanticScopeVisualDto): readonly ScopeEntry[] {
  const scopes: ScopeEntry[] = [];
  const visit = (scope: UiSemanticScopeVisualDto, depth: number): void => {
    scopes.push({ scope, depth });
    scope.children.forEach((child) => visit(child, depth + 1));
  };
  visit(root, 0);
  return scopes;
}

export function contains(outer: { startOffset: number; endOffset: number }, inner: { startOffset: number; endOffset: number }): boolean {
  return outer.startOffset <= inner.startOffset && outer.endOffset >= inner.endOffset;
}

export function astNodeById(node: UiAstNodeVisualDto, id: string): UiAstNodeVisualDto | null {
  if (node.id === id) {
    return node;
  }
  for (const child of node.children) {
    const found = astNodeById(child, id);
    if (found) {
      return found;
    }
  }
  return null;
}

export default MiniCVisualAstGraphRenderer;
