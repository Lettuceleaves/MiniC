import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCAstGraphNodeMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCAstGraphNode.java",
  webPath: "uiweb/src/visual/MiniCAstGraphNode.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCAstGraphNode",
  kind: "record",
  imports: [
    "java.util.Objects"
  ],
  fields: [],
  methods: [],
} as const satisfies JavaMirrorFile;

export class MiniCAstGraphNode {
  static readonly mirror = miniCAstGraphNodeMirror;

  constructor(
    readonly id: string,
    readonly label: string,
    readonly depth: number,
    readonly x: number,
    readonly y: number,
    readonly active: boolean,
    readonly leaf: boolean,
    readonly root: boolean,
  ) {
    if (id.length === 0) {
      throw new Error("id must not be empty");
    }
    if (depth < 0) {
      throw new Error("depth must not be negative");
    }
  }
}

export default MiniCAstGraphNode;
