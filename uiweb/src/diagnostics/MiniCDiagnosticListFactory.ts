import type { JavaMirrorFile } from "../translation/javaMirror";
import type {
  UiDiagnosticDto,
  UiGlobalDataDto,
  UiRealtimeAnalysisDto,
  UiStageDataDto,
} from "../translation/uiapi";
import { MiniCDiagnosticItem } from "./MiniCDiagnosticItem";

export const miniCDiagnosticListFactoryMirror = {
  "javaPath": "src/main/java/minic/uilocal/diagnostics/MiniCDiagnosticListFactory.java",
  "webPath": "uiweb/src/diagnostics/MiniCDiagnosticListFactory.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCDiagnosticListFactory",
  "kind": "class",
  "imports": [
    "minic.uiapi.UiDiagnosticDto",
    "minic.uiapi.UiGlobalDataDto",
    "minic.uiapi.UiRealtimeAnalysisDto",
    "minic.uiapi.UiSourceRangeDto",
    "minic.uiapi.UiStageDataDto",
    "java.util.List"
  ],
  "fields": [],
  "methods": [
    {
      "name": "create",
      "signature": "create(UiStageDataDto stageData, UiGlobalDataDto globalData)"
    },
    {
      "name": "create",
      "signature": "create(UiStageDataDto stageData, UiGlobalDataDto globalData, UiRealtimeAnalysisDto realtimeAnalysis)"
    },
    {
      "name": "from",
      "signature": "from(UiDiagnosticDto diagnostic)"
    },
    {
      "name": "from",
      "signature": "from(UiDiagnosticDto diagnostic, UiRealtimeAnalysisDto analysis)"
    },
    {
      "name": "locationAt",
      "signature": "locationAt(String source, int offset)"
    },
    {
      "name": "SourceLocation",
      "signature": "SourceLocation(int line, int column)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCDiagnosticListFactory {
  static readonly mirror = miniCDiagnosticListFactoryMirror;

  readonly mirror = miniCDiagnosticListFactoryMirror;

  create(
    stageData: UiStageDataDto | null,
    globalData: UiGlobalDataDto | null,
    realtimeAnalysis: UiRealtimeAnalysisDto | null = null,
  ): readonly MiniCDiagnosticItem[] {
    if (realtimeAnalysis !== null && realtimeAnalysis.diagnostics.length > 0) {
      return realtimeAnalysis.diagnostics.map((diagnostic) => this.from(diagnostic, realtimeAnalysis));
    }
    const diagnostics =
      globalData !== null && globalData.diagnostics.length > 0
        ? globalData.diagnostics
        : stageData === null
          ? []
          : stageData.diagnostics;
    return diagnostics.map((diagnostic) => this.from(diagnostic));
  }

  from(diagnostic: UiDiagnosticDto, analysis: UiRealtimeAnalysisDto | null = null): MiniCDiagnosticItem {
    const location =
      analysis === null
        ? { line: 1, column: Math.max(1, diagnostic.startOffset + 1) }
        : locationAt(analysis.sourceText, diagnostic.startOffset);
    return new MiniCDiagnosticItem(
      diagnostic.code,
      diagnostic.severity,
      diagnostic.message,
      {
        sourceName: diagnostic.sourceName,
        startOffset: diagnostic.startOffset,
        endOffset: diagnostic.endOffset,
      },
      location.line,
      location.column,
    );
  }

  summary(): string {
    return `MiniCDiagnosticListFactory: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

interface SourceLocation {
  readonly line: number;
  readonly column: number;
}

function locationAt(source: string, offset: number): SourceLocation {
  const safeOffset = Math.max(0, Math.min(Math.trunc(offset), source.length));
  let line = 1;
  let column = 1;
  for (let index = 0; index < safeOffset; index += 1) {
    if (source[index] === "\n") {
      line += 1;
      column = 1;
    } else {
      column += 1;
    }
  }
  return { line, column };
}

export default MiniCDiagnosticListFactory;
