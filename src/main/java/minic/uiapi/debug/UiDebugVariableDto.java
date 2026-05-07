package minic.uiapi;

import java.util.Objects;

/**
 * UI Debug 变量和值 DTO。
 */
public record UiDebugVariableDto(
        String name,
        String address,
        String typeName,
        String valueKind,
        String valueSummary
) {
    public UiDebugVariableDto {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(valueKind, "valueKind");
        Objects.requireNonNull(valueSummary, "valueSummary");
    }
}
