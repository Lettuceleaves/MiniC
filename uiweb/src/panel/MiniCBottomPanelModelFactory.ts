import type { JavaMirrorFile } from "../translation/javaMirror";
import type {
  UiDiagnosticDto,
  UiGlobalDataDto,
  UiRealtimeAnalysisDto,
  UiStageDataDto,
} from "../translation/uiapi";
import { MiniCBottomPanelModel } from "./MiniCBottomPanelModel";

export const miniCBottomPanelModelFactoryMirror = {
  "javaPath": "src/main/java/minic/uilocal/panel/MiniCBottomPanelModelFactory.java",
  "webPath": "uiweb/src/panel/MiniCBottomPanelModelFactory.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCBottomPanelModelFactory",
  "kind": "class",
  "imports": [
    "java.util.List",
    "minic.uiapi.UiDiagnosticDto",
    "minic.uiapi.UiGlobalDataDto",
    "minic.uiapi.UiRealtimeAnalysisDto",
    "minic.uiapi.UiStageDataDto"
  ],
  "fields": [],
  "methods": [
    {
      "name": "create",
      "signature": "create(UiStageDataDto stageData,UiGlobalDataDto globalData,UiRealtimeAnalysisDto realtimeAnalysis)"
    },
    {
      "name": "create",
      "signature": "create(UiStageDataDto stageData,UiGlobalDataDto globalData)"
    },
    {
      "name": "problems",
      "signature": "problems(UiStageDataDto stageData,UiGlobalDataDto globalData,UiRealtimeAnalysisDto realtimeAnalysis)"
    },
    {
      "name": "terminal",
      "signature": "terminal(UiStageDataDto stageData)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCBottomPanelModelFactory {
  static readonly mirror = miniCBottomPanelModelFactoryMirror;

  readonly mirror = miniCBottomPanelModelFactoryMirror;

  create(
    stageData: UiStageDataDto | null,
    globalData: UiGlobalDataDto | null,
    realtimeAnalysis: UiRealtimeAnalysisDto | null = null,
  ): MiniCBottomPanelModel {
    const problems = this.problems(stageData, globalData, realtimeAnalysis);
    const output =
      stageData === null
        ? ["等待开始观测会话"]
        : stageData.accumulatedOutput.length === 0
          ? [stageData.currentItem]
          : stageData.accumulatedOutput;
    const terminal = this.terminal(stageData);
    return new MiniCBottomPanelModel(problems, output, terminal);
  }

  problems(
    stageData: UiStageDataDto | null,
    globalData: UiGlobalDataDto | null,
    realtimeAnalysis: UiRealtimeAnalysisDto | null,
  ): readonly string[] {
    if (realtimeAnalysis !== null) {
      if (realtimeAnalysis.diagnostics.length === 0) {
        return ["OK  实时分析通过"];
      }
      return realtimeAnalysis.diagnostics.map(formatDiagnostic);
    }
    const diagnostics =
      globalData !== null && globalData.diagnostics.length > 0
        ? globalData.diagnostics
        : stageData !== null && stageData.diagnostics.length > 0
          ? stageData.diagnostics
          : null;
    if (diagnostics === null) {
      return ["OK  暂无 diagnostics"];
    }
    return diagnostics.map(formatDiagnostic);
  }

  terminal(stageData: UiStageDataDto | null): readonly string[] {
    if (stageData === null) {
      return ["PS> minic observe <source.mc>"];
    }
    return [
      `PS> minic observe --stage ${stageData.stage}`,
      `${stageData.stage}[${stageData.completedSteps}/${stageData.totalSteps}] ${stageData.currentItem}`,
    ];
  }

  summary(): string {
    return `MiniCBottomPanelModelFactory: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

function formatDiagnostic(diagnostic: UiDiagnosticDto): string {
  return `${diagnostic.severity}  ${diagnostic.code}  ${diagnostic.message}`;
}

export default MiniCBottomPanelModelFactory;
