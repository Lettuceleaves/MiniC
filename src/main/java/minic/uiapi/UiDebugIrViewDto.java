package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * IR Debug 视图 DTO。
 */
public record UiDebugIrViewDto(
        List<UiIrLineVisualDto> lines,
        String currentInstructionId,
        UiSourceSpanDto currentSourceRange,
        String explanation,
        List<UiDebugIrOperandDto> operands
) {
    public UiDebugIrViewDto {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(currentInstructionId, "currentInstructionId");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(operands, "operands");
        lines = List.copyOf(lines);
        operands = List.copyOf(operands);
    }
}
