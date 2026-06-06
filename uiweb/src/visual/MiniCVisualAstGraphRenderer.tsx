import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiAstNodeVisualDto, UiSemanticScopeVisualDto, UiStageVisualDto } from "../translation/uiapi";
import type { MiniCAstGraphModel } from "./MiniCAstGraphModel";
import { MiniCAstGraphModelFactory } from "./MiniCAstGraphModelFactory";
import type { MiniCAstGraphNode } from "./MiniCAstGraphNode";

export const miniCVisualAstGraphRendererMirror = {
  "javaPath": "src/main/java/minic/uilocal/visual/MiniCVisualAstGraphRenderer.java",
  "webPath": "uiweb/src/visual/MiniCVisualAstGraphRenderer.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCVisualAstGraphRenderer",
  "kind": "component",
  "imports": [
    "java.util.ArrayList",
    "java.util.List",
    "java.util.function.BiConsumer",
    "java.util.function.BiFunction",
    "java.util.function.Consumer",
    "java.util.function.Supplier",
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
    "minic.uiapi.UiAstNodeVisualDto",
    "minic.uiapi.UiSemanticScopeVisualDto",
    "minic.uiapi.UiSourceSpanDto",
    "minic.uiapi.UiStageVisualDto",
    "minic.uilocal.control.MiniCGraphViewportAdapter",
    "minic.uilocal.control.MiniCViewportAdapter",
    "minic.uilocal.control.MiniCWorkbenchControlHub"
  ],
  "fields": [
    {
      "name": "ACTIVE_CENTER_Y_KEY",
      "signature": "private static final String ACTIVE_CENTER_Y_KEY="
    },
    {
      "name": "AST_DRAG_START_H_KEY",
      "signature": "private static final String AST_DRAG_START_H_KEY="
    },
    {
      "name": "AST_DRAG_START_V_KEY",
      "signature": "private static final String AST_DRAG_START_V_KEY="
    },
    {
      "name": "AST_DRAG_START_X_KEY",
      "signature": "private static final String AST_DRAG_START_X_KEY="
    },
    {
      "name": "AST_DRAG_START_Y_KEY",
      "signature": "private static final String AST_DRAG_START_Y_KEY="
    },
    {
      "name": "AST_GRAPH_ZOOM_CONTENT_KEY",
      "signature": "private static final String AST_GRAPH_ZOOM_CONTENT_KEY="
    },
    {
      "name": "astContentFactory",
      "signature": "private final BiFunction<UiAstNodeVisualDto,UiStageVisualDto,MiniCHoverInspectorContent>astContentFactory"
    },
    {
      "name": "astGraphModelFactory",
      "signature": "private final MiniCAstGraphModelFactory astGraphModelFactory="
    },
    {
      "name": "astZoom",
      "signature": "private final Slider astZoom"
    },
    {
      "name": "controlHubSupplier",
      "signature": "private final Supplier<MiniCWorkbenchControlHub>controlHubSupplier"
    },
    {
      "name": "inspectorAttacher",
      "signature": "private final BiConsumer<Node,MiniCHoverInspectorContent>inspectorAttacher"
    },
    {
      "name": "refreshAction",
      "signature": "private final Runnable refreshAction"
    },
    {
      "name": "selectedSemanticScopeId",
      "signature": "private final Supplier<String>selectedSemanticScopeId"
    },
    {
      "name": "semanticScopeContentFactory",
      "signature": "private final SemanticScopeContentFactory semanticScopeContentFactory"
    },
    {
      "name": "semanticScopeSelector",
      "signature": "private final Consumer<String>semanticScopeSelector"
    }
  ],
  "methods": [
    {
      "name": "addAstNode",
      "signature": "addAstNode(Pane pane,UiStageVisualDto visual,MiniCAstGraphNode node)"
    },
    {
      "name": "addSemanticScopeMasks",
      "signature": "addSemanticScopeMasks(Pane pane,MiniCAstGraphModel graph,UiStageVisualDto visual)"
    },
    {
      "name": "astGraph",
      "signature": "astGraph(UiStageVisualDto visual)"
    },
    {
      "name": "astNodeById",
      "signature": "astNodeById(UiAstNodeVisualDto node,String id)"
    },
    {
      "name": "BoundsBox",
      "signature": "BoundsBox(double x,double y,double width,double height)"
    },
    {
      "name": "clamp",
      "signature": "clamp(double value)"
    },
    {
      "name": "collectCoveredGraphNodes",
      "signature": "collectCoveredGraphNodes(UiSourceSpanDto scopeRange,UiAstNodeVisualDto astNode,MiniCAstGraphModel graph,ArrayList<MiniCAstGraphNode>covered)"
    },
    {
      "name": "configureAstGraphDrag",
      "signature": "configureAstGraphDrag(Pane graphViewport)"
    },
    {
      "name": "configureAstGraphWheelZoom",
      "signature": "configureAstGraphWheelZoom(Pane graphViewport)"
    },
    {
      "name": "contains",
      "signature": "contains(UiSourceSpanDto outer,UiSourceSpanDto inner)"
    },
    {
      "name": "emptyPane",
      "signature": "emptyPane(String message)"
    },
    {
      "name": "flattenScopes",
      "signature": "flattenScopes(UiSemanticScopeVisualDto root)"
    },
    {
      "name": "flattenScopes",
      "signature": "flattenScopes(UiSemanticScopeVisualDto scope,int depth,ArrayList<ScopeEntry>scopes)"
    },
    {
      "name": "graphLocalPointFromViewportPoint",
      "signature": "graphLocalPointFromViewportPoint(Node zoomContent,ScrollPane scrollPane,Point2D viewportPoint)"
    },
    {
      "name": "graphViewportAdapter",
      "signature": "graphViewportAdapter(Pane graphViewport)"
    },
    {
      "name": "graphZoomContent",
      "signature": "graphZoomContent(Pane graphViewport)"
    },
    {
      "name": "graphZoomPoint",
      "signature": "graphZoomPoint(Pane graphViewport,double localX,double localY)"
    },
    {
      "name": "installGraphAdapterLater",
      "signature": "installGraphAdapterLater(Pane graphViewport)"
    },
    {
      "name": "nearestScrollPane",
      "signature": "nearestScrollPane(Node node)"
    },
    {
      "name": "resizeGraphViewport",
      "signature": "resizeGraphViewport(Pane graphViewport,double baseWidth,double baseHeight,double zoom)"
    },
    {
      "name": "scopeBounds",
      "signature": "scopeBounds(UiSourceSpanDto scopeRange,MiniCAstGraphModel graph,UiAstNodeVisualDto root)"
    },
    {
      "name": "ScopeEntry",
      "signature": "ScopeEntry(UiSemanticScopeVisualDto scope,int depth)"
    },
    {
      "name": "semanticAstGraph",
      "signature": "semanticAstGraph(UiStageVisualDto visual)"
    },
    {
      "name": "setAstZoom",
      "signature": "setAstZoom(double value)"
    },
    {
      "name": "shortLabel",
      "signature": "shortLabel(String label)"
    },
    {
      "name": "updateZoomedActiveMarker",
      "signature": "updateZoomedActiveMarker(VBox box,Pane graph,double zoom)"
    },
    {
      "name": "visibleMin",
      "signature": "visibleMin(double value,double min,double max,double contentMin,double contentSize,double viewportSize)"
    },
    {
      "name": "zoomableAstGraph",
      "signature": "zoomableAstGraph(UiStageVisualDto visual,boolean semanticMasks)"
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
  selectedSemanticScopeId,
  onSemanticScopeSelect,
}: MiniCVisualAstGraphRendererProps) {
  if (!visual?.astRoot) {
    return emptyPane("AST 尚未就绪");
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
        {semanticMasks && visual.semanticRoot
          ? addSemanticScopeMasks(visual.semanticRoot, model, root, selectedSemanticScopeId, onSemanticScopeSelect)
          : null}
        {model.edges.map((edge) => (
          <line
            className={`ast-edge${edge.hot ? " hot" : ""}`}
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
                r={node.root ? 30 : node.leaf ? 22 : 26}
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

export function addSemanticScopeMasks(
  root: UiSemanticScopeVisualDto,
  graph: MiniCAstGraphModel,
  astRoot: UiAstNodeVisualDto,
  selectedSemanticScopeId?: string,
  onSemanticScopeSelect?: (scopeId: string) => void,
) {
  return flattenScopes(root).map((entry) => {
    if (!entry.scope.range) {
      return null;
    }
    const bounds = scopeBounds(entry.scope.range, graph, astRoot);
    if (!bounds) {
      return null;
    }
    const className = [
      `semantic-graph-scope-mask-${entry.depth % 4}`,
      entry.scope.active ? "active-scope-mask" : "",
      entry.scope.id === selectedSemanticScopeId ? "selected-scope-mask" : "",
    ].filter(Boolean).join(" ");
    return (
      <rect
        className={className}
        height={bounds.height + 68}
        key={entry.scope.id}
        onClick={(event) => {
          onSemanticScopeSelect?.(entry.scope.id);
          event.stopPropagation();
        }}
        width={bounds.width + 68}
        x={bounds.x - 34}
        y={bounds.y - 34}
      />
    );
  });
}

export function emptyPane(message: string) {
  return <div className="ast-graph"><span className="body-text">{message}</span></div>;
}

export function shortLabel(label: string): string {
  const compact = label.replaceAll("\n", " ").trim();
  return compact.length <= 10 ? compact : `${compact.slice(0, 9)}...`;
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

interface BoundsBox {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

export function scopeBounds(
  scopeRange: { sourceName: string; startOffset: number; endOffset: number },
  graph: MiniCAstGraphModel,
  root: UiAstNodeVisualDto,
): BoundsBox | null {
  const covered: MiniCAstGraphNode[] = [];
  collectCoveredGraphNodes(scopeRange, root, graph, covered);
  if (covered.length === 0) {
    return null;
  }
  const minX = Math.min(...covered.map((node) => node.x));
  const maxX = Math.max(...covered.map((node) => node.x));
  const minY = Math.min(...covered.map((node) => node.y));
  const maxY = Math.max(...covered.map((node) => node.y));
  return { x: minX, y: minY, width: Math.max(1, maxX - minX), height: Math.max(1, maxY - minY) };
}

export function collectCoveredGraphNodes(
  scopeRange: { sourceName: string; startOffset: number; endOffset: number },
  astNode: UiAstNodeVisualDto,
  graph: MiniCAstGraphModel,
  covered: MiniCAstGraphNode[],
): void {
  if (astNode.range && contains(scopeRange, astNode.range)) {
    const graphNode = graph.nodes.find((node) => node.id === astNode.id);
    if (graphNode) {
      covered.push(graphNode);
    }
  }
  astNode.children.forEach((child) => collectCoveredGraphNodes(scopeRange, child, graph, covered));
}

export function contains(
  outer: { sourceName: string; startOffset: number; endOffset: number },
  inner: { sourceName: string; startOffset: number; endOffset: number },
): boolean {
  return outer.sourceName === inner.sourceName
    && outer.startOffset <= inner.startOffset
    && outer.endOffset >= inner.endOffset;
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
