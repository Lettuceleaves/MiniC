import { MiniCControlTargetType } from "./MiniCControlTargetType";
import type { MiniCViewportAdapter } from "./MiniCViewportAdapter";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { ViewportPoint } from "../translation/uiTypes";
import { clampNumber, requireValue } from "../translation/uiTypes";

export const miniCScrollPaneViewportAdapterMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCScrollPaneViewportAdapter.java",
  "webPath": "uiweb/src/control/MiniCScrollPaneViewportAdapter.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCScrollPaneViewportAdapter",
  "kind": "class",
  "imports": [
    "javafx.geometry.Bounds",
    "javafx.scene.Node",
    "javafx.scene.control.ScrollPane",
    "java.util.Objects",
    "java.util.function.DoubleConsumer"
  ],
  "fields": [
    {
      "name": "ADAPTER_PROPERTY",
      "signature": "public static final String ADAPTER_PROPERTY ="
    },
    {
      "name": "scrollPane",
      "signature": "private final ScrollPane scrollPane;"
    }
  ],
  "methods": [
    {
      "name": "type",
      "signature": "type()"
    },
    {
      "name": "canScrollVertical",
      "signature": "canScrollVertical()"
    },
    {
      "name": "scrollVertical",
      "signature": "scrollVertical(double delta)"
    },
    {
      "name": "canScrollHorizontal",
      "signature": "canScrollHorizontal()"
    },
    {
      "name": "scrollHorizontal",
      "signature": "scrollHorizontal(double delta)"
    },
    {
      "name": "canPan",
      "signature": "canPan()"
    },
    {
      "name": "pan",
      "signature": "pan(double deltaX, double deltaY)"
    },
    {
      "name": "scrollBy",
      "signature": "scrollBy(double delta, boolean horizontal)"
    },
    {
      "name": "setAxisByDelta",
      "signature": "setAxisByDelta(double value, double delta, double contentSize, double viewportSize, double min, double max, DoubleConsumer setter)"
    },
    {
      "name": "normalized",
      "signature": "normalized(double value, double min, double max)"
    },
    {
      "name": "clamp",
      "signature": "clamp(double value)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCScrollPaneViewportAdapter implements MiniCViewportAdapter {
  static readonly mirror = miniCScrollPaneViewportAdapterMirror;

  static readonly ADAPTER_PROPERTY = "minic.uilocal.control.scrollPaneViewportAdapter";

  readonly mirror = miniCScrollPaneViewportAdapterMirror;

  private readonly scrollPane: HTMLElement;

  constructor(scrollPane: HTMLElement) {
    this.scrollPane = requireValue(scrollPane, "scrollPane");
  }

  type(): MiniCControlTargetType {
    return MiniCControlTargetType.SCROLL;
  }

  canZoom(): boolean {
    return false;
  }

  zoomAt(): void {
    return undefined;
  }

  canScrollVertical(): boolean {
    return true;
  }

  scrollVertical(delta: number): void {
    this.scrollBy(delta, false);
  }

  canScrollHorizontal(): boolean {
    return true;
  }

  scrollHorizontal(delta: number): void {
    this.scrollBy(delta, true);
  }

  canPan(): boolean {
    return true;
  }

  pan(deltaX: number, deltaY: number): void {
    this.scrollHorizontal(deltaX);
    this.scrollVertical(deltaY);
  }

  isActiveFullyVisible(): boolean {
    return true;
  }

  centerActiveIfNeeded(): void {
    return undefined;
  }

  centerActive(): void {
    return undefined;
  }

  scrollBy(delta: number, horizontal: boolean): void {
    if (!Number.isFinite(delta)) {
      return;
    }
    if (horizontal) {
      this.scrollPane.scrollLeft = MiniCScrollPaneViewportAdapter.setAxisByDelta(
        this.scrollPane.scrollLeft,
        delta,
        this.scrollPane.scrollWidth,
        this.scrollPane.clientWidth,
        0,
        this.maxScrollLeft(),
      );
      return;
    }
    this.scrollPane.scrollTop = MiniCScrollPaneViewportAdapter.setAxisByDelta(
      this.scrollPane.scrollTop,
      delta,
      this.scrollPane.scrollHeight,
      this.scrollPane.clientHeight,
      0,
      this.maxScrollTop(),
    );
  }

  static setAxisByDelta(
    value: number,
    delta: number,
    contentSize: number,
    viewportSize: number,
    min: number,
    max: number,
  ): number {
    const maxOffset = Math.max(0, contentSize - viewportSize);
    if (maxOffset <= 0 || max <= min) {
      return min;
    }
    const currentOffset = MiniCScrollPaneViewportAdapter.normalized(value, min, max) * maxOffset;
    const target = MiniCScrollPaneViewportAdapter.clamp((currentOffset + delta) / maxOffset);
    return min + target * (max - min);
  }

  static normalized(value: number, min: number, max: number): number {
    if (max <= min) {
      return 0;
    }
    return MiniCScrollPaneViewportAdapter.clamp((value - min) / (max - min));
  }

  static clamp(value: number): number {
    return clampNumber(value, 0, 1);
  }

  private maxScrollLeft(): number {
    return Math.max(0, this.scrollPane.scrollWidth - this.scrollPane.clientWidth);
  }

  private maxScrollTop(): number {
    return Math.max(0, this.scrollPane.scrollHeight - this.scrollPane.clientHeight);
  }

  summary(): string {
    return `MiniCScrollPaneViewportAdapter: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCScrollPaneViewportAdapter;
