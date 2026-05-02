package minic.uiapi;

import minic.runtime.step.StepResult;

import java.util.List;
import java.util.Objects;

/**
 * UI 控制动作结果。
 *
 * @param outcome 结果类别
 * @param stage 当前阶段 ID
 * @param title 标题
 * @param description 说明
 * @param diagnostics 关联诊断
 */
public record UiControlResultDto(
        String outcome,
        String stage,
        String title,
        String description,
        List<UiDiagnosticDto> diagnostics
) {
    public UiControlResultDto {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    static UiControlResultDto from(StepResult result) {
        return new UiControlResultDto(
                result.outcome().name(),
                result.stage().id(),
                result.title(),
                result.description(),
                result.diagnostics().stream().map(UiDiagnosticDto::from).toList()
        );
    }
}
