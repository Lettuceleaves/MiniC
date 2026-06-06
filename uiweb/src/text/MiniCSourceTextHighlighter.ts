import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCLineTokenHighlighter } from "./MiniCLineTokenHighlighter";
import type { MiniCStyledTextSegment } from "./MiniCStyledTextSegment";
import { MiniCTextStyleRole } from "./MiniCTextStyleRole";
import type { MiniCTextStyleRole as MiniCTextStyleRoleValue } from "./MiniCTextStyleRole";
import { MiniCSyntaxTextStyleMapper } from "./MiniCSyntaxTextStyleMapper";

export const miniCSourceTextHighlighterMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCSourceTextHighlighter.java",
  webPath: "uiweb/src/text/MiniCSourceTextHighlighter.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCSourceTextHighlighter",
  kind: "class",
  imports: ["minic.compiler.lexer.Lexer", "java.util.ArrayList", "java.util.List"],
  fields: [{ name: "styleMapper", signature: "private final MiniCSyntaxTextStyleMapper styleMapper =" }],
  methods: [{ name: "highlight", signature: "highlight(String source)" }],
} as const satisfies JavaMirrorFile;

const SOURCE_TOKEN =
  /\/\/[^\n\r]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[-+]?0x[0-9A-Fa-f]+|[-+]?[0-9]+(?:\.[0-9]+)?|[A-Za-z_][A-Za-z0-9_]*|==|!=|<=|>=|->|\+\+|--|\+=|-=|&&|\|\||\S/g;

const KEYWORD_KIND_BY_TEXT = new Map<string, string>([
  ["bool", "BOOL"],
  ["char", "CHAR"],
  ["int", "INT"],
  ["long", "LONG"],
  ["float", "FLOAT"],
  ["double", "DOUBLE"],
  ["extern", "EXTERN"],
  ["struct", "STRUCT"],
  ["return", "RETURN"],
  ["if", "IF"],
  ["else", "ELSE"],
  ["while", "WHILE"],
  ["for", "FOR"],
  ["break", "BREAK"],
  ["continue", "CONTINUE"],
]);

export class MiniCSourceTextHighlighter {
  static readonly mirror = miniCSourceTextHighlighterMirror;

  private readonly styleMapper = new MiniCSyntaxTextStyleMapper();

  highlight(source: string | null | undefined): readonly MiniCStyledTextSegment[] {
    const text = source && source.length > 0 ? source : " ";
    const segments: MiniCStyledTextSegment[] = [];
    let cursor = 0;

    for (const match of text.matchAll(SOURCE_TOKEN)) {
      const token = match[0];
      const start = match.index ?? 0;
      if (start > cursor) {
        MiniCLineTokenHighlighter.add(segments, text.slice(cursor, start), MiniCTextStyleRole.CODE_PLAIN);
      }
      MiniCLineTokenHighlighter.add(segments, token, this.roleForToken(token));
      cursor = start + token.length;
    }

    if (cursor < text.length) {
      MiniCLineTokenHighlighter.add(segments, text.slice(cursor), MiniCTextStyleRole.CODE_PLAIN);
    }

    return segments.length === 0 ? [MiniCLineTokenHighlighter.highlight(" ", { roleFor: () => MiniCTextStyleRole.CODE_PLAIN })[0]] : segments;
  }

  private roleForToken(token: string): MiniCTextStyleRoleValue {
    if (token.startsWith("//")) {
      return MiniCTextStyleRole.CODE_COMMENT;
    }
    if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
      return this.styleMapper.roleFor(token.startsWith("\"") ? "STRING_LITERAL" : "CHAR_LITERAL");
    }
    if (/^[-+]?0x[0-9A-Fa-f]+$/.test(token) || /^[-+]?[0-9]+(?:\.[0-9]+)?$/.test(token)) {
      return this.styleMapper.roleFor(token.includes(".") ? "DOUBLE_LITERAL" : "INTEGER_LITERAL");
    }
    if (token === "true" || token === "false") {
      return this.styleMapper.roleFor("BOOL_LITERAL");
    }
    if (token === "null") {
      return this.styleMapper.roleFor("NULL_LITERAL");
    }
    const keywordKind = KEYWORD_KIND_BY_TEXT.get(token);
    if (keywordKind) {
      return this.styleMapper.roleFor(keywordKind);
    }
    if (/^[A-Za-z_][A-Za-z0-9_]*$/.test(token)) {
      return this.styleMapper.roleFor("IDENTIFIER");
    }
    return MiniCTextStyleRole.CODE_OPERATOR;
  }
}

export default MiniCSourceTextHighlighter;
