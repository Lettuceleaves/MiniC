import type { JavaMirrorFile } from "../translation/javaMirror";
import type {
  UiDebugBreakpointDto,
  UiDebugEventDto,
  UiDebugFrameDto,
  UiDebugTimelineItemDto,
  UiDebugVariableDto,
  UiDebugVisualElementDto,
  UiSourceSpanDto,
} from "../translation/uiapi";

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

  frameText(frame: UiDebugFrameDto | null | undefined): string {
    if (frame === null || frame === undefined) {
      return this.emptyText("frame");
    }
    return `  ${frame.functionName} return=${frame.returnTarget ?? ""} active=${this.rangeText(frame.activeRange)}`;
  }

  frameWithValuesText(frame: UiDebugFrameDto): string {
    const parameters = this.variableTreeText(frame.parameters);
    const locals = this.variableTreeText(frame.locals);
    return `${this.frameText(frame)} params=[${parameters}] locals=[${locals}]`;
  }

  stackLines(frames: readonly UiDebugFrameDto[]): readonly string[] {
    const lines: string[] = [];
    for (const frame of frames) {
      lines.push(this.frameText(frame));
      if (frame.parameters.length > 0) {
        lines.push("  parameters:");
        lines.push(...this.variableLines(frame.parameters));
      }
      if (frame.locals.length > 0) {
        lines.push("  locals:");
        lines.push(...this.variableLines(frame.locals));
      }
    }
    return lines;
  }

  variableLines(variables: readonly UiDebugVariableDto[]): readonly string[] {
    const lines: string[] = [];
    for (const variable of variables) {
      this.addVariableLines(lines, variable, 0);
    }
    return lines;
  }

  breakpointText(breakpoint: UiDebugBreakpointDto): string {
    return `  line ${breakpoint.line} enabled=${breakpoint.enabled}`;
  }

  eventText(event: UiDebugEventDto): string {
    return `  #${event.eventId} [${event.type}] ${event.title} · ${event.description}`;
  }

  timelineText(item: UiDebugTimelineItemDto): string {
    return `  snapshot ${item.snapshotId} step=${item.visibleStepIndex} reason=${item.stopReason} breakpoint=${item.breakpointHit} range=${this.rangeText(item.sourceRange)}`;
  }

  visualElementText(element: UiDebugVisualElementDto): string {
    const name = element.metadata.fieldName ?? element.label;
    const value = element.metadata.valueSummary ?? "";
    const pointerTarget = element.metadata.pointerTarget ?? "";
    const type = element.metadata.type ?? element.metadata.typeName ?? "";
    if (pointerTarget.trim().length > 0) {
      return `${name}${type.trim().length === 0 ? "" : ` : ${type}`} -> ${pointerTarget}`;
    }
    if (value.trim().length > 0) {
      return `${name}${type.trim().length === 0 ? "" : ` : ${type}`} = ${value}`;
    }
    return `${name}${type.trim().length === 0 ? "" : ` : ${type}`}`;
  }

  emptyText(value: string | null | undefined): string {
    return value === null || value === undefined || value.trim().length === 0 ? "(empty)" : value;
  }

  rangeText(range: UiSourceSpanDto | null | undefined): string {
    if (range === null || range === undefined) {
      return "";
    }
    return `${range.sourceName}:${range.startLine}:${range.startColumn}-${range.endLine}:${range.endColumn}`;
  }

  variableText(variable: UiDebugVariableDto): string {
    return `  ${variable.name} ${variable.typeName} ${variable.valueKind} = ${variable.valueSummary} @ ${variable.address}${
      variable.pointerTarget.trim().length === 0 ? "" : ` pointerTarget=${variable.pointerTarget}`
    }${variable.typeShape.trim().length === 0 ? "" : ` shape=${variable.typeShape}`}${variable.highlightedChange ? " changed" : ""}${
      variable.explanation.trim().length === 0 ? "" : ` · ${variable.explanation}`
    }`;
  }

  addVariableLines(lines: string[], variable: UiDebugVariableDto, depth: number): void {
    lines.push(`  ${"  ".repeat(depth)}${this.variableText(variable).trimStart()}`);
    variable.fields.forEach((field) => this.addVariableLines(lines, field, depth + 1));
    variable.elements.forEach((element) => this.addVariableLines(lines, element, depth + 1));
  }

  variableTreeText(variables: readonly UiDebugVariableDto[]): string {
    return this.variableLines(variables).join("\n");
  }

  summary(): string {
    return `MiniCDebugTextFormatter: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCDebugTextFormatter;
