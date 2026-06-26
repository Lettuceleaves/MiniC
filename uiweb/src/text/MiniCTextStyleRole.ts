import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCTextStyleRoleMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCTextStyleRole.java",
  webPath: "uiweb/src/text/MiniCTextStyleRole.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCTextStyleRole",
  kind: "enum",
  imports: [
    "java.util.List"
  ],
  fields: [
    {
      "name": "fallbackColorKey",
      "signature": "private final String fallbackColorKey"
    },
    {
      "name": "fallbackFontFamily",
      "signature": "private final String fallbackFontFamily"
    },
    {
      "name": "fallbackFontStyle",
      "signature": "private final String fallbackFontStyle"
    },
    {
      "name": "fallbackFontWeight",
      "signature": "private final String fallbackFontWeight"
    },
    {
      "name": "legacyClasses",
      "signature": "private final List<String>legacyClasses"
    },
    {
      "name": "themeId",
      "signature": "private final String themeId"
    }
  ],
  methods: [
    {
      "name": "cssClass",
      "signature": "cssClass()"
    },
    {
      "name": "fallbackColorKey",
      "signature": "fallbackColorKey()"
    },
    {
      "name": "fallbackFontFamily",
      "signature": "fallbackFontFamily()"
    },
    {
      "name": "fallbackFontStyle",
      "signature": "fallbackFontStyle()"
    },
    {
      "name": "fallbackFontWeight",
      "signature": "fallbackFontWeight()"
    },
    {
      "name": "legacyClasses",
      "signature": "legacyClasses()"
    },
    {
      "name": "themeId",
      "signature": "themeId()"
    }
  ],
} as const satisfies JavaMirrorFile;

export interface MiniCTextStyleRoleDefinition {
  readonly name: string;
  readonly themeId: string;
  readonly fallbackColorKey: string;
  readonly fallbackFontFamily: "ui" | "mono";
  readonly fallbackFontWeight: "normal" | "bold";
  readonly fallbackFontStyle: "normal" | "italic";
  readonly legacyClasses: readonly string[];
  readonly cssClass: string;
}

function role(
  name: string,
  themeId: string,
  fallbackColorKey: string,
  fallbackFontFamily: "ui" | "mono",
  fallbackFontWeight: "normal" | "bold",
  fallbackFontStyle: "normal" | "italic",
  legacyClasses: readonly string[] = [],
): MiniCTextStyleRoleDefinition {
  return {
    name,
    themeId,
    fallbackColorKey,
    fallbackFontFamily,
    fallbackFontWeight,
    fallbackFontStyle,
    legacyClasses,
    cssClass: `mc-text-${themeId.replaceAll(".", "-")}`,
  };
}

export const MiniCTextStyleRole = {
  BODY: role("BODY", "body", "text.body", "ui", "normal", "normal"),
  BODY_MONO: role("BODY_MONO", "body.mono", "text.body", "mono", "normal", "normal"),
  SECONDARY: role("SECONDARY", "secondary", "text.secondary", "ui", "normal", "normal"),
  MUTED: role("MUTED", "muted", "text.muted", "ui", "normal", "normal"),
  MUTED_ALT: role("MUTED_ALT", "muted.alt", "text.muted_alt", "ui", "normal", "normal"),
  LABEL: role("LABEL", "label", "text.label", "ui", "normal", "normal"),
  ACTIVE: role("ACTIVE", "active", "text.active", "ui", "normal", "normal"),
  EMPHASIS: role("EMPHASIS", "emphasis", "text.body", "ui", "normal", "italic"),
  PANEL_TITLE: role("PANEL_TITLE", "panel.title", "text.panel_title", "ui", "bold", "normal"),
  SECTION_LABEL: role("SECTION_LABEL", "section.label", "text.label", "ui", "bold", "normal"),
  STAGE_TITLE: role("STAGE_TITLE", "stage.title", "text.stage_title", "ui", "bold", "normal"),
  LINE_NUMBER: role("LINE_NUMBER", "line.number", "text.line_number", "mono", "normal", "normal"),
  DIAGNOSTIC_DETAIL: role("DIAGNOSTIC_DETAIL", "diagnostic.detail", "text.diagnostic_detail", "ui", "normal", "normal"),
  GRAPH_LABEL: role("GRAPH_LABEL", "graph.label", "graph.label", "mono", "normal", "normal"),
  CODE_PLAIN: role("CODE_PLAIN", "code.plain", "text.body", "mono", "normal", "normal", ["token-plain"]),
  CODE_KEYWORD: role("CODE_KEYWORD", "code.keyword", "syntax.keyword", "mono", "normal", "normal", ["token-keyword"]),
  CODE_IDENTIFIER: role("CODE_IDENTIFIER", "code.identifier", "text.body", "mono", "normal", "normal", ["token-identifier"]),
  CODE_CONTROL: role("CODE_CONTROL", "code.control", "syntax.control", "mono", "normal", "normal"),
  CODE_FUNCTION: role("CODE_FUNCTION", "code.function", "syntax.function", "mono", "normal", "normal"),
  CODE_VARIABLE: role("CODE_VARIABLE", "code.variable", "syntax.variable", "mono", "normal", "normal"),
  CODE_REGISTER: role("CODE_REGISTER", "code.register", "syntax.register", "mono", "normal", "normal"),
  CODE_LABEL: role("CODE_LABEL", "code.label", "syntax.label", "mono", "normal", "normal"),
  CODE_DIRECTIVE: role("CODE_DIRECTIVE", "code.directive", "syntax.directive", "mono", "normal", "normal"),
  CODE_STRING: role("CODE_STRING", "code.string", "syntax.string", "mono", "normal", "normal", ["token-string"]),
  CODE_LITERAL: role("CODE_LITERAL", "code.literal", "syntax.literal", "mono", "normal", "normal", ["token-literal"]),
  CODE_OPERATOR: role("CODE_OPERATOR", "code.operator", "syntax.operator", "mono", "normal", "normal", ["token-operator"]),
  CODE_PUNCTUATION: role("CODE_PUNCTUATION", "code.punctuation", "syntax.punctuation", "mono", "normal", "normal"),
  CODE_TYPE: role("CODE_TYPE", "code.type", "syntax.type", "mono", "normal", "normal"),
  CODE_COMMENT: role("CODE_COMMENT", "code.comment", "text.muted", "mono", "normal", "italic"),
} as const;

export type MiniCTextStyleRole =
  (typeof MiniCTextStyleRole)[keyof typeof MiniCTextStyleRole];

export const MINI_C_TEXT_STYLE_ROLES = Object.values(MiniCTextStyleRole);

export function isMiniCTextStyleRole(value: MiniCTextStyleRoleDefinition): value is MiniCTextStyleRole {
  return MINI_C_TEXT_STYLE_ROLES.some((roleValue) => roleValue.name === value.name);
}

export default MiniCTextStyleRole;
