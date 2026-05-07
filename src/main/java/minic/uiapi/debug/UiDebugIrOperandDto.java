package minic.uiapi;

import java.util.Objects;

/**
 * IR Debug 操作数和值关联 DTO。
 */
public record UiDebugIrOperandDto(
        String name,
        String typeName,
        String valueSummary,
        String valueRef
) {
    public UiDebugIrOperandDto {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(valueSummary, "valueSummary");
        Objects.requireNonNull(valueRef, "valueRef");
    }
}
