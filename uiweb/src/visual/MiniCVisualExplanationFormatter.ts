import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCVisualExplanationFormatterMirror = {
  "javaPath": "src/main/java/minic/uilocal/visual/MiniCVisualExplanationFormatter.java",
  "webPath": "uiweb/src/visual/MiniCVisualExplanationFormatter.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCVisualExplanationFormatter",
  "kind": "class",
  "imports": [
    "minic.uiapi.ExplanationTemplates",
    "minic.uiapi.UiAstNodeVisualDto",
    "minic.uiapi.UiIrLineVisualDto",
    "minic.uiapi.UiLexerTokenVisualDto",
    "minic.uiapi.UiSourceSpanDto",
    "java.util.LinkedHashMap",
    "java.util.Map",
    "java.util.function.Function"
  ],
  "fields": [
    {
      "name": "sourceSnippetProvider",
      "signature": "private final Function<UiSourceSpanDto, String> sourceSnippetProvider;"
    }
  ],
  "methods": [
    {
      "name": "tokenRole",
      "signature": "tokenRole(String kind, Map<String, String> variables)"
    },
    {
      "name": "isTypeKeyword",
      "signature": "isTypeKeyword(String kind)"
    },
    {
      "name": "isControlKeyword",
      "signature": "isControlKeyword(String kind)"
    },
    {
      "name": "tokenVariables",
      "signature": "tokenVariables(UiLexerTokenVisualDto token)"
    },
    {
      "name": "astVariables",
      "signature": "astVariables(UiAstNodeVisualDto node)"
    },
    {
      "name": "irVariables",
      "signature": "irVariables(UiIrLineVisualDto line)"
    },
    {
      "name": "assemblyVariables",
      "signature": "assemblyVariables(MiniCAssemblyTextLine line)"
    },
    {
      "name": "rangeValue",
      "signature": "rangeValue(UiSourceSpanDto range)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCVisualExplanationFormatter {
  static readonly mirror = miniCVisualExplanationFormatterMirror;

  readonly mirror = miniCVisualExplanationFormatterMirror;

  summary(): string {
    return `MiniCVisualExplanationFormatter: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCVisualExplanationFormatter;
