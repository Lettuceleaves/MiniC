import type { JavaMirrorFile } from "../translation/javaMirror";
import type { MiniCTextStyleRole } from "./MiniCTextStyleRole";
import type { MiniCTextStyleState } from "./MiniCTextStyleState";
import { MiniCDefaultTextStyleResolver } from "./MiniCTextStyleResolver";
import type { MiniCResolvedTextStyle, MiniCTextStyleResolver } from "./MiniCTextStyleResolver";

export const miniCTextStylesMirror = {
  javaPath: "src/main/java/minic/uilocal/text/MiniCTextStyles.java",
  webPath: "uiweb/src/text/MiniCTextStyles.ts",
  packageName: "minic.uilocal.text",
  exportName: "MiniCTextStyles",
  kind: "class",
  imports: [
    "java.util.ArrayList",
    "java.util.Arrays",
    "java.util.Collection",
    "java.util.LinkedHashSet",
    "java.util.List",
    "javafx.scene.Node"
  ],
  fields: [
    {
      "name": "DEFAULT_RESOLVER",
      "signature": "private static final MiniCTextStyleResolver DEFAULT_RESOLVER="
    }
  ],
  methods: [
    {
      "name": "addStateClasses",
      "signature": "addStateClasses(Collection<String>target,MiniCTextStyleState... states)"
    },
    {
      "name": "apply",
      "signature": "apply(Node node,MiniCTextStyleRole role,MiniCTextStyleState... states)"
    },
    {
      "name": "classes",
      "signature": "classes(MiniCTextStyleRole role,MiniCTextStyleState... states)"
    },
    {
      "name": "defaultResolver",
      "signature": "defaultResolver()"
    },
    {
      "name": "stateClasses",
      "signature": "stateClasses(MiniCTextStyleState... states)"
    },
    {
      "name": "styleClasses",
      "signature": "styleClasses(MiniCTextStyleRole role,Collection<MiniCTextStyleState>states)"
    }
  ],
} as const satisfies JavaMirrorFile;

const DEFAULT_RESOLVER = new MiniCDefaultTextStyleResolver();

export class MiniCTextStyles {
  static readonly mirror = miniCTextStylesMirror;

  static defaultResolver(): MiniCTextStyleResolver {
    return DEFAULT_RESOLVER;
  }

  static classes(role: MiniCTextStyleRole, ...states: readonly MiniCTextStyleState[]): readonly string[] {
    return DEFAULT_RESOLVER.styleClasses(role, states);
  }

  static className(role: MiniCTextStyleRole, ...states: readonly MiniCTextStyleState[]): string {
    return this.classes(role, ...states).join(" ");
  }

  static stateClasses(...states: readonly MiniCTextStyleState[]): readonly string[] {
    return Array.from(new Set(states.flatMap((state) => [state.cssClass, ...state.legacyClasses])));
  }

  static addStateClasses(target: string[], ...states: readonly MiniCTextStyleState[]): void {
    target.push(...this.stateClasses(...states));
  }

  static resolve(role: MiniCTextStyleRole, ...states: readonly MiniCTextStyleState[]): MiniCResolvedTextStyle {
    return DEFAULT_RESOLVER.resolve(role, states);
  }
}

export default MiniCTextStyles;
