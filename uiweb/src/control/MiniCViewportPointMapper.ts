import type { JavaMirrorFile } from "../translation/javaMirror";
import type { ViewportPoint } from "../translation/uiTypes";
import { clampNumber, requireValue } from "../translation/uiTypes";

export const miniCViewportPointMapperMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCViewportPointMapper.java",
  "webPath": "uiweb/src/control/MiniCViewportPointMapper.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCViewportPointMapper",
  "kind": "class",
  "imports": [
    "javafx.geometry.Bounds",
    "javafx.geometry.Point2D",
    "javafx.scene.Node",
    "javafx.scene.control.ScrollPane",
    "java.util.Objects"
  ],
  "fields": [],
  "methods": [
    {
      "name": "toViewportPoint",
      "signature": "toViewportPoint(Node localNode, double localX, double localY, ScrollPane scrollPane)"
    },
    {
      "name": "visibleMin",
      "signature": "visibleMin(double value, double min, double max, double contentMin, double contentSize, double viewportSize)"
    },
    {
      "name": "normalized",
      "signature": "normalized(double value, double min, double max)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCViewportPointMapper {
  static readonly mirror = miniCViewportPointMapperMirror;

  readonly mirror = miniCViewportPointMapperMirror;

  static toViewportPoint(
    localNode: HTMLElement,
    localX: number,
    localY: number,
    scrollPane: HTMLElement,
  ): ViewportPoint {
    const node = requireValue(localNode, "localNode");
    const pane = requireValue(scrollPane, "scrollPane");
    const nodeBounds = node.getBoundingClientRect();
    const paneBounds = pane.getBoundingClientRect();
    return {
      x: nodeBounds.left + localX - paneBounds.left + pane.scrollLeft,
      y: nodeBounds.top + localY - paneBounds.top + pane.scrollTop,
    };
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
    return contentMin + MiniCViewportPointMapper.normalized(value, min, max) * maxOffset;
  }

  static normalized(value: number, min: number, max: number): number {
    if (max <= min) {
      return 0;
    }
    return clampNumber((value - min) / (max - min), 0, 1);
  }

  toViewportPoint(localNode: HTMLElement, localX: number, localY: number, scrollPane: HTMLElement): ViewportPoint {
    return MiniCViewportPointMapper.toViewportPoint(localNode, localX, localY, scrollPane);
  }

  summary(): string {
    return `MiniCViewportPointMapper: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCViewportPointMapper;
