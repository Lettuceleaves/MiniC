import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCGraphViewportAdapter } from "../control/MiniCGraphViewportAdapter";
import { MiniCScrollPaneViewportAdapter } from "../control/MiniCScrollPaneViewportAdapter";
import type { MiniCViewportAdapter } from "../control/MiniCViewportAdapter";
import { MiniCWorkbenchControlHub } from "../control/MiniCWorkbenchControlHub";
import { MiniCSettings } from "../settings/MiniCSettings";
import type { ViewportPoint } from "../translation/uiTypes";

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

  private readonly graphAdapters = new WeakMap<HTMLElement, MiniCGraphViewportAdapter>();
  private readonly scrollAdapters = new WeakMap<HTMLElement, MiniCScrollPaneViewportAdapter>();
  private astZoomValue: number;

  constructor(
    private readonly controlHub = new MiniCWorkbenchControlHub(),
    astZoom = 1,
  ) {
    this.astZoomValue = astZoom;
  }

  configureAstGraphViewport(graphViewport: HTMLElement, zoomContent: HTMLElement, baseWidth: number, baseHeight: number): void {
    graphZoomContentMap.set(graphViewport, zoomContent);
    this.resizeGraphViewport(graphViewport, baseWidth, baseHeight, this.astZoomValue);
    this.configureAstWheelZoom(graphViewport);
    this.configureAstDrag(graphViewport);
    this.installGraphAdapterLater(graphViewport);
  }

  configureAstWheelZoom(graphViewport: HTMLElement): void {
    graphViewport.addEventListener("wheel", (event) => {
      if (event.deltaY === 0) {
        return;
      }
      const delta = event.deltaY < 0 ? this.graphZoomStep() : -this.graphZoomStep();
      const adapter = this.graphViewportAdapter(graphViewport);
      if (adapter === null) {
        this.setAstZoom(this.astZoomValue + delta);
      } else {
        this.controlHub.viewportRegistry().businessActive(adapter);
        adapter.zoomAt(this.graphZoomPoint(graphViewport, event.offsetX, event.offsetY), delta);
      }
      event.preventDefault();
    });
  }

  configureAstDrag(graphViewport: HTMLElement): void {
    let start: { readonly x: number; readonly y: number } | null = null;
    graphViewport.addEventListener("mousedown", (event) => {
      if (event.button !== 2) {
        return;
      }
      start = { x: event.screenX, y: event.screenY };
      event.preventDefault();
    });
    graphViewport.addEventListener("mousemove", (event) => {
      if (start === null || event.buttons !== 2) {
        return;
      }
      const adapter = this.graphViewportAdapter(graphViewport);
      if (adapter !== null) {
        this.controlHub.viewportRegistry().businessActive(adapter);
        adapter.pan(start.x - event.screenX, start.y - event.screenY);
      }
      start = { x: event.screenX, y: event.screenY };
      event.preventDefault();
    });
  }

  installGraphAdapterLater(graphViewport: HTMLElement): void {
    window.requestAnimationFrame(() => this.graphViewportAdapter(graphViewport));
  }

  graphViewportAdapter(graphViewport: HTMLElement): MiniCGraphViewportAdapter | null {
    const existing = this.graphAdapters.get(graphViewport);
    if (existing !== undefined) {
      return existing;
    }
    const scrollPane = this.nearestScrollPane(graphViewport);
    if (scrollPane === null) {
      return null;
    }
    const adapter = new MiniCGraphViewportAdapter(scrollPane, this.graphZoomContent(graphViewport), (_point, delta) => {
      this.setAstZoom(this.astZoomValue + delta);
    });
    this.graphAdapters.set(graphViewport, adapter);
    this.controlHub.installViewportTarget(graphViewport, adapter);
    return adapter;
  }

  installScrollViewportTarget(scrollPane: HTMLElement): void {
    const adapter = this.scrollPaneViewportAdapter(scrollPane);
    this.controlHub.installViewportTarget(scrollPane, adapter);
  }

  scrollPaneViewportAdapter(scrollPane: HTMLElement): MiniCScrollPaneViewportAdapter {
    const existing = this.scrollAdapters.get(scrollPane);
    if (existing !== undefined) {
      return existing;
    }
    const adapter = new MiniCScrollPaneViewportAdapter(scrollPane);
    this.scrollAdapters.set(scrollPane, adapter);
    return adapter;
  }

  activeViewportAdapters(root: HTMLElement, sourceAdapter: MiniCViewportAdapter): readonly MiniCViewportAdapter[] {
    const adapters: MiniCViewportAdapter[] = [sourceAdapter];
    this.collectScrollViewportAdapters(root, adapters);
    this.collectGraphViewportAdapters(root, adapters);
    return adapters;
  }

  collectScrollViewportAdapters(node: HTMLElement, adapters: MiniCViewportAdapter[]): void {
    const adapter = this.scrollAdapters.get(node);
    if (adapter !== undefined) {
      adapters.push(adapter);
    }
    node.querySelectorAll<HTMLElement>("*").forEach((child) => {
      const childAdapter = this.scrollAdapters.get(child);
      if (childAdapter !== undefined) {
        adapters.push(childAdapter);
      }
    });
  }

  collectGraphViewportAdapters(node: HTMLElement, adapters: MiniCViewportAdapter[]): void {
    const adapter = this.graphAdapters.get(node);
    if (adapter !== undefined) {
      adapters.push(adapter);
    }
    node.querySelectorAll<HTMLElement>("*").forEach((child) => {
      const childAdapter = this.graphAdapters.get(child);
      if (childAdapter !== undefined) {
        adapters.push(childAdapter);
      }
    });
  }

  nearestScrollPane(node: HTMLElement): HTMLElement | null {
    let parent = node.parentElement;
    while (parent !== null) {
      if (parent.scrollHeight > parent.clientHeight || parent.scrollWidth > parent.clientWidth) {
        return parent;
      }
      parent = parent.parentElement;
    }
    return null;
  }

  graphZoomPoint(graphViewport: HTMLElement, localX: number, localY: number): ViewportPoint {
    if (MiniCSettings.graphZoomAnchoredAtMouse()) {
      return { x: localX, y: localY };
    }
    const scrollPane = this.nearestScrollPane(graphViewport);
    if (scrollPane === null) {
      return { x: localX, y: localY };
    }
    return this.graphLocalPointFromViewportPoint(this.graphZoomContent(graphViewport), scrollPane, {
      x: scrollPane.clientWidth / 2,
      y: scrollPane.clientHeight / 2,
    });
  }

  graphLocalPointFromViewportPoint(zoomContent: HTMLElement, scrollPane: HTMLElement, viewportPoint: ViewportPoint): ViewportPoint {
    const contentRect = zoomContent.getBoundingClientRect();
    const scrollRect = scrollPane.getBoundingClientRect();
    return {
      x: scrollPane.scrollLeft + viewportPoint.x + scrollRect.left - contentRect.left,
      y: scrollPane.scrollTop + viewportPoint.y + scrollRect.top - contentRect.top,
    };
  }

  graphZoomContent(graphViewport: HTMLElement): HTMLElement {
    return graphZoomContentMap.get(graphViewport) ?? graphViewport;
  }

  resizeGraphViewport(graphViewport: HTMLElement, baseWidth: number, baseHeight: number, zoom: number): void {
    graphViewport.style.minWidth = `${Math.max(1, baseWidth * zoom)}px`;
    graphViewport.style.minHeight = `${Math.max(1, baseHeight * zoom)}px`;
  }

  setAstZoom(value: number): void {
    this.astZoomValue = Math.max(0.05, Math.min(1, value));
  }

  graphZoomStep(): number {
    return MiniCSettings.graphZoomStep();
  }

  visibleMin(value: number, min: number, max: number, contentMin: number, contentSize: number, viewportSize: number): number {
    const maxOffset = Math.max(0, contentSize - viewportSize);
    return max <= min ? contentMin : contentMin + this.clamp((value - min) / (max - min)) * maxOffset;
  }

  clamp(value: number): number {
    return Math.max(0, Math.min(1, value));
  }
}

const graphZoomContentMap = new WeakMap<HTMLElement, HTMLElement>();

export default MiniCDebugViewportController;
