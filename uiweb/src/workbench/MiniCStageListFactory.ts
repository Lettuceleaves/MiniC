import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiCurrentStateDto, UiGlobalDataDto, UiStageDataDto } from "../translation/uiapi";
import { createMiniCStageView, type MiniCStageState, type MiniCStageViewModel } from "./MiniCStageView";

export const miniCStageListFactoryMirror = {
  "javaPath": "src/main/java/minic/uilocal/workbench/MiniCStageListFactory.java",
  "webPath": "uiweb/src/workbench/MiniCStageListFactory.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCStageListFactory",
  "kind": "class",
  "imports": [
    "java.util.ArrayList",
    "java.util.List",
    "minic.uiapi.UiCurrentStateDto",
    "minic.uiapi.UiGlobalDataDto",
    "minic.uiapi.UiStageDataDto"
  ],
  "fields": [
    {
      "name": "STAGES",
      "signature": "private static final List<StageInfo>STAGES="
    }
  ],
  "methods": [
    {
      "name": "create",
      "signature": "create(UiCurrentStateDto currentState,UiStageDataDto currentStageData,UiGlobalDataDto globalData)"
    },
    {
      "name": "detail",
      "signature": "detail(String stage,boolean active,UiStageDataDto currentStageData,UiGlobalDataDto globalData)"
    },
    {
      "name": "hasErrors",
      "signature": "hasErrors(UiGlobalDataDto globalData)"
    },
    {
      "name": "progress",
      "signature": "progress(String stage,boolean active,boolean done,UiCurrentStateDto currentState,UiStageDataDto currentStageData)"
    },
    {
      "name": "stageIndex",
      "signature": "stageIndex(String id)"
    },
    {
      "name": "StageInfo",
      "signature": "StageInfo(String id,String title)"
    },
    {
      "name": "state",
      "signature": "state(boolean active,boolean done,UiGlobalDataDto globalData)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCStageListFactory {
  static readonly mirror = miniCStageListFactoryMirror;

  readonly mirror = miniCStageListFactoryMirror;

  create(
    currentState: UiCurrentStateDto | null,
    currentStageData: UiStageDataDto | null,
    globalData: UiGlobalDataDto | null,
  ): readonly MiniCStageViewModel[] {
    const currentStage = currentState === null ? "source" : currentState.currentStage;
    const currentIndex = stageIndex(currentStage);
    return STAGES.map((stage, index) => {
      const active = stage.id === currentStage;
      const done =
        currentState !== null &&
        (index < currentIndex || (active && currentStageData !== null && currentStageData.completed));
      return createMiniCStageView(
        stage.id,
        stage.title,
        this.state(active, done, globalData),
        this.detail(stage.id, active, currentStageData, globalData),
        this.progress(stage.id, active, done, currentState, currentStageData),
      );
    });
  }

  state(active: boolean, done: boolean, globalData: UiGlobalDataDto | null): MiniCStageState {
    if (this.hasErrors(globalData)) {
      return active ? "error" : done ? "done" : "queued";
    }
    if (active) {
      return "running";
    }
    if (done) {
      return "done";
    }
    return "queued";
  }

  hasErrors(globalData: UiGlobalDataDto | null): boolean {
    return globalData !== null && globalData.diagnostics.some((diagnostic) => diagnostic.severity === "ERROR");
  }

  progress(
    stage: string,
    active: boolean,
    done: boolean,
    currentState: UiCurrentStateDto | null,
    currentStageData: UiStageDataDto | null,
  ): number {
    if (done) {
      return 100;
    }
    if (stage === "source" && active && currentState !== null) {
      return 100;
    }
    if (!active || currentStageData === null || currentStageData.totalSteps <= 0) {
      return 0;
    }
    return Math.max(
      0,
      Math.min(100, Math.trunc((currentStageData.completedSteps * 100) / currentStageData.totalSteps)),
    );
  }

  detail(
    stage: string,
    active: boolean,
    currentStageData: UiStageDataDto | null,
    globalData: UiGlobalDataDto | null,
  ): string {
    if (active && currentStageData !== null) {
      return `${currentStageData.completedSteps} / ${currentStageData.totalSteps} · 当前阶段`;
    }
    if (globalData === null) {
      return "等待会话启动";
    }
    switch (stage) {
      case "source":
        return "源码已加载";
      case "preprocess":
        return globalData.preprocessSummary.length === 0 ? "等待预编译" : "预处理产物已生成";
      case "lexer":
        return `${globalData.tokenSummary.length} 个 token`;
      case "parser":
        return `${globalData.astSummary.length} 个 AST 项`;
      case "semantic":
        return `${globalData.semanticSummary.length} 个语义项`;
      case "ir":
        return `${globalData.irSummary.length} 个 IR 项`;
      case "codegen":
        return `${globalData.assemblySummary.length} 行汇编`;
      case "toolchain":
        return globalData.artifactSummary.length === 0 ? "尚未生成产物" : "产物已就绪";
      case "execution":
        return globalData.executionOutputSummary.length === 0 ? "等待输入" : "运行完成";
      default:
        return "排队中";
    }
  }

  summary(): string {
    return `MiniCStageListFactory: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

interface StageInfo {
  readonly id: string;
  readonly title: string;
}

const STAGES: readonly StageInfo[] = [
  { id: "source", title: "源码" },
  { id: "preprocess", title: "预编译" },
  { id: "lexer", title: "词法分析" },
  { id: "parser", title: "语法分析" },
  { id: "semantic", title: "语义分析" },
  { id: "ir", title: "IR 降级" },
  { id: "codegen", title: "代码生成" },
  { id: "toolchain", title: "工具链" },
  { id: "execution", title: "执行" },
];

function stageIndex(id: string): number {
  const index = STAGES.findIndex((stage) => stage.id === id);
  return index < 0 ? 0 : index;
}

export default MiniCStageListFactory;
