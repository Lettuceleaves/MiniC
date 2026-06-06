import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCStyledTextSegment } from "./MiniCStyledTextSegment";
import { MiniCTextStyleRole } from "./MiniCTextStyleRole";
import type { MiniCTextStyleRole as MiniCTextStyleRoleValue } from "./MiniCTextStyleRole";

export const miniCLineTokenHighlighterMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCLineTokenHighlighter.java",
  webPath: "uiweb/src/text/MiniCLineTokenHighlighter.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCLineTokenHighlighter",
  kind: "class",
  imports: ["java.util.ArrayList", "java.util.List", "java.util.regex.Pattern"],
  fields: [{ name: "TOKEN", signature: "private static final Pattern TOKEN =" }],
  methods: [
    { name: "highlight", signature: "highlight(String line, TokenClassifier classifier)" },
    { name: "add", signature: "add(List<MiniCStyledTextSegment> segments, String text, MiniCTextStyleRole role)" },
  ],
} as const satisfies JavaMirrorFile;

export interface MiniCTokenClassifier {
  roleFor(token: string, startOffset: number, fullLine: string): MiniCTextStyleRoleValue;
}

const TOKEN_PATTERN = /[-+]?0x[0-9A-Fa-f]+|[-+]?[0-9]+(?:\.[0-9]+)?|[%@$&.]?[A-Za-z0-9_][A-Za-z0-9_.$]*|\S/g;

export class MiniCLineTokenHighlighter {
  static readonly mirror = miniCLineTokenHighlighterMirror;

  static highlight(line: string | null | undefined, classifier: MiniCTokenClassifier): readonly MiniCStyledTextSegment[] {
    const text = line && line.length > 0 ? line : " ";
    const segments: MiniCStyledTextSegment[] = [];
    let cursor = 0;

    for (const match of text.matchAll(TOKEN_PATTERN)) {
      const token = match[0];
      const start = match.index ?? 0;
      if (start > cursor) {
        this.add(segments, text.slice(cursor, start), MiniCTextStyleRole.CODE_PLAIN);
      }
      this.add(segments, token, classifier.roleFor(token, start, text));
      cursor = start + token.length;
    }

    if (cursor < text.length) {
      this.add(segments, text.slice(cursor), MiniCTextStyleRole.CODE_PLAIN);
    }

    return segments;
  }

  static add(
    segments: MiniCStyledTextSegment[],
    text: string,
    role: MiniCTextStyleRoleValue,
  ): void {
    if (text.length === 0) {
      return;
    }
    const previous = segments.at(-1);
    if (previous && previous.role === role) {
      segments[segments.length - 1] = new MiniCStyledTextSegment(previous.text + text, role);
      return;
    }
    segments.push(new MiniCStyledTextSegment(text, role));
  }
}

export default MiniCLineTokenHighlighter;
