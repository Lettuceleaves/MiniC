import type { JavaMirrorFile } from "../translation/javaMirror";
import type { MiniCAstGraphEdge } from "./MiniCAstGraphEdge";
import type { MiniCAstGraphNode } from "./MiniCAstGraphNode";

export const miniCAstGraphModelMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCAstGraphModel.java",
  webPath: "uiweb/src/visual/MiniCAstGraphModel.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCAstGraphModel",
  kind: "record",
  imports: ["java.util.List"],
  fields: [],
  methods: [{ name: "MiniCAstGraphModel", signature: "MiniCAstGraphModel(List<MiniCAstGraphNode> nodes, List<MiniCAstGraphEdge> edges, double width, double height)" }],
} as const satisfies JavaMirrorFile;

export class MiniCAstGraphModel {
  static readonly mirror = miniCAstGraphModelMirror;

  readonly nodes: readonly MiniCAstGraphNode[];
  readonly edges: readonly MiniCAstGraphEdge[];
  readonly width: number;
  readonly height: number;

  constructor(
    nodes: readonly MiniCAstGraphNode[],
    edges: readonly MiniCAstGraphEdge[],
    width: number,
    height: number,
  ) {
    this.nodes = [...nodes];
    this.edges = [...edges];
    this.width = width;
    this.height = height;
  }
}

export default MiniCAstGraphModel;
