import type { JavaMirrorFile, UiLexerTokenVisualDto, UiStageVisualDto } from "../translation/javaMirror";
import { MiniCLexerOverlayLine } from "./MiniCLexerOverlayLine";
import { MiniCLexerOverlaySegment } from "./MiniCLexerOverlaySegment";

export const miniCLexerOverlayModelFactoryMirror = {
  javaPath: "src/main/java/minic/uilocal/visual/MiniCLexerOverlayModelFactory.java",
  webPath: "uiweb/src/visual/MiniCLexerOverlayModelFactory.ts",
  packageName: "minic.uilocal.visual",
  exportName: "MiniCLexerOverlayModelFactory",
  kind: "class",
  imports: [
    "java.util.ArrayList",
    "java.util.List",
    "minic.uiapi.UiLexerTokenVisualDto",
    "minic.uiapi.UiStageVisualDto"
  ],
  fields: [],
  methods: [
    {
      "name": "create",
      "signature": "create(String source,UiStageVisualDto visual)"
    },
    {
      "name": "segments",
      "signature": "segments(String source,int lineStart,int lineEnd,UiLexerTokenVisualDto activeToken)"
    }
  ],
} as const satisfies JavaMirrorFile;

export class MiniCLexerOverlayModelFactory {
  static readonly mirror = miniCLexerOverlayModelFactoryMirror;

  create(source: string, visual: UiStageVisualDto | null | undefined): readonly MiniCLexerOverlayLine[] {
    const activeToken = visual?.lexerTokens.find((token) => token.active) ?? null;
    const rows: MiniCLexerOverlayLine[] = [];
    let lineStart = 0;
    let lineNumber = 1;

    for (let offset = 0; offset <= source.length; offset++) {
      if (offset === source.length || source.charAt(offset) === "\n") {
        rows.push(new MiniCLexerOverlayLine(lineNumber, this.segments(source, lineStart, offset, activeToken)));
        lineNumber += 1;
        lineStart = offset + 1;
      }
    }

    if (rows.length === 0) {
      rows.push(new MiniCLexerOverlayLine(1, [new MiniCLexerOverlaySegment("", false)]));
    }
    return rows;
  }

  private segments(
    source: string,
    lineStart: number,
    lineEnd: number,
    activeToken: UiLexerTokenVisualDto | null,
  ): readonly MiniCLexerOverlaySegment[] {
    const activeStart = activeToken?.startOffset ?? -1;
    const activeEnd = activeToken?.endOffset ?? -1;
    if (!activeToken || activeEnd <= lineStart || activeStart >= lineEnd) {
      return [new MiniCLexerOverlaySegment(source.slice(lineStart, lineEnd), false)];
    }

    const segments: MiniCLexerOverlaySegment[] = [];
    const beforeEnd = Math.max(lineStart, Math.min(activeStart, lineEnd));
    const hotStart = Math.max(lineStart, activeStart);
    const hotEnd = Math.max(hotStart, Math.min(activeEnd, lineEnd));
    if (beforeEnd > lineStart) {
      segments.push(new MiniCLexerOverlaySegment(source.slice(lineStart, beforeEnd), false));
    }
    segments.push(new MiniCLexerOverlaySegment(source.slice(hotStart, hotEnd), true));
    if (lineEnd > hotEnd) {
      segments.push(new MiniCLexerOverlaySegment(source.slice(hotEnd, lineEnd), false));
    }
    return segments;
  }
}

export default MiniCLexerOverlayModelFactory;
