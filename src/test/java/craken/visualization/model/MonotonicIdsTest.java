package craken.visualization.model;

import craken.visualization.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class MonotonicIdsTest {
    @Test void reservationsAreNeverReusedEvenWhenNotCommitted() {
        var ids = new MonotonicIds();
        assertEquals(1, ids.next());
        long failedReservation = ids.next();
        assertEquals(2, failedReservation);
        assertEquals(3, ids.next());
    }
    @Test void restoringAnOldSnapshotDoesNotRewindTheAllocator() {
        var ids = new MonotonicIds();
        ids.observe(20);
        ids.observe(5);
        assertEquals(21, ids.next());
        assertEquals(21, ids.highWater());
    }
    @Test void exhaustedNamespaceFailsInsteadOfWrappingOrReusing() {
        var ids = new MonotonicIds();
        ids.observe(Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, ids::next);
    }
    @Test void namespacesAreIndependentAndPositionsRemainQualified() {
        assertEquals(new MonotonicIds().next(), new MonotonicIds().next());
        var position = new ViewLocation(1, 2, 3);
        assertEquals(new PageRef(1, 2), position.page());
        assertNotEquals(position, new ViewLocation(2, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new ViewLocation(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PageRef(1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationPath(new ViewLocation(2, 1, 1), position));
        assertEquals(position, new OperationPath(null, position).nxt());
    }
}
