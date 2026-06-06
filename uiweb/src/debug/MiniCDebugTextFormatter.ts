import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiDebugBreakpointDto } from "../translation/uiapi";

export const miniCDebugTextFormatterMirror = {
  "javaPath": "src/main/java/minic/uilocal/debug/MiniCDebugTextFormatter.java",
  "webPath": "uiweb/src/debug/MiniCDebugTextFormatter.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCDebugTextFormatter",
  "kind": "class",
  "imports": [
    "minic.uiapi.UiDebugBreakpointDto",
    "minic.uiapi.UiDebugEventDto",
    "minic.uiapi.UiDebugFrameDto",
    "minic.uiapi.UiDebugTimelineItemDto",
    "minic.uiapi.UiDebugVariableDto",
    "minic.uiapi.UiDebugVisualElementDto",
    "minic.uiapi.UiSourceSpanDto",
    "java.util.ArrayList",
    "java.util.List",
    "java.util.Map"
  ],
  "fields": [],
  "methods": [
    {
      "name": "addVariableLines",
      "signature": "addVariableLines(List<String> lines, UiDebugVariableDto variable, int depth)"
    },
    {
      "name": "variableTreeText",
      "signature": "variableTreeText(List<UiDebugVariableDto> variables)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCDebugTextFormatter {
  static readonly mirror = miniCDebugTextFormatterMirror;

  readonly mirror = miniCDebugTextFormatterMirror;

  frameText(frame: string | null | undefined): string {
    return frame && frame.trim().length > 0 ? frame : this.emptyText("frame");
  }

  frameWithValuesText(frame: string, values: readonly string[]): string {
    return [this.frameText(frame), ...values.map((value) => `  ${value}`)].join("\n");
  }

  stackLines(lines: readonly string[]): readonly string[] {
    return lines.length > 0 ? [...lines] : [this.emptyText("stack")];
  }

  variableLines(lines: readonly string[]): readonly string[] {
    return lines.length > 0 ? [...lines] : [this.emptyText("variables")];
  }

  breakpointText(breakpoint: UiDebugBreakpointDto): string {
    return `${breakpoint.enabled ? "●" : "○"} line ${breakpoint.line}`;
  }

  eventText(event: string): string {
    return event.trim().length === 0 ? this.emptyText("event") : event;
  }

  timelineText(timeline: readonly string[]): string {
    return timeline.length === 0 ? this.emptyText("timeline") : timeline.join("\n");
  }

  visualElementText(row: string): string {
    return row.trim().length === 0 ? " " : row;
  }

  emptyText(label: string): string {
    return `暂无 ${label}`;
  }

  rangeText(startOffset: number, endOffset: number): string {
    return `${startOffset}-${endOffset}`;
  }

  variableText(name: string, value: string): string {
    return `${name}: ${value}`;
  }

  addVariableLines(lines: string[], variables: readonly string[]): void {
    lines.push(...variables);
  }

  variableTreeText(lines: readonly string[]): string {
    return this.variableLines(lines).join("\n");
  }

  summary(): string {
    return `MiniCDebugTextFormatter: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCDebugTextFormatter;
