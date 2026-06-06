import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCTextStyleStateMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCTextStyleState.java",
  webPath: "uiweb/src/text/MiniCTextStyleState.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCTextStyleState",
  kind: "enum",
  imports: [
    "java.util.List"
  ],
  fields: [
    {
      "name": "fallbackBackgroundKey",
      "signature": "private final String fallbackBackgroundKey"
    },
    {
      "name": "fallbackColorKey",
      "signature": "private final String fallbackColorKey"
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
      "name": "fallbackBackgroundKey",
      "signature": "fallbackBackgroundKey()"
    },
    {
      "name": "fallbackColorKey",
      "signature": "fallbackColorKey()"
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

export interface MiniCTextStyleStateDefinition {
  readonly name: string;
  readonly themeId: string;
  readonly fallbackColorKey: string | null;
  readonly fallbackBackgroundKey: string | null;
  readonly legacyClasses: readonly string[];
  readonly cssClass: string;
}

function state(
  name: string,
  themeId: string,
  fallbackColorKey: string | null,
  fallbackBackgroundKey: string | null,
  legacyClasses: readonly string[],
): MiniCTextStyleStateDefinition {
  return {
    name,
    themeId,
    fallbackColorKey,
    fallbackBackgroundKey,
    legacyClasses,
    cssClass: `mc-text-state-${themeId.replaceAll(".", "-")}`,
  };
}

export const MiniCTextStyleState = {
  ACTIVE: state("ACTIVE", "active", "text.active", null, ["active"]),
  SELECTED: state("SELECTED", "selected", "text.active", null, ["selected"]),
  FOCUSED: state("FOCUSED", "focused", "text.active", null, ["focus"]),
  HOT: state("HOT", "hot", "text.active", null, ["hot"]),
  DIAGNOSTIC: state("DIAGNOSTIC", "diagnostic", null, null, ["diagnostic"]),
  DEBUG_EXECUTION: state("DEBUG_EXECUTION", "debug.execution", "text.active", "background.running", ["debug-execution-range"]),
} as const;

export type MiniCTextStyleState =
  (typeof MiniCTextStyleState)[keyof typeof MiniCTextStyleState];

export const MINI_C_TEXT_STYLE_STATES = Object.values(MiniCTextStyleState);

export default MiniCTextStyleState;
