import type { JavaMirrorFile } from "../translation/javaMirror";
import type { MiniCTextStyleRole } from "./MiniCTextStyleRole";
import type { MiniCTextStyleState } from "./MiniCTextStyleState";

export const miniCTextStyleResolverMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCTextStyleResolver.java",
  webPath: "uiweb/src/text/MiniCTextStyleResolver.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCTextStyleResolver",
  kind: "interface",
  imports: [
    "java.util.Collection"
  ],
  fields: [],
  methods: [],
} as const satisfies JavaMirrorFile;

export interface MiniCResolvedTextStyle {
  readonly classNames: readonly string[];
  readonly cssProperties: Readonly<Record<string, string>>;
}

export interface MiniCTextStyleResolver {
  styleClasses(role: MiniCTextStyleRole, states?: readonly MiniCTextStyleState[]): readonly string[];
  resolve(role: MiniCTextStyleRole, states?: readonly MiniCTextStyleState[]): MiniCResolvedTextStyle;
}

function unique(values: readonly string[]): readonly string[] {
  return Array.from(new Set(values.filter((value) => value.length > 0)));
}

export class MiniCDefaultTextStyleResolver implements MiniCTextStyleResolver {
  styleClasses(role: MiniCTextStyleRole, states: readonly MiniCTextStyleState[] = []): readonly string[] {
    return unique([
      role.cssClass,
      ...role.legacyClasses,
      ...states.flatMap((state) => [state.cssClass, ...state.legacyClasses]),
    ]);
  }

  resolve(role: MiniCTextStyleRole, states: readonly MiniCTextStyleState[] = []): MiniCResolvedTextStyle {
    const cssProperties: Record<string, string> = {};
    cssProperties["--mc-text-color-key"] = role.fallbackColorKey;
    cssProperties.fontFamily = role.fallbackFontFamily === "mono"
      ? "Consolas, 'JetBrains Mono', monospace"
      : "'Segoe UI', Arial, sans-serif";
    cssProperties.fontWeight = role.fallbackFontWeight;
    cssProperties.fontStyle = role.fallbackFontStyle;

    for (const state of states) {
      if (state.fallbackColorKey) {
        cssProperties["--mc-text-state-color-key"] = state.fallbackColorKey;
      }
      if (state.fallbackBackgroundKey) {
        cssProperties["--mc-text-state-background-key"] = state.fallbackBackgroundKey;
      }
    }

    return {
      classNames: this.styleClasses(role, states),
      cssProperties,
    };
  }
}

export default MiniCDefaultTextStyleResolver;
