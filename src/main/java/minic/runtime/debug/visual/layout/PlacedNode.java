package minic.runtime.debug.visual.layout;

import java.util.Objects;

/**
 * Node with finalized grid bounds.
 *
 * @param address memory reference
 * @param bounds grid bounds
 * @param measure measured size
 * @param role visual node role
 */
public record PlacedNode(String address, GridRect bounds, NodeMeasure measure, VisualMemoryNodeRole role) {
    public PlacedNode(String address, GridRect bounds, NodeMeasure measure) {
        this(address, bounds, measure, VisualMemoryNodeRole.OBJECT);
    }

    public PlacedNode {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(measure, "measure");
        Objects.requireNonNull(role, "role");
        if (address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }
    }
}
