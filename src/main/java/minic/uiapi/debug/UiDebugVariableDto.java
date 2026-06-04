package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * UI Debug 变量和值 DTO。
 */
public record UiDebugVariableDto(
        String name,
        String address,
        String typeName,
        String valueKind,
        String valueSummary,
        String pointerTarget,
        String typeShape,
        boolean highlightedChange,
        String explanation,
        List<UiDebugVariableDto> fields,
        List<UiDebugVariableDto> elements
) {
    public UiDebugVariableDto {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(valueKind, "valueKind");
        Objects.requireNonNull(valueSummary, "valueSummary");
        Objects.requireNonNull(pointerTarget, "pointerTarget");
        Objects.requireNonNull(typeShape, "typeShape");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(elements, "elements");
        fields = List.copyOf(fields);
        elements = List.copyOf(elements);
    }

    /**
     * 创建兼容旧调用点的扁平变量 DTO。
     */
    public UiDebugVariableDto(
            String name,
            String address,
            String typeName,
            String valueKind,
            String valueSummary
    ) {
        this(
                name,
                address,
                typeName,
                valueKind,
                valueSummary,
                "",
                "",
                false,
                "",
                List.of(),
                List.of()
        );
    }
}
