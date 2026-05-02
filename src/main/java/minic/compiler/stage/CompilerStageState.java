package minic.compiler.stage;

import minic.runtime.step.CompileStage;

/**
 * 编译阶段执行状态模式。
 *
 * @param <I> 输入数据类型
 * @param <W> 工作数据类型
 * @param <O> 输出数据类型
 */
public interface CompilerStageState<
        I extends CompilerStageInput,
        W extends CompilerStageWork,
        O extends CompilerStageOutput> {
    /**
     * 返回阶段标识。
     *
     * @return 阶段标识
     */
    CompileStage stage();

    /**
     * 返回输入数据。
     *
     * @return 输入数据
     */
    I input();

    /**
     * 返回编译层内部工作数据。
     *
     * @return 工作数据
     */
    W work();

    /**
     * 返回当前快照。
     *
     * @return 当前快照
     */
    CompilerStageSnapshot snapshot();

    /**
     * 当前阶段是否还能正向推进。
     *
     * @return 可以推进时为 {@code true}
     */
    boolean canNext();

    /**
     * 正向推进一个阶段内部最小步骤。
     *
     * @return 推进后的快照
     */
    CompilerStageSnapshot next();

    /**
     * 构建当前阶段输出数据。
     *
     * @return 阶段结果
     */
    CompilerStageResult<O> result();
}
