package minic.runtime.debug.visual.grid;

import minic.runtime.debug.visual.layout.GridRect;

import java.util.Map;
import java.util.Objects;

/**
 * Grid drawing node with finalized bounds.
 *
 * @param id visual node id
 * @param label display label
 * @param valueRef runtime value reference
 * @param bounds grid bounds
 * @param metadata node metadata
 */
public record GridSceneNode(
        String id,
        String label,
        String valueRef,
        GridRect bounds,
        Map<String, String> metadata
) {
    public GridSceneNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(valueRef, "valueRef");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(metadata, "metadata");
        if (id.isBlank() || label.isBlank()) {
            throw new IllegalArgumentException("id and label must not be blank");
        }
        metadata = Map.copyOf(metadata);
    }
}
