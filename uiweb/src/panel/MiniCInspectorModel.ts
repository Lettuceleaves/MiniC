import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCInspectorModelMirror = {
  "javaPath": "src/main/java/minic/uilocal/panel/MiniCInspectorModel.java",
  "webPath": "uiweb/src/panel/MiniCInspectorModel.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCInspectorModel",
  "kind": "record",
  "imports": [
    "java.util.Objects"
  ],
  "fields": [],
  "methods": [
    {
      "name": "MiniCInspectorModel",
      "signature": "MiniCInspectorModel(String currentState, String currentItem, String accumulatedOutput)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCInspectorModel {
  static readonly mirror = miniCInspectorModelMirror;

  readonly mirror = miniCInspectorModelMirror;

  readonly currentState: string;

  readonly currentItem: string;

  readonly accumulatedOutput: string;

  constructor(currentState: string, currentItem: string, accumulatedOutput: string) {
    this.currentState = currentState;
    this.currentItem = currentItem;
    this.accumulatedOutput = accumulatedOutput;
  }

  summary(): string {
    return `MiniCInspectorModel: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCInspectorModel;
