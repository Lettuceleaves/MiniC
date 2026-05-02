package minic.runtime.step;

import minic.diagnostics.Diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * 当前阶段数据区。
 *
 * @param stage 阶段标识
 * @param progress 阶段进度
 * @param inputSummary 阶段输入摘要
 * @param currentItem 当前项摘要；没有当前项时为空字符串
 * @param accumulatedOutput 累计输出摘要
 * @param diagnostics 阶段 diagnostics
 */
public record StageStepData(
        CompileStage stage,
        StageProgress progress,
        List<String> inputSummary,
        String currentItem,
        List<String> accumulatedOutput,
        List<Diagnostic> diagnostics
) {
    /**
     * 创建阶段数据区，并防御性复制集合。
     *
     * @param stage 阶段标识
     * @param progress 阶段进度
     * @param inputSummary 阶段输入摘要
     * @param currentItem 当前项摘要；没有当前项时为空字符串
     * @param accumulatedOutput 累计输出摘要
     * @param diagnostics 阶段 diagnostics
     */
    public StageStepData {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(inputSummary, "inputSummary");
        Objects.requireNonNull(currentItem, "currentItem");
        Objects.requireNonNull(accumulatedOutput, "accumulatedOutput");
        Objects.requireNonNull(diagnostics, "diagnostics");
        inputSummary = List.copyOf(inputSummary);
        accumulatedOutput = List.copyOf(accumulatedOutput);
        diagnostics = List.copyOf(diagnostics);
    }
}
