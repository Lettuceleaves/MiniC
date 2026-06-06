export interface ViewportPoint {
  readonly x: number;
  readonly y: number;
}

export interface ViewportSize {
  readonly width: number;
  readonly height: number;
}

export interface ViewportBounds extends ViewportPoint, ViewportSize {
}

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

export interface UiDiagnosticDto extends UiSourceRangeDto {
  readonly code: string;
  readonly severity: string;
  readonly message: string;
}

export interface UiLexerTokenVisualDto {
  readonly kind: string;
  readonly text: string;
  readonly range: UiSourceSpanDto | null;
  readonly startOffset: number;
  readonly endOffset: number;
  readonly startLine: number;
  readonly startColumn: number;
  readonly endLine: number;
  readonly endColumn: number;
  readonly active: boolean;
}

export interface UiRealtimeAnalysisDto {
  readonly sourceName: string;
  readonly sourceText: string;
  readonly diagnostics: readonly UiDiagnosticDto[];
  readonly tokens: readonly UiLexerTokenVisualDto[];
  readonly version: number;
}

export interface UiCurrentStateDto {
  readonly sourceRange?: UiSourceRangeDto | null;
}

export type BooleanSupplier = () => boolean;
export type Runnable = () => void;
export type DoubleConsumer = (value: number) => void;
export type LongConsumer = (value: number) => void;
export type Scheduler = (action: Runnable) => void;

export function requireValue<T>(value: T | null | undefined, name: string): T {
  if (value === null || value === undefined) {
    throw new TypeError(`${name} must not be null`);
  }
  return value;
}

export function clampNumber(value: number, min: number, max: number): number {
  if (!Number.isFinite(value)) {
    return min;
  }
  return Math.max(min, Math.min(max, value));
}

export function sourceSpan(
  sourceName: string,
  sourceText: string,
  startOffset: number,
  endOffset: number,
): UiSourceSpanDto {
  const safeStart = clampNumber(Math.trunc(startOffset), 0, sourceText.length);
  const safeEnd = clampNumber(Math.trunc(endOffset), safeStart, sourceText.length);
  const start = sourcePosition(sourceText, safeStart);
  const end = sourcePosition(sourceText, safeEnd);
  return {
    sourceName,
    startOffset: safeStart,
    endOffset: safeEnd,
    startLine: start.line,
    startColumn: start.column,
    endLine: end.line,
    endColumn: end.column,
  };
}

export function sourcePosition(sourceText: string, offset: number): { readonly line: number; readonly column: number } {
  const safeOffset = clampNumber(Math.trunc(offset), 0, sourceText.length);
  let line = 1;
  let lineStart = 0;
  for (let index = 0; index < safeOffset; index += 1) {
    if (sourceText[index] === "\n") {
      line += 1;
      lineStart = index + 1;
    }
  }
  return {
    line,
    column: safeOffset - lineStart + 1,
  };
}
