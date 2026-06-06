import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCDebugViewportControllerMirror = {
  "javaPath": "src/main/java/minic/uilocal/debug/MiniCDebugViewportController.java",
  "webPath": "uiweb/src/debug/MiniCDebugViewportController.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCDebugViewportController",
  "kind": "class",
  "imports": [
    "javafx.application.Platform",
    "javafx.geometry.Bounds",
    "javafx.geometry.Point2D",
    "javafx.scene.Node",
    "javafx.scene.Parent",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.control.Slider",
    "javafx.scene.input.MouseButton",
    "javafx.scene.input.MouseEvent",
    "javafx.scene.input.ScrollEvent",
    "javafx.scene.layout.Pane",
    "javafx.scene.control.SplitPane",
    "minic.settings.MiniCSettings",
    "minic.uilocal.control.MiniCGraphViewportAdapter",
    "minic.uilocal.control.MiniCScrollPaneViewportAdapter",
    "minic.uilocal.control.MiniCViewportAdapter",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "java.util.ArrayList",
    "java.util.List"
  ],
  "fields": [
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
      "name": "SCROLL_VIEWPORT_FILTER_INSTALLED_KEY",
      "signature": "private static final String SCROLL_VIEWPORT_FILTER_INSTALLED_KEY ="
    },
    {
      "name": "SCROLL_DRAG_START_X_KEY",
      "signature": "private static final String SCROLL_DRAG_START_X_KEY ="
    },
    {
      "name": "SCROLL_DRAG_START_Y_KEY",
      "signature": "private static final String SCROLL_DRAG_START_Y_KEY ="
    },
    {
      "name": "controlHub",
      "signature": "private final MiniCWorkbenchControlHub controlHub;"
    },
    {
      "name": "astZoom",
      "signature": "private final Slider astZoom;"
    }
  ],
  "methods": [
    {
      "name": "configureAstWheelZoom",
      "signature": "configureAstWheelZoom(Pane graphViewport)"
    },
    {
      "name": "configureAstDrag",
      "signature": "configureAstDrag(Pane graphViewport)"
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
      "name": "scrollPaneViewportAdapter",
      "signature": "scrollPaneViewportAdapter(ScrollPane scrollPane)"
    },
    {
      "name": "collectScrollViewportAdapters",
      "signature": "collectScrollViewportAdapters(Node node, List<MiniCViewportAdapter> adapters)"
    },
    {
      "name": "collectGraphViewportAdapters",
      "signature": "collectGraphViewportAdapters(Node node, List<MiniCViewportAdapter> adapters)"
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
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCDebugViewportController {
  static readonly mirror = miniCDebugViewportControllerMirror;

  readonly mirror = miniCDebugViewportControllerMirror;

  summary(): string {
    return `MiniCDebugViewportController: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCDebugViewportController;
