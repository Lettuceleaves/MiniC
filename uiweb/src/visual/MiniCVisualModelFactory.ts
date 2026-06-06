import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiStageVisualDto } from "../translation/uiapi";
import { MiniCVisualItem } from "./MiniCVisualItem";

export const miniCVisualModelFactoryMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCVisualModelFactory.java",
  webPath: "uiweb/src/visual/MiniCVisualModelFactory.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCVisualModelFactory",
  kind: "class",
  imports: [
    "java.util.ArrayList",
    "java.util.List",
    "minic.uiapi.UiGlobalDataDto",
    "minic.uiapi.UiStageDataDto"
  ],
  fields: [],
  methods: [
    {
      "name": "create",
      "signature": "create(UiStageDataDto stageData,UiGlobalDataDto globalData)"
    },
    {
      "name": "sourceFor",
      "signature": "sourceFor(String stage,UiStageDataDto stageData,UiGlobalDataDto globalData)"
    }
  ],
} as const satisfies JavaMirrorFile;

export interface MiniCStageDataLike {
  readonly stage: string;
  readonly currentItem: string;
  readonly accumulatedOutput: readonly string[];
}

export interface MiniCGlobalDataLike {
  readonly preprocessSummary?: readonly string[];
  readonly tokenSummary?: readonly string[];
  readonly astSummary?: readonly string[];
  readonly semanticSummary?: readonly string[];
  readonly irSummary?: readonly string[];
  readonly assemblySummary?: readonly string[];
}

export class MiniCVisualModelFactory {
  static readonly mirror = miniCVisualModelFactoryMirror;

  create(stageData: MiniCStageDataLike | null | undefined, globalData?: MiniCGlobalDataLike | null): readonly MiniCVisualItem[] {
    if (!stageData) {
      return [new MiniCVisualItem("等待开始观测会话", true)];
    }
    const source = this.sourceFor(stageData.stage, stageData, globalData ?? null);
    const items: MiniCVisualItem[] = [];
    if (stageData.currentItem.trim().length > 0) {
      items.push(new MiniCVisualItem(stageData.currentItem, true));
    }
    items.push(...source.slice(0, 24).map((item) => new MiniCVisualItem(item, false)));
    if (items.length === 0) {
      items.push(new MiniCVisualItem(`${stageData.stage} 暂无输出`, true));
    }
    return items;
  }

  createFromVisual(visual: UiStageVisualDto | null | undefined): readonly MiniCVisualItem[] {
    if (!visual) {
      return [new MiniCVisualItem("暂无可视化数据", true)];
    }
    const items = [
      ...visual.genericItems,
      ...visual.lexerTokens.map((token) => `${token.kind} ${token.text}`.trim()),
      ...visual.irLines.map((line) => line.text),
      ...visual.assemblyLines.map((line) => line.text),
    ];
    return items.length > 0
      ? items.slice(0, 24).map((item, index) => new MiniCVisualItem(item, index === 0))
      : [new MiniCVisualItem(`${visual.stage} 暂无输出`, true)];
  }

  private sourceFor(stage: string, stageData: MiniCStageDataLike, globalData: MiniCGlobalDataLike | null): readonly string[] {
    if (!globalData) {
      return stageData.accumulatedOutput;
    }
    switch (stage) {
      case "preprocess":
        return globalData.preprocessSummary ?? stageData.accumulatedOutput;
      case "lexer":
        return globalData.tokenSummary ?? stageData.accumulatedOutput;
      case "parser":
        return globalData.astSummary ?? stageData.accumulatedOutput;
      case "semantic":
        return globalData.semanticSummary ?? stageData.accumulatedOutput;
      case "ir":
        return globalData.irSummary ?? stageData.accumulatedOutput;
      case "codegen":
        return globalData.assemblySummary ?? stageData.accumulatedOutput;
      default:
        return stageData.accumulatedOutput;
    }
  }
}

export default MiniCVisualModelFactory;
