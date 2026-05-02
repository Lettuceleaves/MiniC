package minic.runtime.step;

/**
 * 当前编译观测会话的控制能力。
 *
 * @param canNext 是否允许下一步
 * @param canPrevious 是否允许上一步
 * @param canPlay 是否允许自动播放
 * @param canPlayFast 是否允许两倍速播放
 * @param canPause 是否允许暂停
 * @param canReversePlay 是否允许自动倒放
 */
public record StepCapabilities(
        boolean canNext,
        boolean canPrevious,
        boolean canPlay,
        boolean canPlayFast,
        boolean canPause,
        boolean canReversePlay
) {
    /**
     * 创建本阶段默认能力模型。正向控制可用，反向控制仅预留。
     *
     * @return 默认能力模型
     */
    public static StepCapabilities forwardOnly() {
        return new StepCapabilities(true, false, true, true, true, false);
    }

    /**
     * 创建全部能力不可用的模型。
     *
     * @return 空能力模型
     */
    public static StepCapabilities none() {
        return new StepCapabilities(false, false, false, false, false, false);
    }

    /**
     * 返回 previous 能力不可用时的标准结果。
     *
     * @param stage 当前关联阶段
     * @return unsupported 结果
     */
    public StepResult previousUnsupported(CompileStage stage) {
        return StepResult.unsupported(stage, "上一步暂不支持", "当前阶段只预留反向步进能力。");
    }

    /**
     * 返回 reversePlay 能力不可用时的标准结果。
     *
     * @param stage 当前关联阶段
     * @return unsupported 结果
     */
    public StepResult reversePlayUnsupported(CompileStage stage) {
        return StepResult.unsupported(stage, "自动倒放暂不支持", "当前阶段只预留自动倒放能力。");
    }
}
