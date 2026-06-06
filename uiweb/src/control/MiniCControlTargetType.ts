import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCControlTargetTypeMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCControlTargetType.java",
  "webPath": "uiweb/src/control/MiniCControlTargetType.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCControlTargetType",
  "kind": "enum",
  "imports": [],
  "fields": [],
  "methods": []
} as const satisfies JavaMirrorFile;

export enum MiniCControlTargetType {
  TEXT = "TEXT",
  GRAPH = "GRAPH",
  SCROLL = "SCROLL",
  STAGE = "STAGE",
  NONE = "NONE",
}

export namespace MiniCControlTargetType {
  export const mirror = miniCControlTargetTypeMirror;

  export function summary(): string {
    return `MiniCControlTargetType: ${mirror.methods.length} methods, ${mirror.fields.length} fields`;
  }
}

export default MiniCControlTargetType;
