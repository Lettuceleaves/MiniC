package minic.runtime.debug.memory;

import java.util.Objects;

/**
 * Pointer relation from a graph node to a target address and, when resolvable, a target node.
 *
 * @param fromNodeId source node id
 * @param toAddress target address display text
 * @param toNodeId resolved target node id; {@code null} when the target is not visible
 */
public record TypedPointerEdge(String fromNodeId, String toAddress, String toNodeId) {
    public TypedPointerEdge {
        Objects.requireNonNull(fromNodeId, "fromNodeId");
        Objects.requireNonNull(toAddress, "toAddress");
        if (fromNodeId.isBlank()) {
            throw new IllegalArgumentException("fromNodeId must not be blank");
        }
        if (toAddress.isBlank()) {
            throw new IllegalArgumentException("toAddress must not be blank");
        }
    }

    /**
     * Creates an unresolved pointer edge.
     *
     * @param fromNodeId source node id
     * @param toAddress target address display text
     */
    public TypedPointerEdge(String fromNodeId, String toAddress) {
        this(fromNodeId, toAddress, null);
    }
}
