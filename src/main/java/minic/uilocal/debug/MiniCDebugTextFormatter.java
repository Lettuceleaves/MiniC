package minic.uilocal;

import minic.uiapi.UiDebugBreakpointDto;
import minic.uiapi.UiDebugEventDto;
import minic.uiapi.UiDebugFrameDto;
import minic.uiapi.UiDebugTimelineItemDto;
import minic.uiapi.UiDebugVariableDto;
import minic.uiapi.UiDebugVisualElementDto;
import minic.uiapi.UiSourceSpanDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class MiniCDebugTextFormatter {
    private MiniCDebugTextFormatter() {
    }

    static String frameText(UiDebugFrameDto frame) {
        return "  " + frame.functionName()
                + " return=" + (frame.returnTarget() == null ? "" : frame.returnTarget())
                + " active=" + rangeText(frame.activeRange());
    }

    static String frameWithValuesText(UiDebugFrameDto frame) {
        String parameters = variableTreeText(frame.parameters());
        String locals = variableTreeText(frame.locals());
        return frameText(frame)
                + " params=[" + parameters + "]"
                + " locals=[" + locals + "]";
    }

    static List<String> stackLines(List<UiDebugFrameDto> frames) {
        ArrayList<String> lines = new ArrayList<>();
        for (UiDebugFrameDto frame : frames) {
            lines.add(frameText(frame));
            if (!frame.parameters().isEmpty()) {
                lines.add("  parameters:");
                lines.addAll(variableLines(frame.parameters()));
            }
            if (!frame.locals().isEmpty()) {
                lines.add("  locals:");
                lines.addAll(variableLines(frame.locals()));
            }
        }
        return lines;
    }

    static List<String> variableLines(List<UiDebugVariableDto> variables) {
        ArrayList<String> lines = new ArrayList<>();
        variables.forEach(variable -> addVariableLines(lines, variable, 0));
        return lines;
    }

    static String breakpointText(UiDebugBreakpointDto breakpoint) {
        return "  line " + breakpoint.line() + " enabled=" + breakpoint.enabled();
    }

    static String eventText(UiDebugEventDto event) {
        return "  #" + event.eventId()
                + " [" + event.type() + "] " + event.title()
                + " · " + event.description();
    }

    static String timelineText(UiDebugTimelineItemDto item) {
        return "  snapshot " + item.snapshotId()
                + " step=" + item.visibleStepIndex()
                + " reason=" + item.stopReason()
                + " breakpoint=" + item.breakpointHit()
                + " range=" + rangeText(item.sourceRange());
    }

    static String visualElementText(UiDebugVisualElementDto element) {
        Map<String, String> metadata = element.metadata();
        String name = metadata.getOrDefault("fieldName", element.label());
        String value = metadata.getOrDefault("valueSummary", "");
        String pointerTarget = metadata.getOrDefault("pointerTarget", "");
        String type = metadata.getOrDefault("type", metadata.getOrDefault("typeName", ""));
        if (!pointerTarget.isBlank()) {
            return name + (type.isBlank() ? "" : " : " + type) + " -> " + pointerTarget;
        }
        if (!value.isBlank()) {
            return name + (type.isBlank() ? "" : " : " + type) + " = " + value;
        }
        return name + (type.isBlank() ? "" : " : " + type);
    }

    static String emptyText(String value) {
        return value == null || value.isBlank() ? "(empty)" : value;
    }

    static String rangeText(UiSourceSpanDto range) {
        if (range == null) {
            return "";
        }
        return range.sourceName()
                + ":" + range.startLine()
                + ":" + range.startColumn()
                + "-" + range.endLine()
                + ":" + range.endColumn();
    }

    static String variableText(UiDebugVariableDto variable) {
        return "  " + variable.name()
                + " " + variable.typeName()
                + " " + variable.valueKind()
                + " = " + variable.valueSummary()
                + " @ " + variable.address()
                + (variable.pointerTarget().isBlank() ? "" : " pointerTarget=" + variable.pointerTarget())
                + (variable.typeShape().isBlank() ? "" : " shape=" + variable.typeShape())
                + (variable.highlightedChange() ? " changed" : "")
                + (variable.explanation().isBlank() ? "" : " · " + variable.explanation());
    }

    private static void addVariableLines(List<String> lines, UiDebugVariableDto variable, int depth) {
        lines.add("  " + "  ".repeat(depth) + variableText(variable).stripLeading());
        variable.fields().forEach(field -> addVariableLines(lines, field, depth + 1));
        variable.elements().forEach(element -> addVariableLines(lines, element, depth + 1));
    }

    private static String variableTreeText(List<UiDebugVariableDto> variables) {
        return String.join("\n", variableLines(variables));
    }
}
