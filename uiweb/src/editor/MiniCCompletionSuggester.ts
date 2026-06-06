import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiLexerTokenVisualDto } from "../translation/uiTypes";

export const miniCCompletionSuggesterMirror = {
  "javaPath": "src/main/java/minic/uilocal/editor/MiniCCompletionSuggester.java",
  "webPath": "uiweb/src/editor/MiniCCompletionSuggester.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCCompletionSuggester",
  "kind": "class",
  "imports": [
    "minic.uiapi.UiLexerTokenVisualDto",
    "java.util.LinkedHashSet",
    "java.util.List",
    "java.util.Locale",
    "java.util.Set",
    "java.util.regex.Matcher",
    "java.util.regex.Pattern"
  ],
  "fields": [
    {
      "name": "KEYWORDS",
      "signature": "private static final List<String> KEYWORDS ="
    },
    {
      "name": "COMMON_EXTERNALS",
      "signature": "private static final List<String> COMMON_EXTERNALS ="
    },
    {
      "name": "IDENTIFIER_PATTERN",
      "signature": "private static final Pattern IDENTIFIER_PATTERN ="
    },
    {
      "name": "DECLARED_NAME_PATTERN",
      "signature": "private static final Pattern DECLARED_NAME_PATTERN ="
    }
  ],
  "methods": [
    {
      "name": "extractDeclaredNames",
      "signature": "extractDeclaredNames(String source, List<UiLexerTokenVisualDto> tokens)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCCompletionSuggester {
  static readonly mirror = miniCCompletionSuggesterMirror;

  static readonly KEYWORDS = Object.freeze([
    "bool",
    "char",
    "int",
    "long",
    "float",
    "double",
    "extern",
    "struct",
    "return",
    "if",
    "else",
    "while",
    "for",
    "break",
    "continue",
    "true",
    "false",
    "null",
  ]);

  static readonly COMMON_EXTERNALS = Object.freeze([
    "printf",
    "scanf",
    "puts",
    "getchar",
    "putchar",
    "malloc",
    "free",
    "memset",
    "memcpy",
    "strlen",
  ]);

  private static readonly IDENTIFIER_PATTERN = /\b[A-Za-z_][A-Za-z0-9_]*\b/gu;

  private static readonly DECLARED_NAME_PATTERN =
    /\b(?:extern\s+)?(?:bool|char|int|long|float|double|struct\s+[A-Za-z_][A-Za-z0-9_]*)(?:\s*\*)*\s+([A-Za-z_][A-Za-z0-9_]*)/gu;

  readonly mirror = miniCCompletionSuggesterMirror;

  static suggestions(
    prefix: string,
    source: string,
    tokens: readonly UiLexerTokenVisualDto[] = [],
  ): readonly string[] {
    const normalizedPrefix = prefix.toLocaleLowerCase();
    const candidates = new Set<string>();
    for (const keyword of MiniCCompletionSuggester.KEYWORDS) {
      candidates.add(keyword);
    }
    for (const name of MiniCCompletionSuggester.extractDeclaredNames(source, tokens)) {
      candidates.add(name);
    }
    for (const external of MiniCCompletionSuggester.COMMON_EXTERNALS) {
      candidates.add(external);
    }
    return [...candidates]
      .filter((candidate) => prefix.length === 0 || candidate.toLocaleLowerCase().startsWith(normalizedPrefix))
      .filter((candidate) => candidate !== prefix)
      .slice(0, 9);
  }

  static extractDeclaredNames(source: string, tokens: readonly UiLexerTokenVisualDto[] = []): ReadonlySet<string> {
    const names = new Set<string>();
    for (const token of tokens) {
      if (token.kind === "IDENTIFIER") {
        names.add(token.text);
      }
    }
    for (const match of source.matchAll(MiniCCompletionSuggester.DECLARED_NAME_PATTERN)) {
      const name = match[1];
      if (name) {
        names.add(name);
      }
    }
    for (const match of source.matchAll(MiniCCompletionSuggester.IDENTIFIER_PATTERN)) {
      const identifier = match[0];
      if (!MiniCCompletionSuggester.KEYWORDS.includes(identifier)) {
        names.add(identifier);
      }
    }
    return names;
  }

  suggestions(prefix: string, source: string, tokens: readonly UiLexerTokenVisualDto[] = []): readonly string[] {
    return MiniCCompletionSuggester.suggestions(prefix, source, tokens);
  }

  extractDeclaredNames(source: string, tokens: readonly UiLexerTokenVisualDto[] = []): ReadonlySet<string> {
    return MiniCCompletionSuggester.extractDeclaredNames(source, tokens);
  }

  summary(): string {
    return `MiniCCompletionSuggester: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCCompletionSuggester;
