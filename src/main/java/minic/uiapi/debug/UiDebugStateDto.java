package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * UI Debug 状态 DTO。
 */
public record UiDebugStateDto(
        String sourceName,
        String executionState,
        UiDebugSnapshotDto currentSnapshot,
        List<UiDebugSnapshotDto> snapshots,
        List<UiDebugEventDto> events,
        List<UiDebugBreakpointDto> breakpoints
) {
    public UiDebugStateDto {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(executionState, "executionState");
        Objects.requireNonNull(currentSnapshot, "currentSnapshot");
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(breakpoints, "breakpoints");
        snapshots = List.copyOf(snapshots);
        events = List.copyOf(events);
        breakpoints = List.copyOf(breakpoints);
    }
}
