package minic.runtime.debug.memory;

import java.util.Objects;

/**
 * Named struct field in a typed memory node.
 *
 * @param name field name
 * @param value field value node
 */
public record TypedMemoryField(String name, TypedMemoryNode value) {
    public TypedMemoryField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
