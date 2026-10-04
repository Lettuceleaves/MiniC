package craken.visualization.layout;

import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static craken.visualization.layout.LayoutRequest.*;
import static craken.visualization.layout.LayoutFixtures.*;

@Tag("visualization-layout")
final class LayoutProtocolTest {
    @Test void rejectsNonFiniteOrNonPositiveMeasurementsBeforeLayout() {
        assertThrows(IllegalArgumentException.class, () -> new Size(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Size(1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Point(Double.POSITIVE_INFINITY, 0));
    }
    @Test void rejectsDanglingEndpointsAndDuplicateIdentities() {
        assertThrows(IllegalArgumentException.class, () -> request(Kind.GRAPH, List.of(node(1, 60, 30)),
                List.of(edge(1, 1, 2)), Hints.defaults()));
        assertThrows(IllegalArgumentException.class, () -> request(Kind.GRAPH,
                List.of(node(1, 60, 30), node(1, 60, 30)), List.of(), Hints.defaults()));
    }
    @Test void snapshotsListsAndKeepsCancellationOutOfTheGeometryKey() {
        var nodes = new ArrayList<>(List.of(node(1, 60, 30)));
        var request = request(Kind.POINT, nodes, List.of(), Hints.defaults());
        nodes.clear();
        assertEquals(1, request.units().size());
        assertThrows(UnsupportedOperationException.class, () -> request.units().clear());
    }
}
