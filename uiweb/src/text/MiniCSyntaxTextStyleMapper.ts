import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCTextStyleRole } from "./MiniCTextStyleRole";
import type { MiniCTextStyleRole as MiniCTextStyleRoleValue } from "./MiniCTextStyleRole";
import { MiniCTextStyleState } from "./MiniCTextStyleState";
import type { MiniCTextStyleState as MiniCTextStyleStateValue } from "./MiniCTextStyleState";
import { MiniCTextStyles } from "./MiniCTextStyles";
import type { MiniCTextStyleResolver } from "./MiniCTextStyleResolver";

export const miniCSyntaxTextStyleMapperMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCSyntaxTextStyleMapper.java",
  webPath: "uiweb/src/text/MiniCSyntaxTextStyleMapper.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCSyntaxTextStyleMapper",
  kind: "class",
  imports: [
    "java.util.Collection",
    "java.util.List"
  ],
  fields: [
    {
      "name": "resolver",
      "signature": "private final MiniCTextStyleResolver resolver"
    }
  ],
  methods: [
    {
      "name": "roleFor",
      "signature": "roleFor(String tokenKind)"
    },
    {
      "name": "statesFor",
      "signature": "statesFor(boolean diagnostic)"
    },
    {
      "name": "styleClassesFor",
      "signature": "styleClassesFor(String tokenKind,boolean diagnostic)"
    }
  ],
} as const satisfies JavaMirrorFile;

const KEYWORD_KINDS = new Set([
  "BOOL",
  "CHAR",
  "INT",
  "LONG",
  "FLOAT",
  "DOUBLE",
  "EXTERN",
  "STRUCT",
  "RETURN",
  "IF",
  "ELSE",
  "WHILE",
  "FOR",
  "BREAK",
  "CONTINUE",
]);

const STRING_KINDS = new Set(["STRING_LITERAL", "CHAR_LITERAL"]);
const LITERAL_KINDS = new Set([
  "INTEGER_LITERAL",
  "LONG_LITERAL",
  "FLOAT_LITERAL",
  "DOUBLE_LITERAL",
  "BOOL_LITERAL",
  "NULL_LITERAL",
]);

export class MiniCSyntaxTextStyleMapper {
  static readonly mirror = miniCSyntaxTextStyleMapperMirror;

  private readonly resolver: MiniCTextStyleResolver;

  constructor(resolver: MiniCTextStyleResolver = MiniCTextStyles.defaultResolver()) {
    this.resolver = resolver;
  }

  roleFor(tokenKind: string): MiniCTextStyleRoleValue {
    if (KEYWORD_KINDS.has(tokenKind)) {
      return MiniCTextStyleRole.CODE_KEYWORD;
    }
    if (STRING_KINDS.has(tokenKind)) {
      return MiniCTextStyleRole.CODE_STRING;
    }
    if (LITERAL_KINDS.has(tokenKind)) {
      return MiniCTextStyleRole.CODE_LITERAL;
    }
    if (tokenKind === "IDENTIFIER") {
      return MiniCTextStyleRole.CODE_IDENTIFIER;
    }
    return MiniCTextStyleRole.CODE_OPERATOR;
  }

  statesFor(diagnostic: boolean): readonly MiniCTextStyleStateValue[] {
    return diagnostic ? [MiniCTextStyleState.DIAGNOSTIC] : [];
  }

  styleClassesFor(tokenKind: string, diagnostic: boolean): readonly string[] {
    return this.resolver.styleClasses(this.roleFor(tokenKind), this.statesFor(diagnostic));
  }
}

export default MiniCSyntaxTextStyleMapper;
