package minic.uiapi;

import minic.runtime.step.StageStepData;

import java.util.List;
import java.util.Objects;

/**
 * UI 当前阶段数据 DTO。
 *
 * @param stage 阶段 ID
 * @param completedSteps 已完成步骤数
 * @param totalSteps 总步骤数；未知时为 -1
 * @param completed 阶段是否完成
 * @param inputSummary 输入摘要
 * @param currentItem 当前项
 * @param accumulatedOutput 累计输出摘要
 * @param diagnostics 阶段诊断
 */
public record UiStageDataDto(
        String stage,
        long completedSteps,
        long totalSteps,
        boolean completed,
        List<String> inputSummary,
        String currentItem,
        List<String> accumulatedOutput,
        List<UiDiagnosticDto> diagnostics
) {
    public UiStageDataDto {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(inputSummary, "inputSummary");
        Objects.requireNonNull(currentItem, "currentItem");
        Objects.requireNonNull(accumulatedOutput, "accumulatedOutput");
        Objects.requireNonNull(diagnostics, "diagnostics");
        inputSummary = List.copyOf(inputSummary);
        accumulatedOutput = List.copyOf(accumulatedOutput);
        diagnostics = List.copyOf(diagnostics);
    }

    static UiStageDataDto from(StageStepData data) {
        return new UiStageDataDto(
                data.stage().id(),
                data.progress().completedSteps(),
                data.progress().totalSteps(),
                data.progress().completed(),
                data.inputSummary(),
                data.currentItem(),
                data.accumulatedOutput(),
                data.diagnostics().stream().map(UiDiagnosticDto::from).toList()
        );
    }
}
