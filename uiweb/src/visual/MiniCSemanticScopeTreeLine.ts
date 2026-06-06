import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCSemanticScopeTreeLineMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCSemanticScopeTreeLine.java",
  webPath: "uiweb/src/visual/MiniCSemanticScopeTreeLine.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCSemanticScopeTreeLine",
  kind: "record",
  imports: ["java.util.List", "java.util.Objects"],
  fields: [],
  methods: [{ name: "MiniCSemanticScopeTreeLine", signature: "MiniCSemanticScopeTreeLine(String label, int depth, List<String> symbols, boolean active, boolean onActivePath, String arrowDirection)" }],
} as const satisfies JavaMirrorFile;

export type MiniCSemanticScopeArrowDirection = "child-to-parent" | "parent-to-child";

export class MiniCSemanticScopeTreeLine {
  static readonly mirror = miniCSemanticScopeTreeLineMirror;

  readonly symbols: readonly string[];

  constructor(
    readonly label: string,
    readonly depth: number,
    symbols: readonly string[],
    readonly active: boolean,
    readonly onActivePath: boolean,
    readonly arrowDirection: MiniCSemanticScopeArrowDirection,
  ) {
    if (depth < 0) {
      throw new Error("depth must not be negative");
    }
    this.symbols = [...symbols];
  }
}

export default MiniCSemanticScopeTreeLine;
