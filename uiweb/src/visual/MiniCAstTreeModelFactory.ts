import type { JavaMirrorFile, UiAstNodeVisualDto, UiStageVisualDto } from "../translation/javaMirror";
import { MiniCAstTreeLine } from "./MiniCAstTreeLine";

export const miniCAstTreeModelFactoryMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCAstTreeModelFactory.java",
  webPath: "uiweb/src/visual/MiniCAstTreeModelFactory.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCAstTreeModelFactory",
  kind: "class",
  imports: ["minic.uiapi.UiAstNodeVisualDto", "minic.uiapi.UiStageVisualDto", "java.util.List"],
  fields: [],
  methods: [{ name: "create", signature: "create(UiStageVisualDto visual)" }],
} as const satisfies JavaMirrorFile;

export class MiniCAstTreeModelFactory {
  static readonly mirror = miniCAstTreeModelFactoryMirror;

  create(visual: UiStageVisualDto | null | undefined): readonly MiniCAstTreeLine[] {
    if (!visual?.astRoot) {
      return [];
    }
    const lines: MiniCAstTreeLine[] = [];
    this.append(visual.astRoot, 0, lines);
    return lines;
  }

  private append(node: UiAstNodeVisualDto, depth: number, lines: MiniCAstTreeLine[]): void {
    lines.push(new MiniCAstTreeLine(node.label, depth, node.active, node.range));
    for (const child of node.children) {
      this.append(child, depth + 1, lines);
    }
  }
}

export default MiniCAstTreeModelFactory;
