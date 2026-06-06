import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCBottomPanelModelMirror = {
  "javaPath": "src/main/java/minic/uilocal/panel/MiniCBottomPanelModel.java",
  "webPath": "uiweb/src/panel/MiniCBottomPanelModel.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCBottomPanelModel",
  "kind": "record",
  "imports": [
    "java.util.List",
    "java.util.Objects"
  ],
  "fields": [],
  "methods": []
} as const satisfies JavaMirrorFile;

export class MiniCBottomPanelModel {
  static readonly mirror = miniCBottomPanelModelMirror;

  readonly mirror = miniCBottomPanelModelMirror;

  readonly problems: readonly string[];

  readonly output: readonly string[];

  readonly terminal: readonly string[];

  constructor(
    problems: readonly string[],
    output: readonly string[],
    terminal: readonly string[],
  ) {
    this.problems = [...problems];
    this.output = [...output];
    this.terminal = [...terminal];
  }

  summary(): string {
    return `MiniCBottomPanelModel: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCBottomPanelModel;
