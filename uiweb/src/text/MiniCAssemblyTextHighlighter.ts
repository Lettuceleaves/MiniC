import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCLineTokenHighlighter } from "./MiniCLineTokenHighlighter";
import type { MiniCTokenClassifier } from "./MiniCLineTokenHighlighter";
import type { MiniCStyledTextSegment } from "./MiniCStyledTextSegment";
import { MiniCTextStyleRole } from "./MiniCTextStyleRole";
import type { MiniCTextStyleRole as MiniCTextStyleRoleValue } from "./MiniCTextStyleRole";

export const miniCAssemblyTextHighlighterMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCAssemblyTextHighlighter.java",
  webPath: "uiweb/src/text/MiniCAssemblyTextHighlighter.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCAssemblyTextHighlighter",
  kind: "class",
  imports: [
    "java.util.ArrayList",
    "java.util.List",
    "java.util.Set"
  ],
  fields: [
    {
      "name": "DIRECTIVES",
      "signature": "private static final Set<String>DIRECTIVES="
    },
    {
      "name": "MNEMONICS",
      "signature": "private static final Set<String>MNEMONICS="
    },
    {
      "name": "REGISTERS",
      "signature": "private static final Set<String>REGISTERS="
    }
  ],
  methods: [
    {
      "name": "highlight",
      "signature": "highlight(String line)"
    },
    {
      "name": "isIdentifier",
      "signature": "isIdentifier(String token)"
    },
    {
      "name": "isNumber",
      "signature": "isNumber(String token)"
    },
    {
      "name": "roleFor",
      "signature": "roleFor(String token,int startOffset,String fullLine)"
    },
    {
      "name": "startsLabel",
      "signature": "startsLabel(String token,int startOffset,String fullLine)"
    }
  ],
} as const satisfies JavaMirrorFile;

const MNEMONICS = new Set([
  "mov",
  "movsx",
  "movzx",
  "lea",
  "push",
  "pop",
  "sub",
  "add",
  "imul",
  "idiv",
  "cqo",
  "xor",
  "or",
  "and",
  "not",
  "neg",
  "shl",
  "sar",
  "cmp",
  "sete",
  "setne",
  "setl",
  "setle",
  "setg",
  "setge",
  "jmp",
  "je",
  "jne",
  "jl",
  "jle",
  "jg",
  "jge",
  "call",
  "ret",
  "leave",
  "test",
]);

const REGISTERS = new Set([
  "rax",
  "rbx",
  "rcx",
  "rdx",
  "rsi",
  "rdi",
  "rbp",
  "rsp",
  "r8",
  "r9",
  "r10",
  "r11",
  "r12",
  "r13",
  "r14",
  "r15",
  "eax",
  "ebx",
  "ecx",
  "edx",
  "esi",
  "edi",
  "ebp",
  "esp",
  "r8d",
  "r9d",
  "r10d",
  "r11d",
  "r12d",
  "r13d",
  "r14d",
  "r15d",
  "ax",
  "bx",
  "cx",
  "dx",
  "al",
  "bl",
  "cl",
  "dl",
  "xmm0",
  "xmm1",
  "xmm2",
  "xmm3",
  "xmm4",
  "xmm5",
]);

const DIRECTIVES = new Set([
  "proc",
  "endp",
  "public",
  "extern",
  "extrn",
  "data",
  "code",
  "const",
  "segment",
  "ends",
  "db",
  "dw",
  "dd",
  "dq",
  "qword",
  "dword",
  "word",
  "byte",
  "ptr",
  "offset",
  "flat",
]);

const IDENTIFIER = /^[A-Za-z_.$][A-Za-z0-9_.$]*$/;
const NUMBER = /^[-+]?0x[0-9A-Fa-f]+$|^[-+]?[0-9]+(?:\.[0-9]+)?$/;

export class MiniCAssemblyTextHighlighter implements MiniCTokenClassifier {
  static readonly mirror = miniCAssemblyTextHighlighterMirror;

  highlight(line: string | null | undefined): readonly MiniCStyledTextSegment[] {
    const text = line && line.length > 0 ? line : " ";
    const commentStart = text.indexOf(";");
    if (commentStart < 0) {
      return MiniCLineTokenHighlighter.highlight(text, this);
    }
    const segments = [...MiniCLineTokenHighlighter.highlight(text.slice(0, commentStart), this)];
    MiniCLineTokenHighlighter.add(segments, text.slice(commentStart), MiniCTextStyleRole.CODE_COMMENT);
    return segments;
  }

  roleFor(token: string, startOffset: number, fullLine: string): MiniCTextStyleRoleValue {
    const normalized = token.toLowerCase();
    if (this.startsLabel(token, startOffset, fullLine) || token.startsWith("$") || token.startsWith(".")) {
      return MiniCTextStyleRole.CODE_TYPE;
    }
    if (MNEMONICS.has(normalized)) {
      return MiniCTextStyleRole.CODE_KEYWORD;
    }
    if (REGISTERS.has(normalized)) {
      return MiniCTextStyleRole.CODE_IDENTIFIER;
    }
    if (DIRECTIVES.has(normalized)) {
      return MiniCTextStyleRole.CODE_TYPE;
    }
    if (NUMBER.test(token)) {
      return MiniCTextStyleRole.CODE_LITERAL;
    }
    if (IDENTIFIER.test(token)) {
      return MiniCTextStyleRole.CODE_IDENTIFIER;
    }
    return MiniCTextStyleRole.CODE_OPERATOR;
  }

  private startsLabel(token: string, startOffset: number, fullLine: string): boolean {
    return fullLine.at(startOffset + token.length) === ":";
  }
}

export default MiniCAssemblyTextHighlighter;
