import { MiniCControlTargetType } from "./MiniCControlTargetType";
import type { MiniCViewportAdapter } from "./MiniCViewportAdapter";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { ViewportBounds, ViewportPoint } from "../translation/uiTypes";
import { clampNumber, requireValue } from "../translation/uiTypes";

export const miniCGraphViewportAdapterMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCGraphViewportAdapter.java",
  "webPath": "uiweb/src/control/MiniCGraphViewportAdapter.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCGraphViewportAdapter",
  "kind": "class",
  "imports": [
    "java.util.Objects",
    "java.util.Optional",
    "java.util.function.BiConsumer",
    "java.util.function.DoubleConsumer",
    "javafx.geometry.Bounds",
    "javafx.geometry.Point2D",
    "javafx.scene.Node",
    "javafx.scene.Parent",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.shape.Circle",
    "javafx.scene.shape.Rectangle"
  ],
  "fields": [
    {
      "name": "ADAPTER_PROPERTY",
      "signature": "public static final String ADAPTER_PROPERTY="
    },
    {
      "name": "graphContent",
      "signature": "private final Node graphContent"
    },
    {
      "name": "scrollPane",
      "signature": "private final ScrollPane scrollPane"
    },
    {
      "name": "VISIBILITY_EPSILON",
      "signature": "private static final double VISIBILITY_EPSILON="
    },
    {
      "name": "zoomCallback",
      "signature": "private final BiConsumer<Point2D,Double>zoomCallback"
    }
  ],
  "methods": [
    {
      "name": "activeBoundsInScrollContent",
      "signature": "activeBoundsInScrollContent()"
    },
    {
      "name": "activeShape",
      "signature": "activeShape(Node node)"
    },
    {
      "name": "boundsInAncestor",
      "signature": "boundsInAncestor(Node node,Node ancestor)"
    },
    {
      "name": "canPan",
      "signature": "canPan()"
    },
    {
      "name": "canScrollHorizontal",
      "signature": "canScrollHorizontal()"
    },
    {
      "name": "canScrollVertical",
      "signature": "canScrollVertical()"
    },
    {
      "name": "canZoom",
      "signature": "canZoom()"
    },
    {
      "name": "centerActive",
      "signature": "centerActive()"
    },
    {
      "name": "centerAxis",
      "signature": "centerAxis(double activeCenter,double contentMin,double contentSize,double viewportSize,double min,double max,DoubleConsumer setter)"
    },
    {
      "name": "clamp",
      "signature": "clamp(double value)"
    },
    {
      "name": "compensateResidualTranslation",
      "signature": "compensateResidualTranslation(Point2D graphPoint,Point2D targetScenePoint)"
    },
    {
      "name": "isActive",
      "signature": "isActive(Node node)"
    },
    {
      "name": "isActiveFullyVisible",
      "signature": "isActiveFullyVisible()"
    },
    {
      "name": "isTrackedShape",
      "signature": "isTrackedShape(Node node)"
    },
    {
      "name": "normalized",
      "signature": "normalized(double value,double min,double max)"
    },
    {
      "name": "pan",
      "signature": "pan(double deltaX,double deltaY)"
    },
    {
      "name": "residualTranslationTarget",
      "signature": "residualTranslationTarget()"
    },
    {
      "name": "scrollBy",
      "signature": "scrollBy(double delta,Orientation orientation)"
    },
    {
      "name": "scrollContent",
      "signature": "scrollContent()"
    },
    {
      "name": "scrollContentBounds",
      "signature": "scrollContentBounds()"
    },
    {
      "name": "scrollHorizontal",
      "signature": "scrollHorizontal(double delta)"
    },
    {
      "name": "scrollVertical",
      "signature": "scrollVertical(double delta)"
    },
    {
      "name": "setAxisByDelta",
      "signature": "setAxisByDelta(double value,double delta,double contentSize,double viewportSize,double min,double max,DoubleConsumer setter)"
    },
    {
      "name": "setAxisToVisibleMin",
      "signature": "setAxisToVisibleMin(double targetVisibleMin,double contentMin,double contentSize,double viewportSize,double min,double max,DoubleConsumer setter)"
    },
    {
      "name": "toContentPoint",
      "signature": "toContentPoint(Node content,Point2D graphPoint)"
    },
    {
      "name": "type",
      "signature": "type()"
    },
    {
      "name": "visibleMin",
      "signature": "visibleMin(double value,double min,double max,double contentMin,double contentSize,double viewportSize)"
    },
    {
      "name": "zoomAt",
      "signature": "zoomAt(Point2D localPoint,double delta)"
    }
  ]
} as const satisfies JavaMirrorFile;

export type MiniCGraphZoomCallback = (point: ViewportPoint, delta: number) => void;

type Orientation = "horizontal" | "vertical";

export class MiniCGraphViewportAdapter implements MiniCViewportAdapter {
  static readonly mirror = miniCGraphViewportAdapterMirror;

  static readonly ADAPTER_PROPERTY = "minic.uilocal.control.graphViewportAdapter";

  private static readonly VISIBILITY_EPSILON = 0.5;

  readonly mirror = miniCGraphViewportAdapterMirror;

  private readonly scrollPane: HTMLElement;

  private readonly graphContent: HTMLElement;

  private readonly zoomCallback: MiniCGraphZoomCallback;

  constructor(
    scrollPane: HTMLElement,
    graphContent: HTMLElement,
    zoomCallback: MiniCGraphZoomCallback | ((delta: number) => void),
  ) {
    this.scrollPane = requireValue(scrollPane, "scrollPane");
    this.graphContent = requireValue(graphContent, "graphContent");
    const callback = requireValue(zoomCallback, "zoomCallback");
    if (callback.length <= 1) {
      const oneArgumentCallback = callback as (delta: number) => void;
      this.zoomCallback = (_point, delta) => oneArgumentCallback(delta);
    } else {
      this.zoomCallback = callback as MiniCGraphZoomCallback;
    }
  }

  type(): MiniCControlTargetType {
    return MiniCControlTargetType.GRAPH;
  }

  canZoom(): boolean {
    return true;
  }

  zoomAt(localPoint: ViewportPoint, delta: number): void {
    const graphPoint = localPoint ?? { x: 0, y: 0 };
    const before = this.graphContent.getBoundingClientRect();
    const paneBefore = this.scrollPane.getBoundingClientRect();
    const anchorX = before.left + graphPoint.x - paneBefore.left + this.scrollPane.scrollLeft;
    const anchorY = before.top + graphPoint.y - paneBefore.top + this.scrollPane.scrollTop;
    this.zoomCallback(graphPoint, delta);
    const after = this.graphContent.getBoundingClientRect();
    const paneAfter = this.scrollPane.getBoundingClientRect();
    const nextAnchorX = after.left + graphPoint.x - paneAfter.left + this.scrollPane.scrollLeft;
    const nextAnchorY = after.top + graphPoint.y - paneAfter.top + this.scrollPane.scrollTop;
    this.scrollPane.scrollLeft += nextAnchorX - anchorX;
    this.scrollPane.scrollTop += nextAnchorY - anchorY;
  }

  canScrollVertical(): boolean {
    return true;
  }

  scrollVertical(delta: number): void {
    this.scrollBy(delta, "vertical");
  }

  canScrollHorizontal(): boolean {
    return true;
  }

  scrollHorizontal(delta: number): void {
    this.scrollBy(delta, "horizontal");
  }

  canPan(): boolean {
    return true;
  }

  pan(deltaX: number, deltaY: number): void {
    this.scrollHorizontal(deltaX);
    this.scrollVertical(deltaY);
  }

  isActiveFullyVisible(): boolean {
    const active = this.activeBoundsInScrollContent();
    if (!active) {
      return true;
    }
    const viewport = this.viewportBounds();
    if (viewport.width <= 0 || viewport.height <= 0) {
      return true;
    }
    const epsilon = MiniCGraphViewportAdapter.VISIBILITY_EPSILON;
    return active.x >= viewport.x - epsilon
      && active.x + active.width <= viewport.x + viewport.width + epsilon
      && active.y >= viewport.y - epsilon
      && active.y + active.height <= viewport.y + viewport.height + epsilon;
  }

  centerActiveIfNeeded(): void {
    if (!this.isActiveFullyVisible()) {
      this.centerActive();
    }
  }

  centerActive(): void {
    const active = this.activeBoundsInScrollContent();
    if (!active) {
      return;
    }
    this.scrollPane.scrollLeft = MiniCGraphViewportAdapter.centerAxis(
      active.x + active.width / 2,
      this.scrollPane.scrollWidth,
      this.scrollPane.clientWidth,
    );
    this.scrollPane.scrollTop = MiniCGraphViewportAdapter.centerAxis(
      active.y + active.height / 2,
      this.scrollPane.scrollHeight,
      this.scrollPane.clientHeight,
    );
  }

  scrollBy(delta: number, orientation: Orientation): void {
    if (!Number.isFinite(delta)) {
      return;
    }
    if (orientation === "horizontal") {
      this.scrollPane.scrollLeft = MiniCGraphViewportAdapter.setAxisByDelta(
        this.scrollPane.scrollLeft,
        delta,
        this.scrollPane.scrollWidth,
        this.scrollPane.clientWidth,
        0,
        this.maxScrollLeft(),
      );
      return;
    }
    this.scrollPane.scrollTop = MiniCGraphViewportAdapter.setAxisByDelta(
      this.scrollPane.scrollTop,
      delta,
      this.scrollPane.scrollHeight,
      this.scrollPane.clientHeight,
      0,
      this.maxScrollTop(),
    );
  }

  activeBoundsInScrollContent(): ViewportBounds | undefined {
    const active = this.activeShape(this.graphContent);
    if (!active) {
      return undefined;
    }
    return this.boundsInAncestor(active, this.scrollPane);
  }

  activeShape(node: Element): Element | undefined {
    if (this.isTrackedShape(node) && this.isActive(node)) {
      return node;
    }
    for (const child of node.children) {
      const active = this.activeShape(child);
      if (active) {
        return active;
      }
    }
    return undefined;
  }

  isTrackedShape(node: Element): boolean {
    const tagName = node.tagName.toLowerCase();
    return tagName === "circle"
      || tagName === "rect"
      || node instanceof HTMLElement
      || node instanceof SVGElement;
  }

  isActive(node: Element): boolean {
    return node.classList.contains("active")
      || node.classList.contains("active-scope-mask")
      || node.classList.contains("current")
      || node.classList.contains("debug-active")
      || node.getAttribute("data-active") === "true";
  }

  boundsInAncestor(node: Element, ancestor: HTMLElement): ViewportBounds {
    const nodeBounds = node.getBoundingClientRect();
    const ancestorBounds = ancestor.getBoundingClientRect();
    return {
      x: nodeBounds.left - ancestorBounds.left + ancestor.scrollLeft,
      y: nodeBounds.top - ancestorBounds.top + ancestor.scrollTop,
      width: nodeBounds.width,
      height: nodeBounds.height,
    };
  }

  scrollContent(): HTMLElement {
    return this.scrollPane.firstElementChild instanceof HTMLElement
      ? this.scrollPane.firstElementChild
      : this.graphContent;
  }

  scrollContentBounds(): ViewportBounds {
    const content = this.scrollContent();
    return {
      x: 0,
      y: 0,
      width: content.scrollWidth || content.getBoundingClientRect().width,
      height: content.scrollHeight || content.getBoundingClientRect().height,
    };
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
    const currentOffset = MiniCGraphViewportAdapter.normalized(value, min, max) * maxOffset;
    const target = MiniCGraphViewportAdapter.clamp((currentOffset + delta) / maxOffset);
    return min + target * (max - min);
  }

  static centerAxis(activeCenter: number, contentSize: number, viewportSize: number): number {
    const maxOffset = Math.max(0, contentSize - viewportSize);
    if (maxOffset <= 0) {
      return 0;
    }
    return clampNumber(activeCenter - viewportSize / 2, 0, maxOffset);
  }

  static setAxisToVisibleMin(
    targetVisibleMin: number,
    contentMin: number,
    contentSize: number,
    viewportSize: number,
    min: number,
    max: number,
  ): number {
    const maxOffset = Math.max(0, contentSize - viewportSize);
    if (maxOffset <= 0 || max <= min) {
      return min;
    }
    const offset = clampNumber(targetVisibleMin - contentMin, 0, maxOffset);
    return min + offset / maxOffset * (max - min);
  }

  static visibleMin(
    value: number,
    min: number,
    max: number,
    contentMin: number,
    contentSize: number,
    viewportSize: number,
  ): number {
    const maxOffset = Math.max(0, contentSize - viewportSize);
    return contentMin + MiniCGraphViewportAdapter.normalized(value, min, max) * maxOffset;
  }

  static normalized(value: number, min: number, max: number): number {
    if (max <= min) {
      return 0;
    }
    return MiniCGraphViewportAdapter.clamp((value - min) / (max - min));
  }

  static clamp(value: number): number {
    return clampNumber(value, 0, 1);
  }

  private viewportBounds(): ViewportBounds {
    return {
      x: this.scrollPane.scrollLeft,
      y: this.scrollPane.scrollTop,
      width: this.scrollPane.clientWidth,
      height: this.scrollPane.clientHeight,
    };
  }

  private maxScrollLeft(): number {
    return Math.max(0, this.scrollPane.scrollWidth - this.scrollPane.clientWidth);
  }

  private maxScrollTop(): number {
    return Math.max(0, this.scrollPane.scrollHeight - this.scrollPane.clientHeight);
  }

  summary(): string {
    return `MiniCGraphViewportAdapter: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCGraphViewportAdapter;
