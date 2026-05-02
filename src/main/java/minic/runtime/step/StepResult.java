package minic.runtime.step;

import minic.diagnostics.Diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * 单步控制动作的结果。
 *
 * @param outcome 结果类别
 * @param stage 当前关联阶段
 * @param title 简短标题
 * @param description 说明文本
 * @param diagnostics 该步骤关联的诊断
 */
public record StepResult(
        StepOutcome outcome,
        CompileStage stage,
        String title,
        String description,
        List<Diagnostic> diagnostics
) {
    /**
     * 创建单步结果，并防御性复制 diagnostics。
     *
     * @param outcome 结果类别
     * @param stage 当前关联阶段
     * @param title 简短标题
     * @param description 说明文本
     * @param diagnostics 该步骤关联的诊断
     */
    public StepResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * 创建无诊断的成功推进结果。
     *
     * @param stage 当前关联阶段
     * @param title 简短标题
     * @param description 说明文本
     * @return 单步结果
     */
    public static StepResult advanced(CompileStage stage, String title, String description) {
        return new StepResult(StepOutcome.ADVANCED, stage, title, description, List.of());
    }

    /**
     * 创建阶段完成结果。
     *
     * @param stage 当前关联阶段
     * @param title 简短标题
     * @param description 说明文本
     * @return 单步结果
     */
    public static StepResult stageCompleted(CompileStage stage, String title, String description) {
        return new StepResult(StepOutcome.STAGE_COMPLETED, stage, title, description, List.of());
    }

    /**
     * 创建无法继续推进结果。
     *
     * @param stage 当前关联阶段
     * @param title 简短标题
     * @param description 说明文本
     * @return 单步结果
     */
    public static StepResult cannotAdvance(CompileStage stage, String title, String description) {
        return new StepResult(StepOutcome.CANNOT_ADVANCE, stage, title, description, List.of());
    }

    /**
     * 创建当前不支持的能力结果。
     *
     * @param stage 当前关联阶段
     * @param title 简短标题
     * @param description 说明文本
     * @return 单步结果
     */
    public static StepResult unsupported(CompileStage stage, String title, String description) {
        return new StepResult(StepOutcome.UNSUPPORTED, stage, title, description, List.of());
    }

    /**
     * 创建失败结果。
     *
     * @param stage 当前关联阶段
     * @param title 简短标题
     * @param description 说明文本
     * @param diagnostics 失败诊断
     * @return 单步结果
     */
    public static StepResult failed(
            CompileStage stage,
            String title,
            String description,
            List<Diagnostic> diagnostics
    ) {
        return new StepResult(StepOutcome.FAILED, stage, title, description, diagnostics);
    }
}
