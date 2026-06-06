import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCStyledTextSegment } from "./MiniCStyledTextSegment";
import { MiniCTextStyleRole } from "./MiniCTextStyleRole";
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

export class MiniCSourceTextHighlighter {
  static readonly mirror = miniCSourceTextHighlighterMirror;

  private readonly styleMapper = new MiniCSyntaxTextStyleMapper();

  highlight(source: string | null | undefined): readonly MiniCStyledTextSegment[] {
    const text = source && source.length > 0 ? source : " ";
    return [new MiniCStyledTextSegment(text, MiniCTextStyleRole.CODE_PLAIN)];
  }
}

export default MiniCSourceTextHighlighter;
