package minic.uiapi;

import java.util.ArrayList;

/**
 * Debug 元数据视图模型构建器。
 */
public final class UiDebugMetadataViewBuilder {
    /**
     * 从 Debug 状态构建元数据视图模型。
     *
     * @param state Debug 状态
     * @return 元数据视图模型
     */
    public UiDebugMetadataViewDto build(UiDebugStateDto state) {
        UiDebugSnapshotDto snapshot = state.currentSnapshot();
        ArrayList<UiDebugVariableDto> variables = new ArrayList<>();
        snapshot.processSpace().stackFrames().forEach(frame -> {
            variables.addAll(frame.parameters());
            variables.addAll(frame.locals());
        });
        return new UiDebugMetadataViewDto(
                state.executionState(),
                snapshot.stopReason(),
                snapshot.functionName(),
                snapshot.sourceRange(),
                snapshot.processSpace().stackFrames(),
                variables,
                snapshot.processSpace().stdout(),
                snapshot.processSpace().stderr(),
                state.breakpoints(),
                state.events(),
                state.snapshots().stream()
                        .map(item -> new UiDebugTimelineItemDto(
                                item.snapshotId(),
                                item.visibleStepIndex(),
                                item.stopReason(),
                                item.breakpointHit(),
                                item.sourceRange()
                        ))
                        .toList()
        );
    }
}
