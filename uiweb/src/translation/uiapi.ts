export type MiniCStageId =
  | "source"
  | "preprocess"
  | "lexer"
  | "parser"
  | "semantic"
  | "ir"
  | "codegen"
  | "toolchain"
  | "execution";

export type MiniCPlaybackMode = "PAUSED" | "PLAYING" | "FAST_PLAYING";

export interface UiSourceRangeDto {
  readonly sourceName: string;
  readonly startOffset: number;
  readonly endOffset: number;
}

export interface UiSourceSpanDto extends UiSourceRangeDto {
  readonly startLine: number;
  readonly startColumn: number;
  readonly endLine: number;
  readonly endColumn: number;
}

export interface UiDiagnosticDto {
  readonly code: string;
  readonly severity: string;
  readonly message: string;
  readonly sourceName: string;
  readonly startOffset: number;
  readonly endOffset: number;
}

export interface UiControlResultDto {
  readonly outcome: string;
  readonly stage: MiniCStageId;
  readonly title: string;
  readonly description: string;
  readonly diagnostics: readonly UiDiagnosticDto[];
}

export interface UiCurrentStateDto {
  readonly sourceName: string;
  readonly currentStage: MiniCStageId;
  readonly globalStepIndex: number;
  readonly stageStepIndex: number;
  readonly playbackMode: MiniCPlaybackMode;
  readonly frameIntervalMillis: number;
  readonly sourceRange: UiSourceRangeDto | null;
  readonly title: string;
  readonly description: string;
  readonly diagnostics: readonly UiDiagnosticDto[];
  readonly canNext: boolean;
  readonly canPrevious: boolean;
  readonly canPlay: boolean;
  readonly canPlayFast: boolean;
  readonly canPause: boolean;
  readonly canReversePlay: boolean;
}

export interface UiStageDataDto {
  readonly stage: MiniCStageId;
  readonly completedSteps: number;
  readonly totalSteps: number;
  readonly completed: boolean;
  readonly inputSummary: readonly string[];
  readonly currentItem: string;
  readonly accumulatedOutput: readonly string[];
  readonly diagnostics: readonly UiDiagnosticDto[];
}

export interface UiGlobalDataDto {
  readonly source: string;
  readonly stageSummaries: readonly string[];
  readonly diagnostics: readonly UiDiagnosticDto[];
  readonly preprocessSummary: readonly string[];
  readonly tokenSummary: readonly string[];
  readonly astSummary: readonly string[];
  readonly semanticSummary: readonly string[];
  readonly irSummary: readonly string[];
  readonly assemblySummary: readonly string[];
  readonly artifactSummary: readonly string[];
  readonly executionInputSummary: readonly string[];
  readonly executionOutputSummary: readonly string[];
}

export interface UiLexerTokenVisualDto {
  readonly kind: string;
  readonly text: string;
  readonly range: UiSourceSpanDto | null;
  readonly active: boolean;
}

export interface UiAstNodeVisualDto {
  readonly id: string;
  readonly label: string;
  readonly kind: string;
  readonly range: UiSourceSpanDto | null;
  readonly active: boolean;
  readonly children: readonly UiAstNodeVisualDto[];
}

export interface UiSemanticScopeVisualDto {
  readonly id: string;
  readonly label: string;
  readonly symbols: readonly string[];
  readonly range: UiSourceSpanDto | null;
  readonly active: boolean;
  readonly children: readonly UiSemanticScopeVisualDto[];
}

export interface UiIrLineVisualDto {
  readonly lineNumber: number;
  readonly text: string;
  readonly range: UiSourceSpanDto | null;
  readonly active: boolean;
}

export interface UiAssemblyLineVisualDto {
  readonly lineNumber: number;
  readonly text: string;
  readonly kind: string;
  readonly section: string;
  readonly label: string;
  readonly range: UiSourceSpanDto | null;
  readonly active: boolean;
}

export interface UiStageVisualDto {
  readonly stage: MiniCStageId;
  readonly visualType: string;
  readonly sourceText: string;
  readonly genericItems: readonly string[];
  readonly lexerTokens: readonly UiLexerTokenVisualDto[];
  readonly astRoot: UiAstNodeVisualDto | null;
  readonly semanticRoot: UiSemanticScopeVisualDto | null;
  readonly semanticEdgesPointChildToParent: boolean;
  readonly irLines: readonly UiIrLineVisualDto[];
  readonly assemblyLines: readonly UiAssemblyLineVisualDto[];
}

export interface UiRealtimeAnalysisDto {
  readonly sourceName: string;
  readonly sourceText: string;
  readonly diagnostics: readonly UiDiagnosticDto[];
  readonly tokens: readonly UiLexerTokenVisualDto[];
  readonly version: number;
}

export interface UiDebugBreakpointDto {
  readonly line: number;
  readonly enabled: boolean;
}

export interface UiDebugStateDto {
  readonly sourceName: string;
  readonly playbackMode: MiniCPlaybackMode;
  readonly currentLine: number;
  readonly breakpoints: readonly UiDebugBreakpointDto[];
  readonly timeline: readonly string[];
}

export interface UiDebugMetadataViewDto {
  readonly rows: readonly string[];
}

export interface UiDebugDataStructureViewDto {
  readonly title: string;
  readonly rows: readonly string[];
}

export interface UiDebugAstViewDto {
  readonly root: UiAstNodeVisualDto | null;
  readonly details: readonly string[];
}

export interface UiDebugIrViewDto {
  readonly lines: readonly UiIrLineVisualDto[];
}

export interface UiDebugAsmViewDto {
  readonly lines: readonly UiAssemblyLineVisualDto[];
}
