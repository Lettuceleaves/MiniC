package minic.runtime.step;

import minic.diagnostics.Diagnostic;
import minic.source.SourceRange;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UI 可直接消费的全局当前状态数据。
 *
 * @param sourceName 源码名称
 * @param currentStage 当前阶段
 * @param globalStepIndex 全局步骤下标
 * @param stageStepIndex 阶段内步骤下标
 * @param playbackMode 播放模式
 * @param frameInterval 帧间隔
 * @param sourceRange 当前步骤关联源码范围；没有时为 {@code null}
 * @param title 当前步骤标题
 * @param description 当前步骤说明
 * @param diagnostics 当前步骤关联 diagnostics
 * @param capabilities 当前控制能力
 */
public record CurrentStepState(
        String sourceName,
        CompileStage currentStage,
        long globalStepIndex,
        long stageStepIndex,
        PlaybackMode playbackMode,
        Duration frameInterval,
        SourceRange sourceRange,
        String title,
        String description,
        List<Diagnostic> diagnostics,
        StepCapabilities capabilities
) {
    /**
     * 创建当前状态数据，并防御性复制 diagnostics。
     *
     * @param sourceName 源码名称
     * @param currentStage 当前阶段
     * @param globalStepIndex 全局步骤下标
     * @param stageStepIndex 阶段内步骤下标
     * @param playbackMode 播放模式
     * @param frameInterval 帧间隔
     * @param sourceRange 当前步骤关联源码范围；没有时为 {@code null}
     * @param title 当前步骤标题
     * @param description 当前步骤说明
     * @param diagnostics 当前步骤关联 diagnostics
     * @param capabilities 当前控制能力
     */
    public CurrentStepState {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(currentStage, "currentStage");
        Objects.requireNonNull(playbackMode, "playbackMode");
        Objects.requireNonNull(frameInterval, "frameInterval");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(capabilities, "capabilities");
        if (sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        if (globalStepIndex < 0) {
            throw new IllegalArgumentException("globalStepIndex must not be negative");
        }
        if (stageStepIndex < 0) {
            throw new IllegalArgumentException("stageStepIndex must not be negative");
        }
        if (frameInterval.isNegative()) {
            throw new IllegalArgumentException("frameInterval must not be negative");
        }
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * 以 Optional 形式返回当前源码范围。
     *
     * @return 源码范围 Optional
     */
    public Optional<SourceRange> sourceRangeOptional() {
        return Optional.ofNullable(sourceRange);
    }

    /**
     * 是否允许下一步。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canNext() {
        return capabilities.canNext();
    }

    /**
     * 是否允许上一步。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canPrevious() {
        return capabilities.canPrevious();
    }

    /**
     * 是否允许自动播放。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canPlay() {
        return capabilities.canPlay();
    }

    /**
     * 是否允许两倍速播放。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canPlayFast() {
        return capabilities.canPlayFast();
    }

    /**
     * 是否允许暂停。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canPause() {
        return capabilities.canPause();
    }

    /**
     * 是否允许自动倒放。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canReversePlay() {
        return capabilities.canReversePlay();
    }
}
