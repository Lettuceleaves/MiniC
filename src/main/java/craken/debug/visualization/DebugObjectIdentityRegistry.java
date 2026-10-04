package craken.debug.visualization;

import java.util.*;

/** Draftable identity and reference ledger. L is the immutable visualization location value. */
public final class DebugObjectIdentityRegistry<L> {
    public record ObjectKey(long allocationId, int offset, String descriptorKey) {
        public ObjectKey {
            if (allocationId <= 0 || offset < 0 || descriptorKey == null || descriptorKey.isBlank()) {
                throw new IllegalArgumentException("Object identity needs allocation, offset and descriptor key");
            }
        }
        public DebugMemoryReader.Address address() { return new DebugMemoryReader.Address(allocationId, offset); }
    }
    public record ReferenceKey(ObjectKey source, String field) {
        public ReferenceKey { Objects.requireNonNull(source); if (field == null || field.isBlank()) throw new IllegalArgumentException("Empty reference field"); }
    }
    public record PointerTarget(long rawAddress, DebugMemoryReader.Address target) {}

    private final Map<Long, RuntimeEvent.MemoryRange> live = new LinkedHashMap<>();
    private final NavigableMap<Long, RuntimeEvent.MemoryRange> addresses = new TreeMap<>();
    private final Map<ObjectKey, L> locations = new LinkedHashMap<>();
    private final Map<ReferenceKey, PointerTarget> references = new LinkedHashMap<>();
    private long lastSequence;

    public DebugObjectIdentityRegistry<L> copy() {
        DebugObjectIdentityRegistry<L> result = new DebugObjectIdentityRegistry<>();
        result.live.putAll(live);
        result.addresses.putAll(addresses);
        result.locations.putAll(locations);
        result.references.putAll(references);
        result.lastSequence = lastSequence;
        return result;
    }

    public Map<ObjectKey, L> locations() { return Collections.unmodifiableMap(new LinkedHashMap<>(locations)); }
    public Optional<L> location(ObjectKey key) { return Optional.ofNullable(locations.get(key)); }
    public void put(ObjectKey key, L location) {
        requireLive(key.address());
        locations.put(key, Objects.requireNonNull(location));
    }

    public void observe(RuntimeEvent event) {
        if (event.sequence() <= lastSequence) throw new IllegalArgumentException("Events must be consumed once in sequence order");
        if (event instanceof RuntimeEvent.Allocated allocated) {
            RuntimeEvent.MemoryRange next = allocated.range();
            if (live.containsKey(next.allocationId()) || next.address() > Long.MAX_VALUE - next.size()) {
                throw new IllegalArgumentException("Duplicate allocation identity or invalid address range");
            }
            var before = addresses.floorEntry(next.address());
            var after = addresses.ceilingEntry(next.address());
            if (before != null && before.getValue().address() + before.getValue().size() > next.address()
                    || after != null && after.getKey() < next.address() + next.size()) {
                throw new IllegalArgumentException("Overlapping allocation events");
            }
            live.put(next.allocationId(), next);
            addresses.put(next.address(), next);
        }
        if (event instanceof RuntimeEvent.Released released) {
            RuntimeEvent.MemoryRange removed = live.remove(released.range().allocationId());
            if (removed != null) addresses.remove(removed.address());
            pruneDeadObjects();
        }
        lastSequence = event.sequence();
    }

    public void reconcile(DebugMemoryReader reader) {
        Map<Long, RuntimeEvent.MemoryRange> actual = new LinkedHashMap<>();
        for (var block : reader.allocations()) {
            if (block.allocationId() <= 0) throw new IllegalArgumentException("Identity metadata is required");
            actual.put(block.allocationId(), new RuntimeEvent.MemoryRange(block.allocationId(), block.address(), block.size()));
        }
        live.clear();
        live.putAll(actual);
        addresses.clear();
        actual.values().forEach(range -> addresses.put(range.address(), range));
        pruneDeadObjects();
    }

    public Optional<DebugMemoryReader.Address> resolve(long address) {
        var candidate = addresses.floorEntry(address);
        if (candidate != null && address - candidate.getKey() < candidate.getValue().size()) {
            return Optional.of(new DebugMemoryReader.Address(candidate.getValue().allocationId(), (int) (address - candidate.getKey())));
        }
        return Optional.empty();
    }

    public void rememberReference(ReferenceKey key, long rawAddress) {
        requireLive(key.source().address());
        references.put(key, new PointerTarget(rawAddress, resolve(rawAddress).orElse(null)));
    }
    public Optional<PointerTarget> reference(ReferenceKey key) { return Optional.ofNullable(references.get(key)); }
    public void forgetReference(ReferenceKey key) { references.remove(key); }
    public Optional<DebugMemoryReader.Address> referenceTarget(ReferenceKey key) {
        PointerTarget pointer = references.get(key);
        if (pointer == null || pointer.target() == null) return Optional.empty();
        RuntimeEvent.MemoryRange target = live.get(pointer.target().allocationId());
        return target == null || pointer.target().offset() >= target.size() ? Optional.empty() : Optional.of(pointer.target());
    }
    public long lastSequence() { return lastSequence; }

    /** Reconstruct the allocation table at the start of a stop interval from its immutable end image. */
    public void beginInterval(DebugMemoryReader reader, List<RuntimeEvent> events) {
        Map<Long, RuntimeEvent.MemoryRange> before = new LinkedHashMap<>();
        for (var block : reader.allocations()) {
            if (block.allocationId() <= 0) throw new IllegalArgumentException("Identity metadata is required");
            before.put(block.allocationId(), new RuntimeEvent.MemoryRange(block.allocationId(), block.address(), block.size()));
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            var event = events.get(i);
            if (event instanceof RuntimeEvent.Allocated a) before.remove(a.range().allocationId());
            if (event instanceof RuntimeEvent.Released r) before.put(r.range().allocationId(), r.range());
        }
        live.clear(); live.putAll(before); addresses.clear();
        before.values().forEach(range -> addresses.put(range.address(), range));
    }

    public void replaceLocations(Map<ObjectKey, L> replacement) {
        locations.clear();
        replacement.forEach(this::put);
    }

    private void requireLive(DebugMemoryReader.Address address) {
        RuntimeEvent.MemoryRange range = live.get(address.allocationId());
        if (range == null || address.offset() >= range.size()) throw new IllegalArgumentException("Object allocation is no longer live");
    }

    private void pruneDeadObjects() {
        locations.keySet().removeIf(key -> !isLive(key.address()));
        references.keySet().removeIf(key -> !isLive(key.source().address()));
        // Captured targets remain in surviving fields as stale generations. Never resolve their raw address again.
    }
    private boolean isLive(DebugMemoryReader.Address address) {
        RuntimeEvent.MemoryRange range = live.get(address.allocationId());
        return range != null && address.offset() < range.size();
    }
}
