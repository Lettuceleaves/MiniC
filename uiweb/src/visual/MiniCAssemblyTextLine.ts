import type { JavaMirrorFile, UiSourceSpanDto } from "../translation/javaMirror";

export const miniCAssemblyTextLineMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCAssemblyTextLine.java",
  webPath: "uiweb/src/visual/MiniCAssemblyTextLine.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCAssemblyTextLine",
  kind: "record",
  imports: [
    "java.util.Objects",
    "minic.uiapi.UiSourceSpanDto"
  ],
  fields: [],
  methods: [],
} as const satisfies JavaMirrorFile;

export class MiniCAssemblyTextLine {
  static readonly mirror = miniCAssemblyTextLineMirror;

  constructor(
    readonly lineNumber: number,
    readonly text: string,
    readonly section: string,
    readonly label: string,
    readonly kind: string,
    readonly range: UiSourceSpanDto | null,
    readonly active: boolean,
  ) {
    if (lineNumber < 1) {
      throw new Error("lineNumber must be 1-based");
    }
  }
}

export default MiniCAssemblyTextLine;
