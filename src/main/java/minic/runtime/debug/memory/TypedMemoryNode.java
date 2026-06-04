package minic.runtime.debug.memory;

import java.util.List;
import java.util.Objects;

/**
 * Recursive typed memory value for debugger visualization.
 *
 * @param id stable graph node id
 * @param name display name or path
 * @param address address display text, or {@code null} when unknown
 * @param typeName source type name
 * @param shape type shape
 * @param valueSummary scalar or aggregate summary
 * @param fields struct fields
 * @param elements array elements
 * @param pointerTarget pointer target address display text, or {@code null}
 */
public record TypedMemoryNode(
        String id,
        String name,
        String address,
        String typeName,
        TypeShape shape,
        String valueSummary,
        List<TypedMemoryField> fields,
        List<TypedMemoryElement> elements,
        String pointerTarget
) {
    public TypedMemoryNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(valueSummary, "valueSummary");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(elements, "elements");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be blank");
        }
        fields = List.copyOf(fields);
        elements = List.copyOf(elements);
    }
}
