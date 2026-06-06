export type JavaMirrorKind = "class" | "enum" | "interface" | "record" | "component";

export interface JavaMirrorMember {
  readonly name: string;
  readonly signature: string;
}

export interface JavaMirrorFile {
  readonly javaPath: string;
  readonly webPath: string;
  readonly packageName: string;
  readonly exportName: string;
  readonly kind: JavaMirrorKind;
  readonly imports: readonly string[];
  readonly fields: readonly JavaMirrorMember[];
  readonly methods: readonly JavaMirrorMember[];
}

export function summarizeMirror(file: JavaMirrorFile): string {
  return `${file.exportName}: ${file.methods.length} methods, ${file.fields.length} fields`;
}

export type UiNullable<T> = T | null;

export interface UiSourceSpanDto {
  readonly sourceName: string;
  readonly startOffset: number;
  readonly endOffset: number;
  readonly startLine: number;
  readonly startColumn: number;
  readonly endLine: number;
  readonly endColumn: number;
}

export interface UiLexerTokenVisualDto {
  readonly kind: string;
  readonly text: string;
  readonly range: UiNullable<UiSourceSpanDto>;
  readonly startOffset: number;
  readonly endOffset: number;
  readonly startLine: number;
  readonly startColumn: number;
  readonly endLine: number;
  readonly endColumn: number;
  readonly active: boolean;
}

export interface UiAstNodeVisualDto {
  readonly id: string;
  readonly label: string;
  readonly kind: string;
  readonly range: UiNullable<UiSourceSpanDto>;
  readonly active: boolean;
  readonly children: readonly UiAstNodeVisualDto[];
}

export interface UiSemanticScopeVisualDto {
  readonly id: string;
  readonly label: string;
  readonly symbols: readonly string[];
  readonly range: UiNullable<UiSourceSpanDto>;
  readonly active: boolean;
  readonly children: readonly UiSemanticScopeVisualDto[];
}

export interface UiIrLineVisualDto {
  readonly lineNumber: number;
  readonly text: string;
  readonly range: UiNullable<UiSourceSpanDto>;
  readonly active: boolean;
}

export interface UiAssemblyLineVisualDto {
  readonly lineNumber: number;
  readonly text: string;
  readonly kind: string;
  readonly section: string;
  readonly label: string;
  readonly range: UiNullable<UiSourceSpanDto>;
  readonly active: boolean;
}

export interface UiStageVisualDto {
  readonly stage: string;
  readonly visualType: string;
  readonly sourceText: string;
  readonly genericItems: readonly string[];
  readonly lexerTokens: readonly UiLexerTokenVisualDto[];
  readonly astRoot: UiNullable<UiAstNodeVisualDto>;
  readonly semanticRoot: UiNullable<UiSemanticScopeVisualDto>;
  readonly semanticEdgesPointChildToParent: boolean;
  readonly irLines: readonly UiIrLineVisualDto[];
  readonly assemblyLines: readonly UiAssemblyLineVisualDto[];
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
  readonly returnTarget: UiNullable<string>;
  readonly activeRange: UiNullable<UiSourceSpanDto>;
}

export interface UiDebugEventDto {
  readonly eventId: number;
  readonly snapshotId: number;
  readonly type: string;
  readonly title: string;
  readonly description: string;
  readonly sourceRange: UiNullable<UiSourceSpanDto>;
  readonly affectedValueRefs: readonly string[];
}

export interface UiDebugTimelineItemDto {
  readonly snapshotId: number;
  readonly visibleStepIndex: number;
  readonly stopReason: string;
  readonly breakpointHit: boolean;
  readonly sourceRange: UiNullable<UiSourceSpanDto>;
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
  readonly sourceRange: UiNullable<UiSourceSpanDto>;
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
  readonly currentSourceRange: UiNullable<UiSourceSpanDto>;
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
  readonly sourceRange: UiNullable<UiSourceSpanDto>;
  readonly explanation: string;
}

export interface UiDebugAstViewDto {
  readonly root: UiAstNodeVisualDto;
  readonly activeNode: UiNullable<UiDebugAstNodeDetailDto>;
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
  readonly currentSourceRange: UiNullable<UiSourceSpanDto>;
  readonly explanation: string;
  readonly operands: readonly UiDebugIrOperandDto[];
}

export interface UiDebugAsmViewDto {
  readonly lines: readonly UiAssemblyLineVisualDto[];
  readonly explanation: string;
  readonly relatedIrIds: readonly string[];
}
