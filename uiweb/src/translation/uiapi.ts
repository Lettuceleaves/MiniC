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

export interface UiDebugVisualElementDto {
  readonly id: string;
  readonly kind: string;
  readonly label: string;
  readonly metadata: Readonly<Record<string, string>>;
}

export interface UiDebugVisualStructureDto {
  readonly id: string;
  readonly name: string;
  readonly type: string;
  readonly kind: string;
  readonly layoutHint: string;
  readonly summary: string;
  readonly explanation: string;
  readonly elements: readonly UiDebugVisualElementDto[];
}

export interface UiDebugVariableDto {
  readonly name: string;
  readonly address: string;
  readonly typeName: string;
  readonly valueKind: string;
  readonly valueSummary: string;
  readonly pointerTarget: string;
  readonly typeShape: string;
  readonly highlightedChange: boolean;
  readonly explanation: string;
  readonly fields: readonly UiDebugVariableDto[];
  readonly elements: readonly UiDebugVariableDto[];
}

export interface UiDebugFrameDto {
  readonly frameId: string;
  readonly functionName: string;
  readonly parameters: readonly UiDebugVariableDto[];
  readonly locals: readonly UiDebugVariableDto[];
  readonly returnTarget: string | null;
  readonly activeRange: UiSourceSpanDto | null;
}

export interface UiDebugEventDto {
  readonly eventId: number;
  readonly snapshotId: number;
  readonly type: string;
  readonly title: string;
  readonly description: string;
  readonly sourceRange: UiSourceSpanDto | null;
  readonly affectedValueRefs: readonly string[];
}

export interface UiDebugTimelineItemDto {
  readonly snapshotId: number;
  readonly visibleStepIndex: number;
  readonly stopReason: string;
  readonly breakpointHit: boolean;
  readonly sourceRange: UiSourceSpanDto | null;
}

export interface UiDebugProcessSpaceDto {
  readonly currentFunctionName: string;
  readonly currentInstructionId: string;
  readonly functions: readonly string[];
  readonly staticValues: readonly UiDebugVariableDto[];
  readonly stackFrames: readonly UiDebugFrameDto[];
  readonly heapValues: readonly UiDebugVariableDto[];
  readonly stdin: string;
  readonly stdout: string;
  readonly stderr: string;
}

export interface UiDebugSnapshotDto {
  readonly snapshotId: number;
  readonly visibleStepIndex: number;
  readonly functionName: string;
  readonly blockLabel: string;
  readonly instructionId: string;
  readonly sourceRange: UiSourceSpanDto | null;
  readonly callStackSummary: readonly string[];
  readonly processSpace: UiDebugProcessSpaceDto;
  readonly breakpointHit: boolean;
  readonly stopReason: string;
}

export interface UiDebugStateDto {
  readonly sourceName: string;
  readonly executionState: string;
  readonly currentSnapshot: UiDebugSnapshotDto;
  readonly snapshots: readonly UiDebugSnapshotDto[];
  readonly events: readonly UiDebugEventDto[];
  readonly breakpoints: readonly UiDebugBreakpointDto[];
}

export interface UiDebugMetadataViewDto {
  readonly executionState: string;
  readonly stopReason: string;
  readonly currentFunction: string;
  readonly currentSourceRange: UiSourceSpanDto | null;
  readonly callStack: readonly UiDebugFrameDto[];
  readonly variables: readonly UiDebugVariableDto[];
  readonly stdout: string;
  readonly stderr: string;
  readonly breakpoints: readonly UiDebugBreakpointDto[];
  readonly events: readonly UiDebugEventDto[];
  readonly timeline: readonly UiDebugTimelineItemDto[];
}

export interface UiDebugDataStructureViewDto {
  readonly processSpace: UiDebugProcessSpaceDto;
  readonly visuals: readonly UiDebugVisualStructureDto[];
  readonly warnings: readonly string[];
}

export interface UiDebugAstNodeDetailDto {
  readonly nodeId: string;
  readonly kind: string;
  readonly label: string;
  readonly sourceRange: UiSourceSpanDto | null;
  readonly explanation: string;
}

export interface UiDebugAstViewDto {
  readonly root: UiAstNodeVisualDto;
  readonly activeNode: UiDebugAstNodeDetailDto | null;
  readonly relatedIrIds: readonly string[];
  readonly relatedAsmIds: readonly string[];
}

export interface UiDebugIrOperandDto {
  readonly name: string;
  readonly typeName: string;
  readonly valueSummary: string;
  readonly valueRef: string;
}

export interface UiDebugIrViewDto {
  readonly lines: readonly UiIrLineVisualDto[];
  readonly currentInstructionId: string;
  readonly currentSourceRange: UiSourceSpanDto | null;
  readonly explanation: string;
  readonly operands: readonly UiDebugIrOperandDto[];
}

export interface UiDebugAsmViewDto {
  readonly lines: readonly UiAssemblyLineVisualDto[];
  readonly explanation: string;
  readonly relatedIrIds: readonly string[];
}
