import type { JavaMirrorFile } from "../translation/javaMirror";
import { requireValue } from "../translation/uiTypes";

export const miniCSourceLineMirror = {
  "javaPath": "src/main/java/minic/uilocal/source/MiniCSourceLine.java",
  "webPath": "uiweb/src/source/MiniCSourceLine.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCSourceLine",
  "kind": "record",
  "imports": [
    "java.util.Objects"
  ],
  "fields": [],
  "methods": [
    {
      "name": "MiniCSourceLine",
      "signature": "MiniCSourceLine(int lineNumber, String text, boolean focused)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCSourceLine {
  static readonly mirror = miniCSourceLineMirror;

  readonly mirror = miniCSourceLineMirror;

  readonly lineNumber: number;

  readonly text: string;

  readonly focused: boolean;

  constructor(lineNumber: number, text: string, focused: boolean) {
    if (!Number.isInteger(lineNumber) || lineNumber <= 0) {
      throw new RangeError("lineNumber must be positive");
    }
    this.lineNumber = lineNumber;
    this.text = requireValue(text, "text");
    this.focused = focused;
  }

  summary(): string {
    return `MiniCSourceLine: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCSourceLine;
