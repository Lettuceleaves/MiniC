import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCLineTokenHighlighter } from "./MiniCLineTokenHighlighter";
import type { MiniCStyledTextSegment } from "./MiniCStyledTextSegment";
import { MiniCTextStyleRole } from "./MiniCTextStyleRole";
import type { MiniCTextStyleRole as MiniCTextStyleRoleValue } from "./MiniCTextStyleRole";

export const miniCExplanationTextHighlighterMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCExplanationTextHighlighter.java",
  webPath: "uiweb/src/text/MiniCExplanationTextHighlighter.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCExplanationTextHighlighter",
  kind: "class",
  imports: ["java.util.Locale", "java.util.List", "java.util.Set", "java.util.regex.Pattern"],
  fields: [{ name: "TOKEN", signature: "private static final Pattern TOKEN =" }],
  methods: [{ name: "highlight", signature: "highlight(String explanation)" }],
} as const satisfies JavaMirrorFile;

const TOKEN =
  /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[-+]?0x[0-9A-Fa-f]+|[-+]?[0-9]+(?:\.[0-9]+)?|==|!=|<=|>=|->|&&|\|\||[%@$&.]?[A-Za-z0-9_][A-Za-z0-9_.$]*|[-+*/%=&|!<>^~.,:;()[\]{}@]/g;
const NUMBER = /^[-+]?0x[0-9A-Fa-f]+$|^[-+]?[0-9]+(?:\.[0-9]+)?$/;
const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_.$]*$/;
const PREFIXED_IDENTIFIER = /^[%@$&.][A-Za-z0-9_][A-Za-z0-9_.$]*$/;
const OPERATOR = /^(==|!=|<=|>=|->|&&|\|\||[-+*/%=&|!<>^~.,:;()[\]{}@])$/;

const C_KEYWORDS = new Set(["extern", "return", "if", "else", "while", "for", "break", "continue", "sizeof", "switch", "case", "default", "do", "goto"]);
const TYPE_NAMES = new Set(["void", "bool", "char", "int", "long", "float", "double", "struct", "ir", "asm", "ast", "token", "qword", "dword", "word", "byte", "ptr", "offset"]);
const LITERALS = new Set(["true", "false", "null", "nullptr"]);
const IR_KEYWORDS = new Set([
  "function",
  "block",
  "declare",
  "check_initialized",
  "address_of",
  "load",
  "store",
  "load_ptr",
  "store_ptr",
  "element_address",
  "field_address",
  "size",
  "check_nonzero",
  "cast",
  "to",
  "call",
  "branch",
  "jump",
  "add",
  "subtract",
  "multiply",
  "divide",
  "modulo",
  "bitwise_and",
  "bitwise_or",
  "bitwise_xor",
  "shift_left",
  "shift_right",
  "logical_and",
  "logical_or",
  "equal",
  "not_equal",
  "less_than",
  "less_equal",
  "greater_than",
  "greater_equal",
]);
const ASM_MNEMONICS = new Set(["mov", "movsx", "movzx", "lea", "push", "pop", "sub", "add", "imul", "idiv", "cqo", "xor", "or", "and", "not", "neg", "shl", "sar", "cmp", "sete", "setne", "setl", "setle", "setg", "setge", "jmp", "je", "jne", "jl", "jle", "jg", "jge", "call", "ret", "leave", "test"]);
const ASM_REGISTERS = new Set(["rax", "rbx", "rcx", "rdx", "rsi", "rdi", "rbp", "rsp", "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15", "eax", "ebx", "ecx", "edx", "esi", "edi", "ebp", "esp", "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d", "ax", "bx", "cx", "dx", "al", "bl", "cl", "dl", "xmm0", "xmm1", "xmm2", "xmm3", "xmm4", "xmm5"]);

export class MiniCExplanationTextHighlighter {
  static readonly mirror = miniCExplanationTextHighlighterMirror;

  highlight(explanation: string | null | undefined): readonly MiniCStyledTextSegment[] {
    const text = explanation && explanation.length > 0 ? explanation : " ";
    const segments: MiniCStyledTextSegment[] = [];
    let cursor = 0;
    for (const match of text.matchAll(TOKEN)) {
      const token = match[0];
      const start = match.index ?? 0;
      if (start > cursor) {
        MiniCLineTokenHighlighter.add(segments, text.slice(cursor, start), MiniCTextStyleRole.BODY);
      }
      MiniCLineTokenHighlighter.add(segments, token, this.roleFor(token, start, text));
      cursor = start + token.length;
    }
    if (cursor < text.length) {
      MiniCLineTokenHighlighter.add(segments, text.slice(cursor), MiniCTextStyleRole.BODY);
    }
    return segments;
  }

  private roleFor(token: string, startOffset: number, fullText: string): MiniCTextStyleRoleValue {
    const normalized = token.toLowerCase();
    if (this.isQuoted(token)) {
      return MiniCTextStyleRole.CODE_STRING;
    }
    if (NUMBER.test(token) || LITERALS.has(normalized)) {
      return MiniCTextStyleRole.CODE_LITERAL;
    }
    if (TYPE_NAMES.has(normalized)) {
      return MiniCTextStyleRole.CODE_TYPE;
    }
    if (C_KEYWORDS.has(normalized) || IR_KEYWORDS.has(normalized) || ASM_MNEMONICS.has(normalized)) {
      return MiniCTextStyleRole.CODE_KEYWORD;
    }
    if (ASM_REGISTERS.has(normalized) || PREFIXED_IDENTIFIER.test(token)) {
      return token.startsWith(".") || token.startsWith("$") ? MiniCTextStyleRole.CODE_TYPE : MiniCTextStyleRole.CODE_IDENTIFIER;
    }
    if (IDENTIFIER.test(token)) {
      if (this.isLabel(token, startOffset, fullText)) {
        return MiniCTextStyleRole.CODE_TYPE;
      }
      return this.isLikelyCodeIdentifier(token, startOffset, fullText) ? MiniCTextStyleRole.CODE_IDENTIFIER : MiniCTextStyleRole.BODY;
    }
    if (OPERATOR.test(token)) {
      return MiniCTextStyleRole.CODE_OPERATOR;
    }
    return MiniCTextStyleRole.BODY;
  }

  private isQuoted(token: string): boolean {
    return token.length >= 2
      && ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'")));
  }

  private isLabel(token: string, startOffset: number, fullText: string): boolean {
    const next = this.nextNonWhitespace(fullText, startOffset + token.length);
    return next >= 0 && fullText.at(next) === ":";
  }

  private isLikelyCodeIdentifier(token: string, startOffset: number, fullText: string): boolean {
    if (token.length === 1 || token.includes("_") || token.includes(".") || /\d/.test(token)) {
      return true;
    }
    const previous = this.previousNonWhitespace(fullText, startOffset - 1);
    const next = this.nextNonWhitespace(fullText, startOffset + token.length);
    return this.isIdentifierBoundary(previous < 0 ? "" : fullText.charAt(previous))
      || this.isIdentifierBoundary(next < 0 ? "" : fullText.charAt(next));
  }

  private previousNonWhitespace(text: string, index: number): number {
    for (let cursor = index; cursor >= 0; cursor--) {
      if (!/\s/.test(text.charAt(cursor))) {
        return cursor;
      }
    }
    return -1;
  }

  private nextNonWhitespace(text: string, index: number): number {
    for (let cursor = index; cursor < text.length; cursor++) {
      if (!/\s/.test(text.charAt(cursor))) {
        return cursor;
      }
    }
    return -1;
  }

  private isIdentifierBoundary(value: string): boolean {
    return value.length > 0 && "=+-*/%&|!<>^~()[]{}@".includes(value);
  }
}

export default MiniCExplanationTextHighlighter;
