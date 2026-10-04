package craken.visualization.snapshot;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.*;
import java.util.*;

/** Pure immutable values: no GUI, page factories, compiler objects or VM references. */
public record VisualizationSnapshot(long containerId, PageRef root, Map<Long,PageState> pages,
        Map<OwnershipBinding.Key,OwnershipBinding> ownership, Map<Long,PageBindingRule> pageRules,
        InteractionState interaction, long sourceVersion, long epoch, String sourceStep, HighWater highWater) {
    public VisualizationSnapshot {
        pages=ordered(pages); ownership=ordered(ownership); pageRules=ordered(pageRules);
        Objects.requireNonNull(interaction); Objects.requireNonNull(highWater);
    }
    private static <K,V> Map<K,V> ordered(Map<K,V> values) { return Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    public record TypeDescription(String key,PageType.Layout layout,boolean readyEnabled,int maximumNesting,Set<ViewNode.Kind> nodeKinds) {
        public TypeDescription { Objects.requireNonNull(key); Objects.requireNonNull(layout); nodeKinds=Set.copyOf(nodeKinds); }
        public static TypeDescription of(PageType type) { return new TypeDescription(type.key(),type.layout(),type.readyEnabled(),type.maximumNesting(),type.nodeKinds()); }
    }
    public record NodeState(ViewLocation location,ViewNode.Spec content,ViewNode.Retention retention,
                            ParentSelection parents,Set<ViewLocation> highlights) {
        public NodeState { Objects.requireNonNull(location); Objects.requireNonNull(content); Objects.requireNonNull(retention); Objects.requireNonNull(parents); highlights=Set.copyOf(highlights); }
    }
    public record PageState(PageRef ref,TypeDescription type,Map<Long,NodeState> nodes,ViewLocation anchor,
                            Map<Long,CompositionLink> composition,Map<Long,TopologyEdge> topology,Set<Long> ready) {
        public PageState { Objects.requireNonNull(ref); Objects.requireNonNull(type); nodes=ordered(nodes); composition=ordered(composition); topology=ordered(topology); ready=Set.copyOf(ready); }
    }
    public record HighWater(long page,long relation,Map<Long,Long> nodes) {
        public HighWater { if (page<0 || relation<0 || nodes.values().stream().anyMatch(n->n<0)) throw new IllegalArgumentException("Negative high water"); nodes=ordered(nodes); }
    }
}
