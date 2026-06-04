package minic.runtime.debug.memory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed memory graph roots and pointer edges.
 *
 * @param roots root memory nodes
 * @param pointerEdges pointer edges found in roots and descendants
 */
public record TypedMemoryGraph(
        List<TypedMemoryNode> roots,
        List<TypedPointerEdge> pointerEdges
) {
    public TypedMemoryGraph {
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(pointerEdges, "pointerEdges");
        roots = List.copyOf(roots);
        pointerEdges = List.copyOf(pointerEdges);
    }

    /**
     * Finds a root by display name.
     *
     * @param name root name
     * @return matching root, when present
     */
    public Optional<TypedMemoryNode> findRoot(String name) {
        Objects.requireNonNull(name, "name");
        return roots.stream()
                .filter(root -> root.name().equals(name))
                .findFirst();
    }
}
