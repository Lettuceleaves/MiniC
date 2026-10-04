package craken.debug.visualization;

import craken.debug.DebugRuntime.MemoryBlock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static craken.debug.visualization.DebugObjectIdentityRegistry.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class DebugObjectIdentityRegistryTest {
    @Test
    void identityIncludesTheAllocationOffsetAndManualInterpretation() {
        DebugObjectIdentityRegistry<String> identities = new DebugObjectIdentityRegistry<>();
        identities.observe(allocated(1, 1, 100, 16));
        ObjectKey array = new ObjectKey(1, 0, "array"), first = new ObjectKey(1, 0, "element"), second = new ObjectKey(1, 4, "element");
        identities.put(array, "array location");
        identities.put(first, "first element location");
        identities.put(second, "second element location");
        assertEquals(3, identities.locations().size());
        assertEquals("first element location", identities.location(first).orElseThrow());
        assertEquals(new DebugMemoryReader.Address(1, 4), identities.resolve(104).orElseThrow());
    }

    @Test
    void freeingAndReusingAnAddressNeverRetargetsAnUntouchedOldPointer() {
        DebugObjectIdentityRegistry<String> identities = new DebugObjectIdentityRegistry<>();
        identities.observe(allocated(1, 1, 100, 8));
        identities.observe(allocated(2, 10, 200, 8));
        ObjectKey source = new ObjectKey(10, 0, "singleton"), old = new ObjectKey(1, 0, "tree");
        identities.put(old, "old location");
        ReferenceKey field = new ReferenceKey(source, "root");
        identities.rememberReference(field, 100);
        assertEquals(new DebugMemoryReader.Address(1, 0), identities.referenceTarget(field).orElseThrow());
        identities.observe(new RuntimeEvent.Released(3, new RuntimeEvent.MemoryRange(1, 100, 8)));
        identities.observe(allocated(4, 2, 100, 8));
        assertTrue(identities.location(old).isEmpty());
        assertTrue(identities.referenceTarget(field).isEmpty());
        assertEquals(new DebugMemoryReader.Address(1, 0), identities.reference(field).orElseThrow().target());
        // A real rewrite, including a same-value write, explicitly resolves the new generation.
        identities.rememberReference(field, 100);
        assertEquals(new DebugMemoryReader.Address(2, 0), identities.referenceTarget(field).orElseThrow());
    }

    @Test
    void draftsDoNotChangeThePublishedMappingUntilTheAdapterAcceptsThem() {
        DebugObjectIdentityRegistry<String> published = new DebugObjectIdentityRegistry<>();
        published.observe(allocated(1, 1, 100, 8));
        ObjectKey key = new ObjectKey(1, 0, "point");
        published.put(key, "published");
        DebugObjectIdentityRegistry<String> draft = published.copy();
        draft.put(key, "draft");
        assertEquals("published", published.location(key).orElseThrow());
        assertEquals("draft", draft.location(key).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> published.locations().clear());
    }

    @Test
    void completeSnapshotsReconcileLifetimesButPreserveStaleReferenceGenerations() {
        DebugObjectIdentityRegistry<String> identities = new DebugObjectIdentityRegistry<>();
        identities.observe(allocated(1, 1, 100, 8));
        identities.observe(allocated(2, 10, 200, 8));
        ObjectKey source = new ObjectKey(10, 0, "point"), old = new ObjectKey(1, 0, "tree");
        identities.put(old, "old");
        ReferenceKey reference = new ReferenceKey(source, "root");
        identities.rememberReference(reference, 100);
        identities.reconcile(new DebugMemoryReader(List.of(block(2, 100, 8), block(10, 200, 8))));
        assertTrue(identities.location(old).isEmpty());
        assertTrue(identities.referenceTarget(reference).isEmpty());
    }

    @Test
    void eventsCanBeConsumedOnlyOnceAndTransientAllocationsLeaveNoFinalMapping() {
        DebugObjectIdentityRegistry<String> identities = new DebugObjectIdentityRegistry<>();
        identities.observe(allocated(1, 1, 100, 8));
        ObjectKey key = new ObjectKey(1, 0, "point");
        identities.put(key, "temporary");
        identities.observe(new RuntimeEvent.Released(2, new RuntimeEvent.MemoryRange(1, 100, 8)));
        assertTrue(identities.locations().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> identities.observe(allocated(1, 1, 100, 8)));
        assertEquals(2, identities.lastSequence());
    }

    private static RuntimeEvent allocated(long sequence, long id, long address, int size) {
        return new RuntimeEvent.Allocated(sequence, new RuntimeEvent.MemoryRange(id, address, size), "heap", "test");
    }
    private static MemoryBlock block(long id, long address, int size) {
        return new MemoryBlock(address, size, "test", "00".repeat(size), size, id, "ff".repeat((size + 7) / 8));
    }
}
