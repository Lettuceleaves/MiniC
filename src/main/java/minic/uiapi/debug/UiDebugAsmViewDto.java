package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * ASM Debug 视图 DTO。
 */
public record UiDebugAsmViewDto(
        List<UiAssemblyLineVisualDto> lines,
        String explanation,
        List<String> relatedIrIds
) {
    public UiDebugAsmViewDto {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(relatedIrIds, "relatedIrIds");
        lines = List.copyOf(lines);
        relatedIrIds = List.copyOf(relatedIrIds);
    }
}
