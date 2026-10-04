package craken.visualization.mutation;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.MonotonicIds;
import craken.visualization.model.relation.OwnershipBinding;
import java.util.*;

/** Mutable transaction-local indexes for one directed relationship with multiple provenance sources. */
public final class OwnershipStore {
    private final Map<OwnershipBinding.Key, OwnershipBinding> bindings = new LinkedHashMap<>();
    private final Map<ViewLocation, LinkedHashSet<ViewLocation>> incoming = new HashMap<>();
    private final Map<ViewLocation, LinkedHashSet<ViewLocation>> outgoing = new HashMap<>();
    private final MonotonicIds ids;
    public OwnershipStore(Map<OwnershipBinding.Key, OwnershipBinding> initial, MonotonicIds ids) {
        this.ids = ids;
        initial.values().forEach(binding -> {
            bindings.put(binding.key(), binding);
            index(binding.key());
        });
    }
    private void index(OwnershipBinding.Key key) {
        incoming.computeIfAbsent(key.nxt(), unused -> new LinkedHashSet<>()).add(key.pre());
        outgoing.computeIfAbsent(key.pre(), unused -> new LinkedHashSet<>()).add(key.nxt());
    }
    public void add(ViewLocation pre, ViewLocation nxt, String source) {
        var key = new OwnershipBinding.Key(pre, nxt);
        var previous = bindings.get(key);
        var sources = new LinkedHashSet<String>();
        if (previous != null) sources.addAll(previous.sources());
        sources.add(source);
        bindings.put(key, new OwnershipBinding(previous == null ? ids.next() : previous.id(), key, sources));
        index(key);
    }
    public Set<ViewLocation> parents(ViewLocation location) {
        return Set.copyOf(incoming.getOrDefault(location, new LinkedHashSet<>()));
    }
    public Set<ViewLocation> children(ViewLocation location) {
        return Set.copyOf(outgoing.getOrDefault(location, new LinkedHashSet<>()));
    }
    public Map<OwnershipBinding.Key, OwnershipBinding> bindings() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }
    public void removeSource(ViewLocation pre, ViewLocation nxt, String source) {
        var key = new OwnershipBinding.Key(pre, nxt);
        var previous = bindings.get(key);
        if (previous == null || !previous.sources().contains(source)) return;
        var sources = new LinkedHashSet<>(previous.sources());
        sources.remove(source);
        if (sources.isEmpty()) remove(key);
        else bindings.put(key, new OwnershipBinding(previous.id(), key, sources));
    }
    public void removeNode(ViewLocation location) {
        for (var pre : parents(location)) remove(new OwnershipBinding.Key(pre, location));
        for (var nxt : children(location)) remove(new OwnershipBinding.Key(location, nxt));
    }
    private void remove(OwnershipBinding.Key key) {
        if (bindings.remove(key) == null) return;
        var parents = incoming.get(key.nxt());
        parents.remove(key.pre());
        if (parents.isEmpty()) incoming.remove(key.nxt());
        var children = outgoing.get(key.pre());
        children.remove(key.nxt());
        if (children.isEmpty()) outgoing.remove(key.pre());
    }
}
