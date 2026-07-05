package minic.runtime.debug.visual.layout;

import java.util.Objects;

/**
 * Edge with finalized endpoint anchors.
 *
 * @param fromAddress source node address
 * @param toAddress target node address
 * @param fromAnchor source anchor
 * @param toAnchor target anchor
 * @param start source anchor point
 * @param end target anchor point
 */
public record PlacedEdge(
        String fromAddress,
        String toAddress,
        NodeAnchor fromAnchor,
        NodeAnchor toAnchor,
        GridPoint start,
        GridPoint end
) {
    public PlacedEdge {
        Objects.requireNonNull(fromAddress, "fromAddress");
        Objects.requireNonNull(toAddress, "toAddress");
        Objects.requireNonNull(fromAnchor, "fromAnchor");
        Objects.requireNonNull(toAnchor, "toAnchor");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (fromAddress.isBlank() || toAddress.isBlank()) {
            throw new IllegalArgumentException("edge addresses must not be blank");
        }
    }
}
