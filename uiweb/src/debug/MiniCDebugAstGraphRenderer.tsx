import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiDebugAstViewDto, UiStageVisualDto } from "../translation/uiapi";
import { MiniCVisualAstGraphRenderer } from "../visual/MiniCVisualAstGraphRenderer";

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
      "signature": "private final MiniCAstGraphModelFactory astGraphModelFactory ="
    },
    {
      "name": "astZoom",
      "signature": "private final Slider astZoom;"
    },
    {
      "name": "viewportController",
      "signature": "private final MiniCDebugViewportController viewportController;"
    }
  ],
  "methods": [
    {
      "name": "emptyAstPane",
      "signature": "emptyAstPane(String message)"
    },
    {
      "name": "astNodeById",
      "signature": "astNodeById(UiAstNodeVisualDto node, String id)"
    },
    {
      "name": "shortLabel",
      "signature": "shortLabel(String label)"
    },
    {
      "name": "rangeText",
      "signature": "rangeText(UiSourceSpanDto range)"
    },
    {
      "name": "label",
      "signature": "label(String text, String styleClass)"
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
  const visual: UiStageVisualDto = {
    stage: "parser",
    visualType: "debug-ast",
    sourceText: "",
    genericItems: view.details,
    lexerTokens: [],
    astRoot: view.root,
    semanticRoot: null,
    semanticEdgesPointChildToParent: false,
    irLines: [],
    assemblyLines: [],
  };
  return <MiniCVisualAstGraphRenderer visual={visual} zoom={zoom} />;
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

export default MiniCDebugAstGraphRenderer;
