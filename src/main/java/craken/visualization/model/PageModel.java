package craken.visualization.model;

import craken.visualization.api.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PageModel(PageRef ref, PageType type, Map<Long, ViewNode> nodes, ViewLocation anchor) {
    public PageModel {
        Objects.requireNonNull(ref);
        Objects.requireNonNull(type);
        nodes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    }
    public PageModel withNode(ViewNode node) {
        var updated = new LinkedHashMap<>(nodes);
        updated.put(node.location().nodeId(), node);
        return new PageModel(ref, type, updated, anchor);
    }
}
