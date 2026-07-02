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
    "java.util.List",
    "java.util.Set",
    "minic.uiapi.UiLexerTokenVisualDto"
  ],
  fields: [
    {
      "name": "CONTROL_KINDS",
      "signature": "private static final Set<String>CONTROL_KINDS="
    },
    {
      "name": "KEYWORD_KINDS",
      "signature": "private static final Set<String>KEYWORD_KINDS="
    },
    {
      "name": "LITERAL_KINDS",
      "signature": "private static final Set<String>LITERAL_KINDS="
    },
    {
      "name": "PUNCTUATION_KINDS",
      "signature": "private static final Set<String>PUNCTUATION_KINDS="
    },
    {
      "name": "STRING_KINDS",
      "signature": "private static final Set<String>STRING_KINDS="
    },
    {
      "name": "TYPE_KINDS",
      "signature": "private static final Set<String>TYPE_KINDS="
    },
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
      "name": "roleForIdentifier",
      "signature": "roleForIdentifier(List<UiLexerTokenVisualDto>tokens,int index)"
    },
    {
      "name": "roleForToken",
      "signature": "roleForToken(List<UiLexerTokenVisualDto>tokens,int index)"
    },
    {
      "name": "kindAt",
      "signature": "kindAt(List<UiLexerTokenVisualDto>tokens,int index)"
    },
    {
      "name": "statesFor",
      "signature": "statesFor(boolean diagnostic)"
    },
    {
      "name": "styleClassesFor",
      "signature": "styleClassesFor(String tokenKind,boolean diagnostic)"
    },
    {
      "name": "styleClassesForToken",
      "signature": "styleClassesForToken(List<UiLexerTokenVisualDto>tokens,int index,boolean diagnostic)"
    }
  ],
} as const satisfies JavaMirrorFile;

const KEYWORD_KINDS = new Set([
  "EXTERN",
  "SIZEOF",
]);

const TYPE_KINDS = new Set([
  "BOOL",
  "CHAR",
  "INT",
  "LONG",
  "FLOAT",
  "DOUBLE",
  "STRUCT",
]);

const CONTROL_KINDS = new Set([
  "RETURN",
  "IF",
  "ELSE",
  "WHILE",
  "DO",
  "FOR",
  "BREAK",
  "CONTINUE",
  "SWITCH",
  "CASE",
  "DEFAULT",
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
const PUNCTUATION_KINDS = new Set([
  "LEFT_PAREN",
  "RIGHT_PAREN",
  "LEFT_BRACE",
  "RIGHT_BRACE",
  "LEFT_BRACKET",
  "RIGHT_BRACKET",
  "SEMICOLON",
  "COMMA",
  "DOT",
  "ELLIPSIS",
  "QUESTION",
  "COLON",
]);

export interface MiniCSyntaxTokenLike {
  readonly kind: string;
  readonly text?: string;
  readonly startOffset?: number;
  readonly endOffset?: number;
}

export class MiniCSyntaxTextStyleMapper {
  static readonly mirror = miniCSyntaxTextStyleMapperMirror;

  private readonly resolver: MiniCTextStyleResolver;

  constructor(resolver: MiniCTextStyleResolver = MiniCTextStyles.defaultResolver()) {
    this.resolver = resolver;
  }

  roleFor(tokenKind: string): MiniCTextStyleRoleValue {
    if (TYPE_KINDS.has(tokenKind) || CONTROL_KINDS.has(tokenKind) || KEYWORD_KINDS.has(tokenKind)) {
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

  roleForToken(tokens: readonly MiniCSyntaxTokenLike[], index: number): MiniCTextStyleRoleValue {
    if (index < 0 || index >= tokens.length) {
      return MiniCTextStyleRole.CODE_PLAIN;
    }
    const tokenKind = tokens[index].kind;
    if (TYPE_KINDS.has(tokenKind)) {
      return MiniCTextStyleRole.CODE_TYPE;
    }
    if (CONTROL_KINDS.has(tokenKind)) {
      return MiniCTextStyleRole.CODE_CONTROL;
    }
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
      return this.roleForIdentifier(tokens, index);
    }
    if (PUNCTUATION_KINDS.has(tokenKind)) {
      return MiniCTextStyleRole.CODE_PUNCTUATION;
    }
    if (tokenKind === "EOF") {
      return MiniCTextStyleRole.CODE_PLAIN;
    }
    return MiniCTextStyleRole.CODE_OPERATOR;
  }

  private roleForIdentifier(tokens: readonly MiniCSyntaxTokenLike[], index: number): MiniCTextStyleRoleValue {
    const previousKind = this.kindAt(tokens, index - 1);
    const nextKind = this.kindAt(tokens, index + 1);
    if (previousKind === "STRUCT") {
      return MiniCTextStyleRole.CODE_TYPE;
    }
    if (nextKind === "LEFT_PAREN") {
      return MiniCTextStyleRole.CODE_FUNCTION;
    }
    return MiniCTextStyleRole.CODE_VARIABLE;
  }

  private kindAt(tokens: readonly MiniCSyntaxTokenLike[], index: number): string {
    return index < 0 || index >= tokens.length ? "" : tokens[index].kind;
  }

  statesFor(diagnostic: boolean): readonly MiniCTextStyleStateValue[] {
    return diagnostic ? [MiniCTextStyleState.DIAGNOSTIC] : [];
  }

  styleClassesFor(tokenKind: string, diagnostic: boolean): readonly string[] {
    return this.resolver.styleClasses(this.roleFor(tokenKind), this.statesFor(diagnostic));
  }

  styleClassesForToken(
    tokens: readonly MiniCSyntaxTokenLike[],
    index: number,
    diagnostic: boolean,
  ): readonly string[] {
    return this.resolver.styleClasses(this.roleForToken(tokens, index), this.statesFor(diagnostic));
  }
}

export default MiniCSyntaxTextStyleMapper;
