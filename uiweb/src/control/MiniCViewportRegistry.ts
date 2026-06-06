import { MiniCControlTargetType } from "./MiniCControlTargetType";
import type { MiniCViewportAdapter } from "./MiniCViewportAdapter";
import type { JavaMirrorFile } from "../translation/javaMirror";
import { requireValue } from "../translation/uiTypes";

export const miniCViewportRegistryMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCViewportRegistry.java",
  "webPath": "uiweb/src/control/MiniCViewportRegistry.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCViewportRegistry",
  "kind": "class",
  "imports": [
    "java.util.Objects",
    "java.util.Optional"
  ],
  "fields": [
    {
      "name": "businessActiveTarget",
      "signature": "private MiniCViewportAdapter businessActiveTarget"
    },
    {
      "name": "hoverTarget",
      "signature": "private MiniCViewportAdapter hoverTarget"
    },
    {
      "name": "pinnedTarget",
      "signature": "private MiniCViewportAdapter pinnedTarget"
    }
  ],
  "methods": [
    {
      "name": "businessActive",
      "signature": "businessActive(MiniCViewportAdapter target)"
    },
    {
      "name": "clearBusinessActive",
      "signature": "clearBusinessActive()"
    },
    {
      "name": "clearBusinessActive",
      "signature": "clearBusinessActive(MiniCViewportAdapter target)"
    },
    {
      "name": "clearHover",
      "signature": "clearHover(MiniCViewportAdapter target)"
    },
    {
      "name": "clearPinned",
      "signature": "clearPinned()"
    },
    {
      "name": "clearPinned",
      "signature": "clearPinned(MiniCViewportAdapter target)"
    },
    {
      "name": "currentTarget",
      "signature": "currentTarget()"
    },
    {
      "name": "hover",
      "signature": "hover(MiniCViewportAdapter target)"
    },
    {
      "name": "isControllable",
      "signature": "isControllable(MiniCViewportAdapter target)"
    },
    {
      "name": "pin",
      "signature": "pin(MiniCViewportAdapter target)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCViewportRegistry {
  static readonly mirror = miniCViewportRegistryMirror;

  readonly mirror = miniCViewportRegistryMirror;

  private hoverTarget: MiniCViewportAdapter | null = null;

  private pinnedTarget: MiniCViewportAdapter | null = null;

  private businessActiveTarget: MiniCViewportAdapter | null = null;

  hover(target: MiniCViewportAdapter): void {
    if (MiniCViewportRegistry.isControllable(target)) {
      this.hoverTarget = target;
    }
  }

  clearHover(target: MiniCViewportAdapter): void {
    if (this.hoverTarget === target) {
      this.hoverTarget = null;
    }
  }

  pin(target: MiniCViewportAdapter): void {
    if (MiniCViewportRegistry.isControllable(target)) {
      this.pinnedTarget = target;
    }
  }

  clearPinned(target?: MiniCViewportAdapter): void {
    if (target === undefined || this.pinnedTarget === target) {
      this.pinnedTarget = null;
    }
  }

  businessActive(target: MiniCViewportAdapter): void {
    if (MiniCViewportRegistry.isControllable(target)) {
      this.businessActiveTarget = target;
    }
  }

  clearBusinessActive(target?: MiniCViewportAdapter): void {
    if (target === undefined || this.businessActiveTarget === target) {
      this.businessActiveTarget = null;
    }
  }

  currentTarget(): MiniCViewportAdapter | undefined {
    return this.hoverTarget ?? this.pinnedTarget ?? this.businessActiveTarget ?? undefined;
  }

  static isControllable(target: MiniCViewportAdapter): boolean {
    return requireValue(target, "target").type() !== MiniCControlTargetType.NONE;
  }

  isControllable(target: MiniCViewportAdapter): boolean {
    return MiniCViewportRegistry.isControllable(target);
  }

  summary(): string {
    return `MiniCViewportRegistry: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCViewportRegistry;
