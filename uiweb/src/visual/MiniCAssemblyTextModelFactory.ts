import type { JavaMirrorFile, UiStageVisualDto } from "../translation/javaMirror";
import { MiniCAssemblyTextLine } from "./MiniCAssemblyTextLine";

export const miniCAssemblyTextModelFactoryMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCAssemblyTextModelFactory.java",
  webPath: "uiweb/src/visual/MiniCAssemblyTextModelFactory.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCAssemblyTextModelFactory",
  kind: "class",
  imports: ["minic.uiapi.UiStageVisualDto", "java.util.List"],
  fields: [],
  methods: [{ name: "create", signature: "create(UiStageVisualDto visual)" }],
} as const satisfies JavaMirrorFile;

export class MiniCAssemblyTextModelFactory {
  static readonly mirror = miniCAssemblyTextModelFactoryMirror;

  create(visual: UiStageVisualDto | null | undefined): readonly MiniCAssemblyTextLine[] {
    return (visual?.assemblyLines ?? []).map((line) => new MiniCAssemblyTextLine(
      line.lineNumber,
      line.text,
      line.section,
      line.label,
      line.kind,
      line.range,
      line.active,
    ));
  }
}

export default MiniCAssemblyTextModelFactory;
