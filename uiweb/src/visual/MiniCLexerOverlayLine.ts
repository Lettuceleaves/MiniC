import type { JavaMirrorFile } from "../translation/javaMirror";
import type { MiniCLexerOverlaySegment } from "./MiniCLexerOverlaySegment";

export const miniCLexerOverlayLineMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCLexerOverlayLine.java",
  webPath: "uiweb/src/visual/MiniCLexerOverlayLine.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCLexerOverlayLine",
  kind: "record",
  imports: [
    "java.util.List"
  ],
  fields: [],
  methods: [],
} as const satisfies JavaMirrorFile;

export class MiniCLexerOverlayLine {
  static readonly mirror = miniCLexerOverlayLineMirror;

  readonly segments: readonly MiniCLexerOverlaySegment[];

  constructor(readonly lineNumber: number, segments: readonly MiniCLexerOverlaySegment[]) {
    if (lineNumber < 1) {
      throw new Error("lineNumber must be 1-based");
    }
    this.segments = [...segments];
  }
}

export default MiniCLexerOverlayLine;
