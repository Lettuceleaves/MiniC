import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCLexerOverlaySegmentMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCLexerOverlaySegment.java",
  webPath: "uiweb/src/visual/MiniCLexerOverlaySegment.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCLexerOverlaySegment",
  kind: "record",
  imports: [
    "java.util.Objects"
  ],
  fields: [],
  methods: [],
} as const satisfies JavaMirrorFile;

export class MiniCLexerOverlaySegment {
  static readonly mirror = miniCLexerOverlaySegmentMirror;

  constructor(readonly text: string, readonly active: boolean) {}
}

export default MiniCLexerOverlaySegment;
