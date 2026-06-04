package minic.runtime.debug.memory;

import java.util.Objects;

/**
 * Indexed array element in a typed memory node.
 *
 * @param index element index
 * @param value element value node
 */
public record TypedMemoryElement(long index, TypedMemoryNode value) {
    public TypedMemoryElement {
        Objects.requireNonNull(value, "value");
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
    }
}
