import type { JavaMirrorFile, UiSourceSpanDto } from "../translation/javaMirror";

export const miniCAstTreeLineMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCAstTreeLine.java",
  webPath: "uiweb/src/visual/MiniCAstTreeLine.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCAstTreeLine",
  kind: "record",
  imports: ["minic.uiapi.UiSourceSpanDto"],
  fields: [],
  methods: [{ name: "MiniCAstTreeLine", signature: "MiniCAstTreeLine(String label, int depth, boolean active, UiSourceSpanDto range)" }],
} as const satisfies JavaMirrorFile;

export class MiniCAstTreeLine {
  static readonly mirror = miniCAstTreeLineMirror;

  constructor(
    readonly label: string,
    readonly depth: number,
    readonly active: boolean,
    readonly range: UiSourceSpanDto | null,
  ) {
    if (depth < 0) {
      throw new Error("depth must not be negative");
    }
  }
}

export default MiniCAstTreeLine;
