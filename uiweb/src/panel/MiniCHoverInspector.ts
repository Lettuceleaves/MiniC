import type { JavaMirrorFile } from "../translation/javaMirror";
import { MiniCHoverInspectorContent } from "./MiniCHoverInspectorContent";

export const miniCHoverInspectorMirror = {
  "javaPath": "src/main/java/minic/uilocal/panel/MiniCHoverInspector.java",
  "webPath": "uiweb/src/panel/MiniCHoverInspector.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCHoverInspector",
  "kind": "class",
  "imports": [
    "javafx.beans.property.ObjectProperty",
    "javafx.beans.property.ReadOnlyObjectProperty",
    "javafx.beans.property.SimpleObjectProperty",
    "java.util.Objects"
  ],
  "fields": [
    {
      "name": "content",
      "signature": "private final ObjectProperty<MiniCHoverInspectorContent> content ="
    }
  ],
  "methods": [
    {
      "name": "contentProperty",
      "signature": "contentProperty()"
    },
    {
      "name": "show",
      "signature": "show(MiniCHoverInspectorContent content)"
    },
    {
      "name": "clear",
      "signature": "clear()"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCHoverInspector {
  static readonly mirror = miniCHoverInspectorMirror;

  readonly mirror = miniCHoverInspectorMirror;

  private content = MiniCHoverInspectorContent.empty();

  private readonly listeners = new Set<(content: MiniCHoverInspectorContent) => void>();

  contentProperty(): MiniCHoverInspectorContent {
    return this.content;
  }

  show(content: MiniCHoverInspectorContent): void {
    this.setContent(content);
  }

  clear(): void {
    this.setContent(MiniCHoverInspectorContent.empty());
  }

  subscribe(listener: (content: MiniCHoverInspectorContent) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  summary(): string {
    return `MiniCHoverInspector: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }

  private setContent(content: MiniCHoverInspectorContent): void {
    this.content = content;
    this.listeners.forEach((listener) => listener(content));
  }
}

export default MiniCHoverInspector;
