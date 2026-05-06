package minic.uiapi;

import java.util.Objects;

/**
 * Debug snapshot timeline 项。
 */
public record UiDebugTimelineItemDto(
        long snapshotId,
        long visibleStepIndex,
        String stopReason,
        boolean breakpointHit,
        UiSourceSpanDto sourceRange
) {
    public UiDebugTimelineItemDto {
        Objects.requireNonNull(stopReason, "stopReason");
    }
}
