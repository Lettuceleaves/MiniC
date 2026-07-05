package minic.runtime.debug.visual.grid;

import minic.runtime.debug.visual.layout.GridPoint;
import minic.runtime.debug.visual.layout.NodeAnchor;

import java.util.Map;
import java.util.Objects;

/**
 * Grid drawing edge with finalized endpoint anchors.
 *
 * @param id visual edge id
 * @param fromNodeId source node id
 * @param toNodeId target node id
 * @param label display label
 * @param fromAnchor source anchor
 * @param toAnchor target anchor
 * @param start source anchor point
 * @param end target anchor point
 * @param metadata edge metadata
 */
public record GridSceneEdge(
        String id,
        String fromNodeId,
        String toNodeId,
        String label,
        NodeAnchor fromAnchor,
        NodeAnchor toAnchor,
        GridPoint start,
        GridPoint end,
        Map<String, String> metadata
) {
    public GridSceneEdge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromNodeId, "fromNodeId");
        Objects.requireNonNull(toNodeId, "toNodeId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(fromAnchor, "fromAnchor");
        Objects.requireNonNull(toAnchor, "toAnchor");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(metadata, "metadata");
        if (id.isBlank() || fromNodeId.isBlank() || toNodeId.isBlank()) {
            throw new IllegalArgumentException("edge id and endpoints must not be blank");
        }
        metadata = Map.copyOf(metadata);
    }
}
