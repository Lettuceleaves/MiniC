import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiLexerTokenVisualDto } from "../translation/uiapi";
import { MiniCStyledTextSegment } from "./MiniCStyledTextSegment";
import { MiniCLineTokenHighlighter } from "./MiniCLineTokenHighlighter";
import { MiniCTextStyleRole } from "./MiniCTextStyleRole";
import { MiniCSyntaxTextStyleMapper } from "./MiniCSyntaxTextStyleMapper";

export const miniCSourceTextHighlighterMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCSourceTextHighlighter.java",
  webPath: "uiweb/src/text/MiniCSourceTextHighlighter.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCSourceTextHighlighter",
  kind: "class",
  imports: [
    "java.util.ArrayList",
    "java.util.Comparator",
    "java.util.List",
    "minic.uiapi.MiniCRealtimeAnalysisApi",
    "minic.uiapi.UiLexerTokenVisualDto"
  ],
  fields: [
    {
      "name": "api",
      "signature": "private final MiniCRealtimeAnalysisApi api="
    },
    {
      "name": "styleMapper",
      "signature": "private final MiniCSyntaxTextStyleMapper styleMapper="
    }
  ],
  methods: [
    {
      "name": "highlight",
      "signature": "highlight(String source)"
    },
    {
      "name": "highlightTokens",
      "signature": "highlightTokens(String source)"
    },
    {
      "name": "safeOffset",
      "signature": "safeOffset(String source,int offset)"
    }
  ],
} as const satisfies JavaMirrorFile;

export class MiniCSourceTextHighlighter {
  static readonly mirror = miniCSourceTextHighlighterMirror;

  private readonly styleMapper = new MiniCSyntaxTextStyleMapper();

  highlight(
    source: string | null | undefined,
    tokens: readonly UiLexerTokenVisualDto[] = [],
  ): readonly MiniCStyledTextSegment[] {
    const text = source && source.length > 0 ? source : " ";
    const sortedTokens = [...tokens
      .filter((token) => token.kind !== "EOF" && token.range !== null)
      .filter((token) => token.range !== null && token.range.endOffset > token.range.startOffset)]
      .sort((left, right) => safeOffset(text, left.range?.startOffset ?? 0) - safeOffset(text, right.range?.startOffset ?? 0));
    if (sortedTokens.length === 0) {
      return [new MiniCStyledTextSegment(text, MiniCTextStyleRole.CODE_PLAIN)];
    }
    const segments: MiniCStyledTextSegment[] = [];
    let cursor = 0;
    for (let index = 0; index < sortedTokens.length; index += 1) {
      const token = sortedTokens[index];
      if (token.range === null) {
        continue;
      }
      const start = safeOffset(text, token.range.startOffset);
      const end = safeOffset(text, token.range.endOffset);
      if (end <= start || start < cursor) {
        continue;
      }
      if (start > cursor) {
        MiniCLineTokenHighlighter.add(segments, text.slice(cursor, start), MiniCTextStyleRole.CODE_PLAIN);
      }
      MiniCLineTokenHighlighter.add(segments, text.slice(start, end), this.styleMapper.roleForToken(sortedTokens, index));
      cursor = end;
    }
    if (cursor < text.length) {
      MiniCLineTokenHighlighter.add(segments, text.slice(cursor), MiniCTextStyleRole.CODE_PLAIN);
    }
    return segments;
  }
}

function safeOffset(source: string, offset: number): number {
  return Math.max(0, Math.min(Math.trunc(offset), source.length));
}

export default MiniCSourceTextHighlighter;
