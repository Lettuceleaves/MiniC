package minic.runtime.step;

/**
 * 编译大阶段统一步进接口。
 *
 * <p>该接口属于运行时兼容层，只暴露 UI 可消费的状态和阶段数据，
 * 不暴露编译层内部可变工作数据。</p>
 */
public interface StageStepper {
    /**
     * 返回阶段标识。
     *
     * @return 阶段标识
     */
    CompileStage stage();

    /**
     * 当前阶段是否还能正向推进。
     *
     * @return 可以推进时为 {@code true}
     */
    boolean canNext();

    /**
     * 当前阶段是否还能反向退回。
     *
     * @return 本阶段始终为 {@code false}
     */
    default boolean canPrevious() {
        return false;
    }

    /**
     * 正向推进一步。
     *
     * @return 单步结果
     */
    StepResult next();

    /**
     * 反向退回一步。本阶段仅预留接口。
     *
     * @return unsupported 单步结果
     */
    default StepResult previous() {
        return StepResult.unsupported(stage(), "上一步暂不支持", "当前阶段只预留反向步进能力。");
    }

    /**
     * 返回当前状态快照。
     *
     * @return 当前状态
     */
    CurrentStepState snapshot();

    /**
     * 返回当前阶段数据。
     *
     * @return 阶段数据
     */
    StageStepData data();
}
