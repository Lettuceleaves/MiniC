package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * Debug 元数据视图 UI 模型。
 */
public record UiDebugMetadataViewDto(
        String executionState,
        String stopReason,
        String currentFunction,
        UiSourceSpanDto currentSourceRange,
        List<UiDebugFrameDto> callStack,
        List<UiDebugVariableDto> variables,
        String stdout,
        String stderr,
        List<UiDebugBreakpointDto> breakpoints,
        List<UiDebugEventDto> events,
        List<UiDebugTimelineItemDto> timeline
) {
    public UiDebugMetadataViewDto {
        Objects.requireNonNull(executionState, "executionState");
        Objects.requireNonNull(stopReason, "stopReason");
        Objects.requireNonNull(currentFunction, "currentFunction");
        Objects.requireNonNull(callStack, "callStack");
        Objects.requireNonNull(variables, "variables");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
        Objects.requireNonNull(breakpoints, "breakpoints");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(timeline, "timeline");
        callStack = List.copyOf(callStack);
        variables = List.copyOf(variables);
        breakpoints = List.copyOf(breakpoints);
        events = List.copyOf(events);
        timeline = List.copyOf(timeline);
    }
}
