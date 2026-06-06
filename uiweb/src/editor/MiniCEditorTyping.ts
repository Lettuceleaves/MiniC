import type { JavaMirrorFile } from "../translation/javaMirror";
import { clampNumber } from "../translation/uiTypes";

export const miniCEditorTypingMirror = {
  "javaPath": "src/main/java/minic/uilocal/editor/MiniCEditorTyping.java",
  "webPath": "uiweb/src/editor/MiniCEditorTyping.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCEditorTyping",
  "kind": "class",
  "imports": [],
  "fields": [],
  "methods": [
    {
      "name": "wrapOrInsert",
      "signature": "wrapOrInsert(String source, int start, int end, String opening, String closing)"
    },
    {
      "name": "skipOrInsert",
      "signature": "skipOrInsert(String source, int start, int end, String closing)"
    },
    {
      "name": "quoteOrSkip",
      "signature": "quoteOrSkip(String source, int start, int end, String quote)"
    },
    {
      "name": "replace",
      "signature": "replace(String source, int start, int end, String replacement, int caret)"
    },
    {
      "name": "isEmptyPair",
      "signature": "isEmptyPair(char opening, char closing)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCEditResult {
  readonly source: string;
  readonly replaceStart: number;
  readonly replaceEnd: number;
  readonly replacement: string;
  readonly selectionStart: number;
  readonly selectionEnd: number;
}

export class MiniCEditorTyping {
  static readonly mirror = miniCEditorTypingMirror;

  readonly mirror = miniCEditorTypingMirror;

  static type(source: string, selectionStart: number, selectionEnd: number, text: string): MiniCEditResult {
    const { start, end } = MiniCEditorTyping.safeSelection(source, selectionStart, selectionEnd);
    switch (text) {
      case "(":
        return MiniCEditorTyping.wrapOrInsert(source, start, end, "(", ")");
      case "[":
        return MiniCEditorTyping.wrapOrInsert(source, start, end, "[", "]");
      case "{":
        return MiniCEditorTyping.wrapOrInsert(source, start, end, "{", "}");
      case "\"":
      case "'":
        return MiniCEditorTyping.quoteOrSkip(source, start, end, text);
      case ")":
      case "]":
      case "}":
        return MiniCEditorTyping.skipOrInsert(source, start, end, text);
      default:
        return MiniCEditorTyping.replace(source, start, end, text, start + text.length);
    }
  }

  static backspace(source: string, selectionStart: number, selectionEnd: number): MiniCEditResult {
    const { start, end } = MiniCEditorTyping.safeSelection(source, selectionStart, selectionEnd);
    if (end > start) {
      return MiniCEditorTyping.replace(source, start, end, "", start);
    }
    if (start > 0 && start < source.length && MiniCEditorTyping.isEmptyPair(source[start - 1], source[start])) {
      return MiniCEditorTyping.replace(source, start - 1, start + 1, "", start - 1);
    }
    if (start > 0) {
      return MiniCEditorTyping.replace(source, start - 1, start, "", start - 1);
    }
    return {
      source,
      replaceStart: start,
      replaceEnd: end,
      replacement: "",
      selectionStart: start,
      selectionEnd: end,
    };
  }

  static wrapOrInsert(source: string, start: number, end: number, opening: string, closing: string): MiniCEditResult {
    if (end > start) {
      const selected = source.slice(start, end);
      return MiniCEditorTyping.replace(source, start, end, `${opening}${selected}${closing}`, end + opening.length);
    }
    return MiniCEditorTyping.replace(source, start, end, `${opening}${closing}`, start + opening.length);
  }

  static skipOrInsert(source: string, start: number, end: number, closing: string): MiniCEditResult {
    if (start === end && source.startsWith(closing, start)) {
      const caret = start + closing.length;
      return {
        source,
        replaceStart: start,
        replaceEnd: end,
        replacement: "",
        selectionStart: caret,
        selectionEnd: caret,
      };
    }
    return MiniCEditorTyping.replace(source, start, end, closing, start + closing.length);
  }

  static quoteOrSkip(source: string, start: number, end: number, quote: string): MiniCEditResult {
    if (start === end && source.startsWith(quote, start)) {
      const caret = start + quote.length;
      return {
        source,
        replaceStart: start,
        replaceEnd: end,
        replacement: "",
        selectionStart: caret,
        selectionEnd: caret,
      };
    }
    return MiniCEditorTyping.wrapOrInsert(source, start, end, quote, quote);
  }

  static replace(source: string, start: number, end: number, replacement: string, caret: number): MiniCEditResult {
    return {
      source: source.slice(0, start) + replacement + source.slice(end),
      replaceStart: start,
      replaceEnd: end,
      replacement,
      selectionStart: caret,
      selectionEnd: caret,
    };
  }

  static isEmptyPair(opening: string, closing: string): boolean {
    return (opening === "(" && closing === ")")
      || (opening === "[" && closing === "]")
      || (opening === "{" && closing === "}")
      || (opening === "\"" && closing === "\"")
      || (opening === "'" && closing === "'");
  }

  private static safeSelection(source: string, selectionStart: number, selectionEnd: number): { readonly start: number; readonly end: number } {
    const start = clampNumber(Math.trunc(selectionStart), 0, source.length);
    const end = clampNumber(Math.trunc(selectionEnd), start, source.length);
    return { start, end };
  }

  summary(): string {
    return `MiniCEditorTyping: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCEditorTyping;
