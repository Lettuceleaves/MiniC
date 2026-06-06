import { MiniCControlTargetType } from "./MiniCControlTargetType";
import type { MiniCViewportAdapter } from "./MiniCViewportAdapter";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { ViewportPoint } from "../translation/uiTypes";
import { requireValue } from "../translation/uiTypes";

export const miniCTextViewportAdapterMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCTextViewportAdapter.java",
  "webPath": "uiweb/src/control/MiniCTextViewportAdapter.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCTextViewportAdapter",
  "kind": "class",
  "imports": [
    "java.util.Objects",
    "javafx.geometry.Point2D",
    "minic.uilocal.MiniCCodeEditor"
  ],
  "fields": [
    {
      "name": "editor",
      "signature": "private final MiniCCodeEditor editor"
    }
  ],
  "methods": [
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
      "name": "centerActiveIfNeeded",
      "signature": "centerActiveIfNeeded()"
    },
    {
      "name": "isActiveFullyVisible",
      "signature": "isActiveFullyVisible()"
    },
    {
      "name": "scrollVertical",
      "signature": "scrollVertical(double delta)"
    },
    {
      "name": "type",
      "signature": "type()"
    },
    {
      "name": "zoomAt",
      "signature": "zoomAt(Point2D localPoint,double delta)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCTextViewportHost {
  zoomFontBy(delta: number): void;
  scrollVerticalBy(delta: number): void;
  isCurrentExecutionFullyVisible(): boolean;
  centerCurrentExecutionIfNeeded(): void;
  centerCurrentExecution(): void;
}

export class MiniCTextViewportAdapter implements MiniCViewportAdapter {
  static readonly mirror = miniCTextViewportAdapterMirror;

  readonly mirror = miniCTextViewportAdapterMirror;

  private readonly editor: MiniCTextViewportHost;

  constructor(editor: MiniCTextViewportHost) {
    this.editor = requireValue(editor, "editor");
  }

  type(): MiniCControlTargetType {
    return MiniCControlTargetType.TEXT;
  }

  canZoom(): boolean {
    return true;
  }

  zoomAt(_localPoint: ViewportPoint, delta: number): void {
    this.editor.zoomFontBy(delta);
  }

  canScrollVertical(): boolean {
    return true;
  }

  scrollVertical(delta: number): void {
    this.editor.scrollVerticalBy(delta);
  }

  canScrollHorizontal(): boolean {
    return false;
  }

  scrollHorizontal(): void {
    return undefined;
  }

  canPan(): boolean {
    return false;
  }

  pan(): void {
    return undefined;
  }

  isActiveFullyVisible(): boolean {
    return this.editor.isCurrentExecutionFullyVisible();
  }

  centerActiveIfNeeded(): void {
    this.editor.centerCurrentExecutionIfNeeded();
  }

  centerActive(): void {
    this.editor.centerCurrentExecution();
  }

  summary(): string {
    return `MiniCTextViewportAdapter: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCTextViewportAdapter;
