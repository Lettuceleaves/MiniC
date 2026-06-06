import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCAstGraphEdgeMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCAstGraphEdge.java",
  webPath: "uiweb/src/visual/MiniCAstGraphEdge.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCAstGraphEdge",
  kind: "record",
  imports: ["java.util.Objects"],
  fields: [],
  methods: [{ name: "MiniCAstGraphEdge", signature: "MiniCAstGraphEdge(String fromId, String toId, double fromX, double fromY, double toX, double toY, boolean hot)" }],
} as const satisfies JavaMirrorFile;

export class MiniCAstGraphEdge {
  static readonly mirror = miniCAstGraphEdgeMirror;

  constructor(
    readonly fromId: string,
    readonly toId: string,
    readonly fromX: number,
    readonly fromY: number,
    readonly toX: number,
    readonly toY: number,
    readonly hot: boolean,
  ) {
    if (fromId.length === 0 || toId.length === 0) {
      throw new Error("edge endpoints must not be empty");
    }
  }
}

export default MiniCAstGraphEdge;
