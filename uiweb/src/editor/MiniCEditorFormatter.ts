import { MiniCEditorTyping, type MiniCEditResult } from "./MiniCEditorTyping";
import type { JavaMirrorFile } from "../translation/javaMirror";
import { requireValue } from "../translation/uiTypes";

export const miniCEditorFormatterMirror = {
  "javaPath": "src/main/java/minic/uilocal/editor/MiniCEditorFormatter.java",
  "webPath": "uiweb/src/editor/MiniCEditorFormatter.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCEditorFormatter",
  "kind": "class",
  "imports": [
    "javafx.scene.input.KeyEvent",
    "org.fxmisc.richtext.StyleClassedTextArea"
  ],
  "fields": [
    {
      "name": "TAB_TEXT",
      "signature": "private static final String TAB_TEXT ="
    },
    {
      "name": "input",
      "signature": "private final StyleClassedTextArea input;"
    }
  ],
  "methods": [
    {
      "name": "applyEdit",
      "signature": "applyEdit(MiniCEditorTyping.EditResult result)"
    },
    {
      "name": "braceBalancedAfter",
      "signature": "braceBalancedAfter(String source, int from)"
    },
    {
      "name": "formatLine",
      "signature": "formatLine(String line)"
    },
    {
      "name": "formatOutsideLiterals",
      "signature": "formatOutsideLiterals(String text)"
    },
    {
      "name": "formatCodeSegment",
      "signature": "formatCodeSegment(String content)"
    },
    {
      "name": "leadingWhitespace",
      "signature": "leadingWhitespace(String text)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCEditorInput {
  getText(): string;
  selectionStart(): number;
  selectionEnd(): number;
  replaceText(start: number, end: number, replacement: string): void;
  selectRange(start: number, end: number): void;
}

export class MiniCEditorFormatter {
  static readonly mirror = miniCEditorFormatterMirror;

  static readonly TAB_TEXT = "    ";

  readonly mirror = miniCEditorFormatterMirror;

  private readonly input: MiniCEditorInput | null;

  constructor(input?: MiniCEditorInput | null) {
    this.input = input ?? null;
  }

  handleTypedText(text: string): MiniCEditResult | null {
    if (!this.input || text.length === 0 || text.charCodeAt(0) < 32) {
      return null;
    }
    const result = MiniCEditorTyping.type(
      this.input.getText(),
      this.input.selectionStart(),
      this.input.selectionEnd(),
      text,
    );
    this.applyEdit(result);
    return result;
  }

  insertTab(): void {
    if (!this.input) {
      return;
    }
    const start = this.input.selectionStart();
    const end = this.input.selectionEnd();
    this.applyEdit(MiniCEditorTyping.replace(
      this.input.getText(),
      start,
      end,
      MiniCEditorFormatter.TAB_TEXT,
      start + MiniCEditorFormatter.TAB_TEXT.length,
    ));
  }

  deleteBackward(): MiniCEditResult | null {
    if (!this.input) {
      return null;
    }
    const result = MiniCEditorTyping.backspace(
      this.input.getText(),
      this.input.selectionStart(),
      this.input.selectionEnd(),
    );
    this.applyEdit(result);
    return result;
  }

  insertNewlineWithIndentAt(source: string, caret: number): MiniCEditResult {
    const safeCaret = Math.max(0, Math.min(Math.trunc(caret), source.length));
    const lineStart = source.lastIndexOf("\n", Math.max(0, safeCaret - 1)) + 1;
    const lineBefore = source.slice(lineStart, safeCaret);
    const formatted = MiniCEditorFormatter.formatLine(lineBefore);
    const currentIndent = MiniCEditorFormatter.leadingWhitespace(formatted);
    const afterOpeningBrace = formatted.length > 0 && formatted[formatted.length - 1] === "{";
    const afterCaret = source.slice(safeCaret);
    const beforeClosingBrace = afterCaret.startsWith("}");
    const needsClosingBrace = afterOpeningBrace
      && !beforeClosingBrace
      && !MiniCEditorFormatter.braceBalancedAfter(source, safeCaret);
    let insertion: string;
    let cursorOffset: number;
    if (afterOpeningBrace && (beforeClosingBrace || needsClosingBrace)) {
      const innerIndent = currentIndent + MiniCEditorFormatter.TAB_TEXT;
      const closingPart = needsClosingBrace ? `\n${currentIndent}}` : `\n${currentIndent}`;
      insertion = `\n${innerIndent}${closingPart}`;
      cursorOffset = 1 + innerIndent.length;
    } else {
      const nextIndent = currentIndent + (afterOpeningBrace ? MiniCEditorFormatter.TAB_TEXT : "");
      insertion = `\n${nextIndent}`;
      cursorOffset = insertion.length;
    }
    const replacement = formatted + insertion;
    return MiniCEditorTyping.replace(source, lineStart, safeCaret, replacement, lineStart + formatted.length + cursorOffset);
  }

  insertNewlineWithIndent(): MiniCEditResult | null {
    if (!this.input) {
      return null;
    }
    const result = this.insertNewlineWithIndentAt(this.input.getText(), this.input.selectionStart());
    this.applyEdit(result);
    return result;
  }

  applyEdit(result: MiniCEditResult): void {
    const input = requireValue(this.input, "input");
    if (result.replacement.length > 0 || result.replaceStart !== result.replaceEnd) {
      input.replaceText(result.replaceStart, result.replaceEnd, result.replacement);
    }
    input.selectRange(result.selectionStart, result.selectionEnd);
  }

  static braceBalancedAfter(source: string, from: number): boolean {
    let depth = 1;
    for (let index = Math.max(0, from); index < source.length; index += 1) {
      const value = source[index];
      if (value === "{") {
        depth += 1;
      } else if (value === "}") {
        depth -= 1;
      }
      if (depth === 0) {
        return true;
      }
    }
    return false;
  }

  static formatLine(line: string): string {
    const indent = MiniCEditorFormatter.leadingWhitespace(line);
    const content = line.slice(indent.length).replace(/\s+$/u, "");
    if (content.length === 0) {
      return indent;
    }
    return indent + MiniCEditorFormatter.formatOutsideLiterals(content);
  }

  static formatOutsideLiterals(text: string): string {
    let result = "";
    let segment = "";
    let quote = "";
    let escaping = false;
    for (let index = 0; index < text.length; index += 1) {
      const value = text[index];
      if (quote.length > 0) {
        result += value;
        if (escaping) {
          escaping = false;
        } else if (value === "\\") {
          escaping = true;
        } else if (value === quote) {
          quote = "";
        }
        continue;
      }
      if (value === "\"" || value === "'") {
        result += MiniCEditorFormatter.formatCodeSegment(segment);
        segment = "";
        result += value;
        quote = value;
      } else {
        segment += value;
      }
    }
    result += MiniCEditorFormatter.formatCodeSegment(segment);
    return result.replace(/\s+/gu, " ").trim();
  }

  static formatCodeSegment(content: string): string {
    return content
      .replace(/\s+([,;\)\]\}])/gu, "$1")
      .replace(/([\(\[\{])\s+/gu, "$1")
      .replace(/\s*([+\-*/%<>=!&|]=?|==|!=|<=|>=|&&|\|\|)\s*/gu, " $1 ")
      .replace(/\s*,\s*/gu, ", ")
      .replace(/\)\s*\{/gu, ") {")
      .replace(/\b(if|for|while)\s*\(/gu, "$1 (")
      .replace(/\s+/gu, " ")
      .trim();
  }

  static leadingWhitespace(text: string): string {
    const match = /^[ \t]*/u.exec(text);
    return match?.[0] ?? "";
  }

  summary(): string {
    return `MiniCEditorFormatter: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCEditorFormatter;
