package minic.runtime.debug.visual.grid;

import java.util.List;
import java.util.Objects;

/**
 * Grid drawing scene produced after layout.
 *
 * @param id visual structure id
 * @param name display name
 * @param kind visual kind
 * @param nodes finalized grid nodes
 * @param edges finalized grid edges
 */
public record GridScene(
        String id,
        String name,
        String kind,
        List<GridSceneNode> nodes,
        List<GridSceneEdge> edges
) {
    public GridScene {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        if (id.isBlank() || name.isBlank() || kind.isBlank()) {
            throw new IllegalArgumentException("id, name and kind must not be blank");
        }
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
