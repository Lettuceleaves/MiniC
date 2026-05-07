package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * Debug 数据结构视图 UI 模型。
 */
public record UiDebugDataStructureViewDto(
        UiDebugProcessSpaceDto processSpace,
        List<UiDebugVisualStructureDto> visuals,
        List<String> warnings
) {
    public UiDebugDataStructureViewDto {
        Objects.requireNonNull(processSpace, "processSpace");
        Objects.requireNonNull(visuals, "visuals");
        Objects.requireNonNull(warnings, "warnings");
        visuals = List.copyOf(visuals);
        warnings = List.copyOf(warnings);
    }
}
