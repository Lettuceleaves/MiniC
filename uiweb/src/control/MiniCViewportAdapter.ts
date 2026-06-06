import { MiniCControlTargetType } from "./MiniCControlTargetType";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { ViewportPoint } from "../translation/uiTypes";

export const miniCViewportAdapterMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCViewportAdapter.java",
  "webPath": "uiweb/src/control/MiniCViewportAdapter.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCViewportAdapter",
  "kind": "interface",
  "imports": [
    "javafx.geometry.Point2D"
  ],
  "fields": [],
  "methods": []
} as const satisfies JavaMirrorFile;

export interface MiniCViewportAdapter {
  readonly mirror?: JavaMirrorFile;
  type(): MiniCControlTargetType;
  canZoom(): boolean;
  zoomAt(localPoint: ViewportPoint, delta: number): void;
  canScrollVertical(): boolean;
  scrollVertical(delta: number): void;
  canScrollHorizontal(): boolean;
  scrollHorizontal(delta: number): void;
  canPan(): boolean;
  pan(deltaX: number, deltaY: number): void;
  isActiveFullyVisible(): boolean;
  centerActiveIfNeeded(): void;
  centerActive(): void;
}

const noopAdapter: MiniCViewportAdapter = {
  mirror: miniCViewportAdapterMirror,
  type: () => MiniCControlTargetType.NONE,
  canZoom: () => false,
  zoomAt: () => undefined,
  canScrollVertical: () => false,
  scrollVertical: () => undefined,
  canScrollHorizontal: () => false,
  scrollHorizontal: () => undefined,
  canPan: () => false,
  pan: () => undefined,
  isActiveFullyVisible: () => true,
  centerActiveIfNeeded: () => undefined,
  centerActive: () => undefined,
};

export const MiniCViewportAdapter = {
  mirror: miniCViewportAdapterMirror,
  noop(): MiniCViewportAdapter {
    return noopAdapter;
  },
  summary(): string {
    return `MiniCViewportAdapter: ${miniCViewportAdapterMirror.methods.length} methods, ${miniCViewportAdapterMirror.fields.length} fields`;
  },
} as const;

export function centerActiveIfNeeded(adapter: MiniCViewportAdapter): void {
  if (!adapter.isActiveFullyVisible()) {
    adapter.centerActive();
  }
}

export default MiniCViewportAdapter;
