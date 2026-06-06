import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiCurrentStateDto, UiGlobalDataDto, UiStageDataDto } from "../translation/uiapi";
import { MiniCInspectorModel } from "./MiniCInspectorModel";

export const miniCInspectorModelFactoryMirror = {
  "javaPath": "src/main/java/minic/uilocal/panel/MiniCInspectorModelFactory.java",
  "webPath": "uiweb/src/panel/MiniCInspectorModelFactory.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCInspectorModelFactory",
  "kind": "class",
  "imports": [
    "minic.uiapi.UiCurrentStateDto",
    "minic.uiapi.UiGlobalDataDto",
    "minic.uiapi.UiStageDataDto"
  ],
  "fields": [],
  "methods": [
    {
      "name": "create",
      "signature": "create(UiCurrentStateDto state, UiStageDataDto stageData, UiGlobalDataDto globalData)"
    },
    {
      "name": "currentState",
      "signature": "currentState(UiCurrentStateDto state)"
    },
    {
      "name": "currentItem",
      "signature": "currentItem(UiStageDataDto stageData)"
    },
    {
      "name": "accumulatedOutput",
      "signature": "accumulatedOutput(UiGlobalDataDto globalData)"
    },
    {
      "name": "stageName",
      "signature": "stageName(String stage)"
    },
    {
      "name": "playbackMode",
      "signature": "playbackMode(String mode)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCInspectorModelFactory {
  static readonly mirror = miniCInspectorModelFactoryMirror;

  readonly mirror = miniCInspectorModelFactoryMirror;

  create(
    state: UiCurrentStateDto | null,
    stageData: UiStageDataDto | null,
    globalData: UiGlobalDataDto | null,
  ): MiniCInspectorModel {
    return new MiniCInspectorModel(
      this.currentState(state),
      this.currentItem(stageData),
      this.accumulatedOutput(globalData),
    );
  }

  currentState(state: UiCurrentStateDto | null): string {
    if (state === null) {
      return "阶段: 等待中\n全局步: 0\n阶段步: 0\n帧间隔: 0ms\n诊断: 0";
    }
    return [
      `阶段: ${stageName(state.currentStage)}`,
      `全局步: ${state.globalStepIndex}`,
      `阶段步: ${state.stageStepIndex}`,
      `播放: ${playbackMode(state.playbackMode)}`,
      `帧间隔: ${state.frameIntervalMillis}ms`,
      `诊断: ${state.diagnostics.length}`,
    ].join("\n");
  }

  currentItem(stageData: UiStageDataDto | null): string {
    if (stageData === null) {
      return "等待开始观测会话。";
    }
    return stageData.currentItem.trim() === ""
      ? `${stageName(stageData.stage)} 暂无当前项`
      : stageData.currentItem;
  }

  accumulatedOutput(globalData: UiGlobalDataDto | null): string {
    if (globalData === null) {
      return "预编译: 0\ntoken: 0\nAST: 0\n语义: 0\nIR: 0\n汇编: 0\n产物: 0";
    }
    return [
      `预编译: ${globalData.preprocessSummary.length}`,
      `token: ${globalData.tokenSummary.length}`,
      `AST: ${globalData.astSummary.length}`,
      `语义: ${globalData.semanticSummary.length}`,
      `IR: ${globalData.irSummary.length}`,
      `汇编: ${globalData.assemblySummary.length}`,
      `产物: ${globalData.artifactSummary.length}`,
    ].join("\n");
  }

  summary(): string {
    return `MiniCInspectorModelFactory: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export function stageName(stage: string): string {
  switch (stage) {
    case "source":
      return "源码";
    case "preprocess":
      return "预编译";
    case "lexer":
      return "词法分析";
    case "parser":
      return "语法分析";
    case "semantic":
      return "语义分析";
    case "ir":
      return "IR 降级";
    case "codegen":
      return "代码生成";
    case "toolchain":
      return "工具链";
    case "execution":
      return "执行";
    default:
      return stage;
  }
}

export function playbackMode(mode: string): string {
  switch (mode) {
    case "PLAYING":
      return "播放中";
    case "FAST_PLAYING":
      return "快速播放";
    case "PAUSED":
      return "暂停";
    default:
      return mode;
  }
}

export default MiniCInspectorModelFactory;
