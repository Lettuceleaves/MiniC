package minic.uiapi;

import minic.runtime.step.CurrentStepState;

import java.util.List;
import java.util.Objects;

/**
 * UI 当前状态 DTO。
 *
 * @param sourceName 源码名称
 * @param currentStage 当前阶段 ID
 * @param globalStepIndex 全局步骤下标
 * @param stageStepIndex 阶段内步骤下标
 * @param playbackMode 播放模式
 * @param frameIntervalMillis 帧间隔毫秒
 * @param sourceRange 当前源码范围；没有时为 {@code null}
 * @param title 标题
 * @param description 说明
 * @param diagnostics 当前诊断
 * @param canNext 是否可下一步
 * @param canPrevious 是否可上一步
 * @param canPlay 是否可播放
 * @param canPlayFast 是否可两倍速播放
 * @param canPause 是否可暂停
 * @param canReversePlay 是否可自动倒放
 */
public record UiCurrentStateDto(
        String sourceName,
        String currentStage,
        long globalStepIndex,
        long stageStepIndex,
        String playbackMode,
        long frameIntervalMillis,
        UiSourceRangeDto sourceRange,
        String title,
        String description,
        List<UiDiagnosticDto> diagnostics,
        boolean canNext,
        boolean canPrevious,
        boolean canPlay,
        boolean canPlayFast,
        boolean canPause,
        boolean canReversePlay
) {
    public UiCurrentStateDto {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(currentStage, "currentStage");
        Objects.requireNonNull(playbackMode, "playbackMode");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    static UiCurrentStateDto from(CurrentStepState state) {
        return new UiCurrentStateDto(
                state.sourceName(),
                state.currentStage().id(),
                state.globalStepIndex(),
                state.stageStepIndex(),
                state.playbackMode().name(),
                state.frameInterval().toMillis(),
                state.sourceRangeOptional().map(UiSourceRangeDto::from).orElse(null),
                state.title(),
                state.description(),
                state.diagnostics().stream().map(UiDiagnosticDto::from).toList(),
                state.canNext(),
                state.canPrevious(),
                state.canPlay(),
                state.canPlayFast(),
                state.canPause(),
                state.canReversePlay()
        );
    }
}
