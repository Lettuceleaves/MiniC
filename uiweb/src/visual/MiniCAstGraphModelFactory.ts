import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiAstNodeVisualDto, UiStageVisualDto } from "../translation/uiapi";
import { MiniCAstGraphEdge } from "./MiniCAstGraphEdge";
import { MiniCAstGraphModel } from "./MiniCAstGraphModel";
import { MiniCAstGraphNode } from "./MiniCAstGraphNode";

export const miniCAstGraphModelFactoryMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCAstGraphModelFactory.java",
  webPath: "uiweb/src/visual/MiniCAstGraphModelFactory.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCAstGraphModelFactory",
  kind: "class",
  imports: [
    "java.util.ArrayList",
    "java.util.HashMap",
    "java.util.List",
    "java.util.Map",
    "minic.uiapi.UiAstNodeVisualDto",
    "minic.uiapi.UiStageVisualDto"
  ],
  fields: [
    {
      "name": "LEFT_PAD",
      "signature": "private static final double LEFT_PAD="
    },
    {
      "name": "NODE_RADIUS",
      "signature": "private static final double NODE_RADIUS="
    },
    {
      "name": "TOP_PAD",
      "signature": "private static final double TOP_PAD="
    },
    {
      "name": "X_GAP",
      "signature": "private static final double X_GAP="
    },
    {
      "name": "Y_GAP",
      "signature": "private static final double Y_GAP="
    }
  ],
  methods: [
    {
      "name": "assignPositions",
      "signature": "assignPositions(UiAstNodeVisualDto node,int depth,int[] nextLeaf,ArrayList<PositionedNode>positioned)"
    },
    {
      "name": "create",
      "signature": "create(UiAstNodeVisualDto root)"
    },
    {
      "name": "create",
      "signature": "create(UiStageVisualDto visual)"
    },
    {
      "name": "PositionedNode",
      "signature": "PositionedNode(UiAstNodeVisualDto node,int depth,double x,double y)"
    }
  ],
} as const satisfies JavaMirrorFile;

const NODE_RADIUS = 28;
const X_GAP = 128;
const Y_GAP = 96;
const LEFT_PAD = 64;
const TOP_PAD = 58;

interface PositionedNode {
  readonly node: UiAstNodeVisualDto;
  readonly depth: number;
  readonly x: number;
  readonly y: number;
}

export class MiniCAstGraphModelFactory {
  static readonly mirror = miniCAstGraphModelFactoryMirror;

  create(visualOrRoot: UiStageVisualDto | UiAstNodeVisualDto | null | undefined): MiniCAstGraphModel {
    const root = this.rootOf(visualOrRoot);
    if (!root) {
      return new MiniCAstGraphModel([], [], 360, 240);
    }

    const positioned: PositionedNode[] = [];
    const nextLeaf = { value: 0 };
    const leafCount = this.assignPositions(root, 0, nextLeaf, positioned);
    const positionedById = new Map(positioned.map((node) => [node.node.id, node]));
    const nodes: MiniCAstGraphNode[] = [];
    const edges: MiniCAstGraphEdge[] = [];

    for (const positionedNode of positioned) {
      const node = positionedNode.node;
      nodes.push(new MiniCAstGraphNode(
        node.id,
        node.label,
        positionedNode.depth,
        positionedNode.x,
        positionedNode.y,
        node.active,
        node.children.length === 0,
        node.id === "ast-root",
      ));

      for (const child of node.children) {
        const childPosition = positionedById.get(child.id);
        if (!childPosition) {
          throw new Error(`missing AST graph position for node ${child.id}`);
        }
        edges.push(new MiniCAstGraphEdge(
          node.id,
          child.id,
          positionedNode.x,
          positionedNode.y + NODE_RADIUS,
          childPosition.x,
          childPosition.y - NODE_RADIUS,
          false,
        ));
      }
    }

    const maxDepth = positioned.reduce((max, node) => Math.max(max, node.depth), 0);
    const width = Math.max(520, LEFT_PAD * 2 + Math.max(leafCount, 1) * X_GAP);
    const height = Math.max(300, TOP_PAD * 2 + (maxDepth + 1) * Y_GAP);
    return new MiniCAstGraphModel(nodes, edges, width, height);
  }

  private rootOf(value: UiStageVisualDto | UiAstNodeVisualDto | null | undefined): UiAstNodeVisualDto | null {
    if (!value) {
      return null;
    }
    if ("astRoot" in value) {
      return value.astRoot;
    }
    return value;
  }

  private assignPositions(
    node: UiAstNodeVisualDto,
    depth: number,
    nextLeaf: { value: number },
    positioned: PositionedNode[],
  ): number {
    if (node.children.length === 0) {
      const x = LEFT_PAD + nextLeaf.value * X_GAP;
      const y = TOP_PAD + depth * Y_GAP;
      nextLeaf.value += 1;
      positioned.push({ node, depth, x, y });
      return 1;
    }

    const before = nextLeaf.value;
    let leaves = 0;
    for (const child of node.children) {
      leaves += this.assignPositions(child, depth + 1, nextLeaf, positioned);
    }
    const left = LEFT_PAD + before * X_GAP;
    const right = LEFT_PAD + (nextLeaf.value - 1) * X_GAP;
    positioned.push({ node, depth, x: (left + right) / 2, y: TOP_PAD + depth * Y_GAP });
    return leaves;
  }
}

export default MiniCAstGraphModelFactory;
