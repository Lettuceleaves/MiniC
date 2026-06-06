import type { MiniCViewportAdapter } from "./MiniCViewportAdapter";
import type { JavaMirrorFile } from "../translation/javaMirror";
import { requireValue } from "../translation/uiTypes";

export const miniCActiveTrackingServiceMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCActiveTrackingService.java",
  "webPath": "uiweb/src/control/MiniCActiveTrackingService.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCActiveTrackingService",
  "kind": "class",
  "imports": [
    "java.util.Collection",
    "java.util.Objects",
    "java.util.function.Supplier"
  ],
  "fields": [
    {
      "name": "activeAdapters",
      "signature": "private final Supplier<? extends Collection<? extends MiniCViewportAdapter>> activeAdapters;"
    }
  ],
  "methods": [
    {
      "name": "trackActiveViewports",
      "signature": "trackActiveViewports()"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCActiveTrackingService {
  static readonly mirror = miniCActiveTrackingServiceMirror;

  readonly mirror = miniCActiveTrackingServiceMirror;

  private readonly activeAdapters: () => Iterable<MiniCViewportAdapter | null | undefined> | null | undefined;

  constructor(activeAdapters: () => Iterable<MiniCViewportAdapter | null | undefined> | null | undefined) {
    this.activeAdapters = requireValue(activeAdapters, "activeAdapters");
  }

  trackActiveViewports(): void {
    const adapters = this.activeAdapters();
    if (!adapters) {
      return;
    }
    for (const adapter of adapters) {
      adapter?.centerActiveIfNeeded();
    }
  }

  summary(): string {
    return `MiniCActiveTrackingService: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCActiveTrackingService;
