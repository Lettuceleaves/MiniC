import type { JavaMirrorFile } from "../translation/javaMirror";
import guideMarkdown from "../../../docs/GUIDE.md?raw";

export const miniCGuideDocumentMirror = {
  "javaPath": "src/main/java/minic/uilocal/info/MiniCGuideDocument.java",
  "webPath": "uiweb/src/info/MiniCGuideDocument.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCGuideDocument",
  "kind": "class",
  "imports": [
    "java.io.IOException",
    "java.nio.charset.StandardCharsets",
    "java.nio.file.Files",
    "java.nio.file.Path",
    "java.util.LinkedHashMap",
    "java.util.Map"
  ],
  "fields": [
    {
      "name": "DEFAULT_GUIDE",
      "signature": "private static final Path DEFAULT_GUIDE="
    },
    {
      "name": "DEFAULT_VERSION",
      "signature": "private static final String DEFAULT_VERSION="
    }
  ],
  "methods": [
    {
      "name": "appVersion",
      "signature": "appVersion()"
    },
    {
      "name": "property",
      "signature": "property(String key)"
    },
    {
      "name": "readGuide",
      "signature": "readGuide(Path guidePath)"
    },
    {
      "name": "runtimeVariables",
      "signature": "runtimeVariables()"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCGuideDocument {
  static readonly mirror = miniCGuideDocumentMirror;
  static readonly DEFAULT_GUIDE = "docs/GUIDE.md";
  static readonly DEFAULT_VERSION = "1.0.0";

  readonly mirror = miniCGuideDocumentMirror;

  static defaultGuidePath(): string {
    return MiniCGuideDocument.DEFAULT_GUIDE;
  }

  static loadDefault(): string {
    return MiniCGuideDocument.load(MiniCGuideDocument.DEFAULT_GUIDE);
  }

  static load(guidePath = MiniCGuideDocument.DEFAULT_GUIDE): string {
    let markdown = MiniCGuideDocument.readGuide(guidePath);
    for (const [key, value] of Object.entries(MiniCGuideDocument.runtimeVariables())) {
      markdown = markdown.replaceAll(`{{${key}}}`, value);
    }
    return markdown;
  }

  static readGuide(guidePath: string): string {
    if (guidePath === MiniCGuideDocument.DEFAULT_GUIDE) {
      return guideMarkdown;
    }
    return `# MiniC 使用指南\n\nGUIDE.md 未找到: \`${guidePath}\`\n`;
  }

  static runtimeVariables(): Record<string, string> {
    return {
      "app.version": MiniCGuideDocument.appVersion(),
      "java.version": "browser",
      "java.vendor": "browser",
      "system.os.name": MiniCGuideDocument.property("platform"),
      "system.os.version": "browser",
      "system.os.arch": MiniCGuideDocument.property("userAgent"),
      "system.processors": String(navigator.hardwareConcurrency || 1),
    };
  }

  static appVersion(): string {
    return MiniCGuideDocument.DEFAULT_VERSION;
  }

  static property(key: string): string {
    const value = key === "platform" ? navigator.platform : key === "userAgent" ? navigator.userAgent : "";
    return value.trim().length === 0 ? "unknown" : value;
  }

  summary(): string {
    return `MiniCGuideDocument: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCGuideDocument;
