import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiSourceSpanDto } from "../translation/uiapi";

export const miniCHoverInspectorContentMirror = {
  "javaPath": "src/main/java/minic/uilocal/panel/MiniCHoverInspectorContent.java",
  "webPath": "uiweb/src/panel/MiniCHoverInspectorContent.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCHoverInspectorContent",
  "kind": "record",
  "imports": [
    "java.util.List",
    "java.util.Objects",
    "minic.uiapi.UiSourceSpanDto"
  ],
  "fields": [],
  "methods": [
    {
      "name": "empty",
      "signature": "empty()"
    },
    {
      "name": "emptyContent",
      "signature": "emptyContent()"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCHoverInspectorContent {
  static readonly mirror = miniCHoverInspectorContentMirror;

  readonly mirror = miniCHoverInspectorContentMirror;

  readonly title: string;

  readonly metadata: readonly string[];

  readonly source: string;

  readonly range: UiSourceSpanDto | null;

  readonly explanation: string;

  constructor(
    title: string,
    metadata: readonly string[],
    source: string,
    range: UiSourceSpanDto | null,
    explanation: string,
  ) {
    this.title = title;
    this.metadata = [...metadata];
    this.source = source;
    this.range = range;
    this.explanation = explanation;
  }

  static empty(): MiniCHoverInspectorContent {
    return new MiniCHoverInspectorContent("", [], "", null, "");
  }

  emptyContent(): boolean {
    return (
      this.title.trim() === "" &&
      this.metadata.length === 0 &&
      this.source.trim() === "" &&
      this.explanation.trim() === ""
    );
  }

  summary(): string {
    return `MiniCHoverInspectorContent: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCHoverInspectorContent;
