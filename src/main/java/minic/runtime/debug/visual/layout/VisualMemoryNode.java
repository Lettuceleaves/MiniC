package minic.runtime.debug.visual.layout;

import java.util.Map;
import java.util.Objects;

/**
 * Runtime memory node represented as a byte span.
 *
 * @param address stable memory reference
 * @param byteCount byte span length
 * @param role visual node role
 * @param metadata layout metadata
 */
public record VisualMemoryNode(String address, int byteCount, VisualMemoryNodeRole role, Map<String, String> metadata) {
    public VisualMemoryNode(String address, int byteCount) {
        this(address, byteCount, VisualMemoryNodeRole.OBJECT, Map.of());
    }

    public VisualMemoryNode {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(metadata, "metadata");
        if (address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }
        if (byteCount <= 0) {
            throw new IllegalArgumentException("byteCount must be positive");
        }
        metadata = Map.copyOf(metadata);
    }
}
