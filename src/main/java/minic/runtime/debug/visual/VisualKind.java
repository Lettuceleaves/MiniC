package minic.runtime.debug.visual;

import java.util.Locale;
import java.util.Optional;

/**
 * Normalized data-shape kinds accepted by the visual spec DSL.
 */
public enum VisualKind {
    AUTO,
    SCALAR,
    POINTER,
    POINTER_CHAIN,
    ARRAY,
    MATRIX,
    POINTER_ARRAY,
    STRUCT,
    STRUCT_POINTER,
    STRUCT_POINTER_CHAIN,
    STRUCT_ARRAY,
    STRUCT_MATRIX,
    STRUCT_LIST,
    STRING,
    STACK,
    QUEUE,
    DEQUE,
    CIRCULAR_QUEUE,
    HEAP,
    FENWICK_TREE,
    DSU,
    RECORD_TABLE,
    SINGLY_LIST,
    DOUBLY_LIST,
    LRU_LIST,
    BINARY_TREE,
    GENERAL_TREE,
    TRIE,
    SEGMENT_TREE,
    HASH_CHAIN_TABLE,
    ADJACENCY_LIST,
    GRAPH,
    PERSISTENT_TREE;

    public static Optional<VisualKind> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(AUTO);
        }
        String normalized = value.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return Optional.of(VisualKind.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
