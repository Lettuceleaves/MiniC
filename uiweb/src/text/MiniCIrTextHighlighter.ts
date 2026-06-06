import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCLineTokenHighlighter } from "./MiniCLineTokenHighlighter";
import type { MiniCTokenClassifier } from "./MiniCLineTokenHighlighter";
import type { MiniCStyledTextSegment } from "./MiniCStyledTextSegment";
import { MiniCTextStyleRole } from "./MiniCTextStyleRole";
import type { MiniCTextStyleRole as MiniCTextStyleRoleValue } from "./MiniCTextStyleRole";

export const miniCIrTextHighlighterMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCIrTextHighlighter.java",
  webPath: "uiweb/src/text/MiniCIrTextHighlighter.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCIrTextHighlighter",
  kind: "class",
  imports: [
    "java.util.List",
    "java.util.Set"
  ],
  fields: [
    {
      "name": "KEYWORDS",
      "signature": "private static final Set<String>KEYWORDS="
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
    }
  ],
} as const satisfies JavaMirrorFile;

const KEYWORDS = new Set([
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
  "offset",
  "check_nonzero",
  "cast",
  "to",
  "call",
  "call*",
  "branch",
  "jump",
  "return",
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

const NUMBER = /^[-+]?0x[0-9A-Fa-f]+$|^[-+]?[0-9]+(?:\.[0-9]+)?$/;
const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_.$]*$/;

export class MiniCIrTextHighlighter implements MiniCTokenClassifier {
  static readonly mirror = miniCIrTextHighlighterMirror;

  highlight(line: string | null | undefined): readonly MiniCStyledTextSegment[] {
    return MiniCLineTokenHighlighter.highlight(line, this);
  }

  roleFor(token: string): MiniCTextStyleRoleValue {
    const normalized = token.toLowerCase();
    if (KEYWORDS.has(normalized)) {
      return MiniCTextStyleRole.CODE_KEYWORD;
    }
    if (NUMBER.test(token)) {
      return MiniCTextStyleRole.CODE_LITERAL;
    }
    if (token.startsWith("%") || token.startsWith("$") || token.startsWith("&") || IDENTIFIER.test(token)) {
      return MiniCTextStyleRole.CODE_IDENTIFIER;
    }
    return MiniCTextStyleRole.CODE_OPERATOR;
  }
}

export default MiniCIrTextHighlighter;
