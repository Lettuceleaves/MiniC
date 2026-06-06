import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCVisualItemMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCVisualItem.java",
  webPath: "uiweb/src/visual/MiniCVisualItem.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCVisualItem",
  kind: "record",
  imports: ["java.util.Objects"],
  fields: [],
  methods: [{ name: "MiniCVisualItem", signature: "MiniCVisualItem(String label, boolean hot)" }],
} as const satisfies JavaMirrorFile;

export class MiniCVisualItem {
  static readonly mirror = miniCVisualItemMirror;

  constructor(readonly label: string, readonly hot: boolean) {}
}

export default MiniCVisualItem;
