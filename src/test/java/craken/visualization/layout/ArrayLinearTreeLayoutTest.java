package craken.visualization.layout;

import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static craken.visualization.layout.LayoutRequest.*;
import static craken.visualization.layout.LayoutFixtures.*;

@Tag("visualization-layout")
final class ArrayLinearTreeLayoutTest {
    @Test void arraysHonorVariableMeasurementsAndColumns() {
        var hints = new Hints(8, 12, 4, 2, Orientation.HORIZONTAL, null, List.of());
        var request = request(Kind.ARRAY, List.of(node(1, 60, 30), node(2, 120, 40), node(3, 80, 50)), List.of(), hints);
        var result = new ArrayLayout().layout(request);
        assertEquals(new Rect(4, 4, 60, 30), result.nodeBounds().get(id(1)));
        assertEquals(new Rect(92, 4, 120, 40), result.nodeBounds().get(id(2)));
        assertEquals(new Rect(4, 56, 80, 50), result.nodeBounds().get(id(3)));
        assertEquals(new Rect(0, 0, 216, 110), result.contentBounds());
        assertEquals(request.stamp(), result.stamp());
    }
    @Test void linearLayoutPreservesExplicitOrderAndActualWidths() {
        var hints = new Hints(10, 20, 4, 1, Orientation.HORIZONTAL, null, List.of(id(3), id(1), id(2)));
        var request = request(Kind.LINEAR, List.of(node(1, 60, 30), node(2, 120, 40), node(3, 80, 50)),
                List.of(edge(1, 1, 2), edge(2, 2, 3)), hints);
        var result = new LinearLayout().layout(request);
        assertEquals(4, result.nodeBounds().get(id(3)).x());
        assertEquals(94, result.nodeBounds().get(id(1)).x());
        assertEquals(164, result.nodeBounds().get(id(2)).x());
        assertEquals(2, result.edgePaths().size());
    }
    @Test void treeUsesUndirectedTopologyAndStableEntryWithoutPointerRoles() {
        var hints = new Hints(16, 30, 8, 1, Orientation.VERTICAL, id(2), List.of(id(3), id(1)));
        var request = request(Kind.TREE, List.of(node(3, 110, 40), node(1, 60, 30), node(2, 80, 50)),
                List.of(edge(1, 1, 2), edge(2, 3, 2)), hints);
        var result = new TreeLayout().layout(request);
        assertEquals(8, result.nodeBounds().get(id(2)).y());
        assertEquals(88, result.nodeBounds().get(id(1)).y());
        assertTrue(result.nodeBounds().get(id(3)).x() < result.nodeBounds().get(id(1)).x());
        for (var a : result.nodeBounds().entrySet()) for (var b : result.nodeBounds().entrySet())
            if (!a.getKey().equals(b.getKey())) assertFalse(overlaps(a.getValue(), b.getValue()));
        assertEquals(result, new TreeLayout().layout(request));
    }
    @Test void rejectsGraphCyclesRatherThanSilentlyChoosingGraphLayout() {
        var request = request(Kind.TREE, List.of(node(1, 60, 30), node(2, 60, 30), node(3, 60, 30)),
                List.of(edge(1, 1, 2), edge(2, 2, 3), edge(3, 3, 1)), Hints.defaults());
        assertEquals(LayoutException.Code.INVALID_TOPOLOGY,
                assertThrows(LayoutException.class, () -> new TreeLayout().layout(request)).code());
    }
    @Test void deepTreesUseIterativeTraversal() {
        var nodes = new ArrayList<Unit>();
        var edges = new ArrayList<Link>();
        for (int i = 1; i <= 3000; i++) {
            nodes.add(node(i, 20, 12));
            if (i > 1) edges.add(edge(i, i - 1, i));
        }
        var result = new TreeLayout().layout(request(Kind.TREE, nodes, edges, Hints.defaults()));
        assertEquals(3000, result.nodeBounds().size());
        assertTrue(result.nodeBounds().get(id(3000)).y() > result.nodeBounds().get(id(1)).y());
    }
    @Test void emptyPartsAndCancellationAreExplicit() {
        for (LayoutEngine engine : List.of(new ArrayLayout(), new LinearLayout(), new TreeLayout())) {
            var empty = request(Kind.POINT, List.of(), List.of(), Hints.defaults());
            assertEquals(new Rect(0, 0, 0, 0), engine.layout(empty).contentBounds());
            assertEquals(LayoutException.Code.CANCELLED,
                    assertThrows(LayoutException.class, () -> engine.layout(empty, () -> true)).code());
        }
    }
}
