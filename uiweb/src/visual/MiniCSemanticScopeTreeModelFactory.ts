import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiSemanticScopeVisualDto, UiStageVisualDto } from "../translation/uiapi";
import { MiniCSemanticScopeTreeLine } from "./MiniCSemanticScopeTreeLine";
import type { MiniCSemanticScopeArrowDirection } from "./MiniCSemanticScopeTreeLine";

export const miniCSemanticScopeTreeModelFactoryMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCSemanticScopeTreeModelFactory.java",
  webPath: "uiweb/src/visual/MiniCSemanticScopeTreeModelFactory.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCSemanticScopeTreeModelFactory",
  kind: "class",
  imports: ["minic.uiapi.UiSemanticScopeVisualDto", "minic.uiapi.UiStageVisualDto", "java.util.List"],
  fields: [],
  methods: [
    { name: "create", signature: "create(UiStageVisualDto visual)" },
    { name: "append", signature: "append(UiSemanticScopeVisualDto node, int depth, boolean childToParent, ArrayList<MiniCSemanticScopeTreeLine> lines)" },
  ],
} as const satisfies JavaMirrorFile;

export class MiniCSemanticScopeTreeModelFactory {
  static readonly mirror = miniCSemanticScopeTreeModelFactoryMirror;

  create(visual: UiStageVisualDto | null | undefined): readonly MiniCSemanticScopeTreeLine[] {
    if (!visual?.semanticRoot) {
      return [];
    }
    const lines: MiniCSemanticScopeTreeLine[] = [];
    this.append(visual.semanticRoot, 0, visual.semanticEdgesPointChildToParent, lines);
    return lines;
  }

  private append(
    node: UiSemanticScopeVisualDto,
    depth: number,
    childToParent: boolean,
    lines: MiniCSemanticScopeTreeLine[],
  ): boolean {
    const currentIndex = lines.length;
    const direction: MiniCSemanticScopeArrowDirection = childToParent ? "child-to-parent" : "parent-to-child";
    lines.push(new MiniCSemanticScopeTreeLine(node.label, depth, node.symbols, node.active, node.active, direction));
    let path = node.active;
    for (const child of node.children) {
      path = this.append(child, depth + 1, childToParent, lines) || path;
    }
    if (path && !lines[currentIndex].onActivePath) {
      const line = lines[currentIndex];
      lines[currentIndex] = new MiniCSemanticScopeTreeLine(
        line.label,
        line.depth,
        line.symbols,
        line.active,
        true,
        line.arrowDirection,
      );
    }
    return path;
  }
}

export default MiniCSemanticScopeTreeModelFactory;
