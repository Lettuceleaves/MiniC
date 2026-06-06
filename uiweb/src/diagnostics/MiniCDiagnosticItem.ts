import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiSourceRangeDto } from "../translation/uiapi";

export const miniCDiagnosticItemMirror = {
  "javaPath": "src/main/java/minic/uilocal/diagnostics/MiniCDiagnosticItem.java",
  "webPath": "uiweb/src/diagnostics/MiniCDiagnosticItem.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCDiagnosticItem",
  "kind": "record",
  "imports": [
    "minic.uiapi.UiSourceRangeDto",
    "java.util.Objects"
  ],
  "fields": [],
  "methods": [
    {
      "name": "MiniCDiagnosticItem",
      "signature": "MiniCDiagnosticItem(String code, String severity, String message, UiSourceRangeDto range, int line, int column)"
    },
    {
      "name": "displayText",
      "signature": "displayText()"
    },
    {
      "name": "locationText",
      "signature": "locationText()"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCDiagnosticItem {
  static readonly mirror = miniCDiagnosticItemMirror;

  readonly mirror = miniCDiagnosticItemMirror;

  readonly code: string;

  readonly severity: string;

  readonly message: string;

  readonly range: UiSourceRangeDto | null;

  readonly line: number;

  readonly column: number;

  constructor(
    code: string,
    severity: string,
    message: string,
    range: UiSourceRangeDto | null,
    line = 1,
    column = range === null ? 1 : Math.max(1, range.startOffset + 1),
  ) {
    this.code = code;
    this.severity = severity;
    this.message = message;
    this.range = range;
    this.line = Math.max(1, Math.trunc(line));
    this.column = Math.max(1, Math.trunc(column));
  }

  displayText(): string {
    return `${this.severity}  ${this.code}  ${this.locationText()}  ${this.message}`;
  }

  locationText(): string {
    if (this.range === null) {
      return "<unknown>";
    }
    return `${this.range.sourceName}:${this.line}:${this.column}`;
  }

  summary(): string {
    return `MiniCDiagnosticItem: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCDiagnosticItem;
