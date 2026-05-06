package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * UI Debug 快照 DTO。
 */
public record UiDebugSnapshotDto(
        long snapshotId,
        long visibleStepIndex,
        String functionName,
        String blockLabel,
        String instructionId,
        UiSourceSpanDto sourceRange,
        List<String> callStackSummary,
        UiDebugProcessSpaceDto processSpace,
        boolean breakpointHit,
        String stopReason
) {
    public UiDebugSnapshotDto {
        Objects.requireNonNull(functionName, "functionName");
        Objects.requireNonNull(blockLabel, "blockLabel");
        Objects.requireNonNull(instructionId, "instructionId");
        Objects.requireNonNull(callStackSummary, "callStackSummary");
        Objects.requireNonNull(processSpace, "processSpace");
        Objects.requireNonNull(stopReason, "stopReason");
        callStackSummary = List.copyOf(callStackSummary);
    }
}
