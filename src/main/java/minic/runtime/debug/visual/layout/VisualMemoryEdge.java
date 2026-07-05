package minic.runtime.debug.visual.layout;

import java.util.Objects;

/**
 * Directed pointer relation between memory nodes.
 *
 * @param fromAddress source node address
 * @param toAddress target node address
 */
public record VisualMemoryEdge(String fromAddress, String toAddress) {
    public VisualMemoryEdge {
        Objects.requireNonNull(fromAddress, "fromAddress");
        Objects.requireNonNull(toAddress, "toAddress");
        if (fromAddress.isBlank() || toAddress.isBlank()) {
            throw new IllegalArgumentException("edge addresses must not be blank");
        }
    }
}
