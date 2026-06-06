import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiSourceRangeDto } from "../translation/uiapi";
import type { MiniCDiagnosticItem } from "./MiniCDiagnosticItem";

export const miniCDiagnosticSelectionMirror = {
  "javaPath": "src/main/java/minic/uilocal/diagnostics/MiniCDiagnosticSelection.java",
  "webPath": "uiweb/src/diagnostics/MiniCDiagnosticSelection.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCDiagnosticSelection",
  "kind": "class",
  "imports": [
    "javafx.beans.property.ReadOnlyObjectProperty",
    "javafx.beans.property.ReadOnlyObjectWrapper",
    "minic.uiapi.UiSourceRangeDto"
  ],
  "fields": [
    {
      "name": "selectedRange",
      "signature": "private final ReadOnlyObjectWrapper<UiSourceRangeDto> selectedRange ="
    }
  ],
  "methods": [
    {
      "name": "select",
      "signature": "select(MiniCDiagnosticItem item)"
    },
    {
      "name": "selectedRangeProperty",
      "signature": "selectedRangeProperty()"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCDiagnosticSelection {
  static readonly mirror = miniCDiagnosticSelectionMirror;

  readonly mirror = miniCDiagnosticSelectionMirror;

  private selectedRange: UiSourceRangeDto | null = null;

  private readonly listeners = new Set<(range: UiSourceRangeDto | null) => void>();

  select(item: MiniCDiagnosticItem | null): void {
    this.setSelectedRange(item === null ? null : item.range);
  }

  selectedRangeProperty(): UiSourceRangeDto | null {
    return this.selectedRange;
  }

  subscribe(listener: (range: UiSourceRangeDto | null) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  clear(): void {
    this.setSelectedRange(null);
  }

  summary(): string {
    return `MiniCDiagnosticSelection: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }

  private setSelectedRange(range: UiSourceRangeDto | null): void {
    this.selectedRange = range;
    this.listeners.forEach((listener) => listener(range));
  }
}

export default MiniCDiagnosticSelection;
