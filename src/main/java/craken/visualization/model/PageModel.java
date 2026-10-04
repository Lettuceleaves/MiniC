package craken.visualization.model;

import craken.visualization.api.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import craken.visualization.model.relation.CompositionLink;
import craken.visualization.model.relation.TopologyEdge;
import java.util.Set;
import java.util.HashSet;

public record PageModel(PageRef ref, PageType type, Map<Long, ViewNode> nodes, ViewLocation anchor,
                        Map<Long, CompositionLink> composition, Map<Long, TopologyEdge> topology,
                        Set<Long> ready, Map<Long, Part> parts, Map<Long, Long> membership, PageLayoutHints layoutHints) {
    public PageModel {
        Objects.requireNonNull(ref);
        Objects.requireNonNull(type);
        Objects.requireNonNull(layoutHints);
        nodes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        composition = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(composition));
        topology = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(topology));
        ready = Set.copyOf(ready);
        parts = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(parts));
        membership = Map.copyOf(membership);
    }
    public PageModel(PageRef ref, PageType type, Map<Long, ViewNode> nodes, ViewLocation anchor,
                     Map<Long, CompositionLink> composition, Map<Long, TopologyEdge> topology,
                     Set<Long> ready, Map<Long, Part> parts, Map<Long, Long> membership) {
        this(ref, type, nodes, anchor, composition, topology, ready, parts, membership, PageLayoutHints.EMPTY);
    }
    public PageModel(PageRef ref, PageType type, Map<Long, ViewNode> nodes, ViewLocation anchor) {
        this(ref, type, nodes, anchor, Map.of());
    }
    public PageModel(PageRef ref, PageType type, Map<Long, ViewNode> nodes, ViewLocation anchor,
                     Map<Long, CompositionLink> composition) {
        this(ref, type, nodes, anchor, composition, Map.of(), Set.of(), Map.of(), Map.of());
    }
    public PageModel withNode(ViewNode node) {
        var updated = new LinkedHashMap<>(nodes);
        updated.put(node.location().nodeId(), node);
        return new PageModel(ref, type, updated, anchor, composition, topology, ready, parts, membership, layoutHints);
    }
    public PageModel withAllocatedNode(ViewNode node) {
        var updated = new LinkedHashMap<>(nodes);
        updated.put(node.location().nodeId(), node);
        var nextReady = new HashSet<>(ready);
        if (type.readyEnabled() && !nodes.isEmpty()) nextReady.add(node.location().nodeId());
        return new PageModel(ref, type, updated, anchor, composition, topology, nextReady, parts, membership, layoutHints);
    }
    public PageModel withComposition(Map<Long, ViewNode> updated, Map<Long, CompositionLink> links) {
        return new PageModel(ref, type, updated, anchor, links, topology, ready, parts, membership, layoutHints);
    }
    public PageModel withTopology(Map<Long, TopologyEdge> edges, Set<Long> nextReady) {
        return new PageModel(ref, type, nodes, anchor, composition, edges, nextReady, parts, membership, layoutHints);
    }
    public PageModel withLayoutHints(PageLayoutHints hints) {
        return new PageModel(ref, type, nodes, anchor, composition, topology, ready, parts, membership, hints);
    }
}
