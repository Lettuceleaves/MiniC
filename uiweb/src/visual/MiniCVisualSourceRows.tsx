import type { ReactElement } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiLexerTokenVisualDto, UiStageVisualDto } from "../translation/uiapi";

export const miniCVisualSourceRowsMirror = {
  "javaPath": "src/main/java/minic/uilocal/visual/MiniCVisualSourceRows.java",
  "webPath": "uiweb/src/visual/MiniCVisualSourceRows.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCVisualSourceRows",
  "kind": "component",
  "imports": [
    "javafx.scene.control.Label",
    "javafx.scene.layout.HBox",
    "minic.uiapi.UiLexerTokenVisualDto",
    "minic.uiapi.UiStageVisualDto",
    "java.util.ArrayList",
    "java.util.List"
  ],
  "fields": [],
  "methods": [
    {
      "name": "activeSourceToken",
      "signature": "activeSourceToken(UiStageVisualDto visual)"
    },
    {
      "name": "lineSeparatorLength",
      "signature": "lineSeparatorLength(String source, int separatorOffset)"
    },
    {
      "name": "sourceLineFlow",
      "signature": "sourceLineFlow(String line, int lineStartOffset, UiLexerTokenVisualDto activeToken)"
    },
    {
      "name": "isMaskedSourceOffset",
      "signature": "isMaskedSourceOffset(int offset, UiLexerTokenVisualDto activeToken)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCVisualSourceRowsProps {
  readonly visual: UiStageVisualDto | null;
}

export function MiniCVisualSourceRows({ visual }: MiniCVisualSourceRowsProps) {
  return <div className="source-flow">{rows(visual)}</div>;
}

MiniCVisualSourceRows.mirror = miniCVisualSourceRowsMirror;

export function rows(visual: UiStageVisualDto | null): readonly ReactElement[] {
  const source = visual?.sourceText ?? "";
  const activeToken = activeSourceToken(visual);
  const sourceRows: ReactElement[] = [];
  let lineStart = 0;
  let lineNumber = 1;
  for (let offset = 0; offset <= source.length; offset += 1) {
    if (offset === source.length || source[offset] === "\n") {
      const line = source.slice(lineStart, offset);
      sourceRows.push(
        <div className="source-flow-line" key={`${lineNumber}-${lineStart}`}>
          <span className="line-number">{lineNumber}</span>
          {sourceLineFlow(line, lineStart, activeToken)}
        </div>,
      );
      lineNumber += 1;
      lineStart = offset + lineSeparatorLength(source, offset);
    }
  }
  return sourceRows.length > 0 ? sourceRows : [<div className="source-flow-line" key="empty"> </div>];
}

export function activeSourceToken(visual: UiStageVisualDto | null | undefined): UiLexerTokenVisualDto | null {
  return visual?.lexerTokens.find((token) => token.active) ?? null;
}

export function lineSeparatorLength(source: string, separatorOffset: number): number {
  if (source[separatorOffset] === "\r" && source[separatorOffset + 1] === "\n") {
    return 2;
  }
  return source[separatorOffset] === "\r" || source[separatorOffset] === "\n" ? 1 : 0;
}

export function sourceLineFlow(line: string, lineStartOffset: number, activeToken: UiLexerTokenVisualDto | null) {
  const text = line.length === 0 ? " " : line;
  return (
    <span className="source-flow-text">
      {Array.from(text).map((char, index) => {
        const offset = lineStartOffset + index;
        return (
          <span className={isMaskedSourceOffset(offset, activeToken) ? "source-token-mask" : ""} key={`${offset}-${char}`}>
            {char === " " ? "\u00a0" : char}
          </span>
        );
      })}
    </span>
  );
}

export function isMaskedSourceOffset(offset: number, activeToken: UiLexerTokenVisualDto | null): boolean {
  const range = activeToken?.range;
  return range !== null && range !== undefined && offset >= range.startOffset && offset < range.endOffset;
}

export default MiniCVisualSourceRows;
