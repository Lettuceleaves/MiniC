package craken.visualization.model;

import craken.visualization.api.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import craken.visualization.model.relation.CompositionLink;

public record PageModel(PageRef ref, PageType type, Map<Long, ViewNode> nodes, ViewLocation anchor,
                        Map<Long, CompositionLink> composition) {
    public PageModel {
        Objects.requireNonNull(ref);
        Objects.requireNonNull(type);
        nodes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        composition = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(composition));
    }
    public PageModel(PageRef ref, PageType type, Map<Long, ViewNode> nodes, ViewLocation anchor) {
        this(ref, type, nodes, anchor, Map.of());
    }
    public PageModel withNode(ViewNode node) {
        var updated = new LinkedHashMap<>(nodes);
        updated.put(node.location().nodeId(), node);
        return new PageModel(ref, type, updated, anchor, composition);
    }
}
