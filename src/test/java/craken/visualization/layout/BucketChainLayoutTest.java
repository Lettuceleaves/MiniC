package craken.visualization.layout;

import craken.visualization.api.ViewLocation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static craken.visualization.layout.LayoutFixtures.*;
import static craken.visualization.layout.LayoutRequest.*;
import static org.junit.jupiter.api.Assertions.*;

/** The chained bucket layout keeps every chain on the row of its own bucket cell. */
@Tag("visualization-layout")
final class BucketChainLayoutTest {
    private static final ViewLocation CONTAINER = id(1);

    private static Unit bucketList(int slots, double slotHeight) {
        var members = new ArrayList<Member>();
        var ports = new ArrayList<Port>();
        members.add(new Member(CONTAINER, new Rect(0, 0, 140, slots * slotHeight)));
        ports.add(new Port(PortRef.node(CONTAINER), new Point(70, slots * slotHeight / 2), Side.AUTO));
        for (int index = 0; index < slots; index++) {
            var location = id(2 + index);
            var bounds = new Rect(0, index * slotHeight, 140, slotHeight);
            members.add(new Member(location, bounds));
            ports.add(new Port(PortRef.node(location), bounds.center(), Side.AUTO));
            ports.add(new Port(new PortRef(location, "east"), new Point(bounds.right(), bounds.center().y()), Side.EAST));
        }
        return new Unit(CONTAINER, new Size(140, slots * slotHeight), members, ports, List.of());
    }

    @Test void everyChainSharesTheRowOfItsBucketCell() {
        var units = new ArrayList<Unit>();
        units.add(bucketList(3, 60));
        units.add(node(10, 80, 40));
        units.add(node(11, 90, 50));
        units.add(node(20, 70, 40));
        var links = List.of(
                new Link(1, PortRef.node(id(2)), PortRef.node(id(10)), Direction.FORWARD),
                new Link(2, PortRef.node(id(10)), PortRef.node(id(11)), Direction.FORWARD),
                new Link(3, PortRef.node(id(3)), PortRef.node(id(20)), Direction.FORWARD));
        var hints = new Hints(24, 40, 8, 3, Orientation.HORIZONTAL, CONTAINER, List.of());
        var result = new BucketChainLayout().layout(request(Kind.BUCKETS, units, links, hints));
        var container = result.nodeBounds().get(CONTAINER);
        var first = result.nodeBounds().get(id(10));
        var second = result.nodeBounds().get(id(11));
        var third = result.nodeBounds().get(id(20));
        assertEquals(container.right() + 24, first.x(), 1e-7, "the first chain column starts right of the bucket list");
        assertEquals(container.y() + 30, first.center().y(), 1e-7, "chain one stays on bucket row zero");
        assertEquals(container.y() + 90, third.center().y(), 1e-7, "the second bucket owns its own row");
        assertTrue(second.x() > first.right(), "longer chains advance to the right");
        assertEquals(second.center().y(), first.center().y(), 1e-7, "one chain keeps a single row");
        assertEquals(3, result.edgePaths().size());
        assertEquals("buckets", result.engine());
        for (var a : units) for (var b : units) if (!a.node().equals(b.node()))
            assertFalse(overlaps(result.nodeBounds().get(a.node()), result.nodeBounds().get(b.node())),
                    a.node() + " overlaps " + b.node());
    }

    @Test void unitsWithoutABucketRowStayVisibleBelowTheList() {
        var units = new ArrayList<Unit>();
        units.add(bucketList(2, 60));
        units.add(node(10, 80, 40));
        units.add(node(30, 80, 40));
        var links = List.of(new Link(1, PortRef.node(id(2)), PortRef.node(id(10)), Direction.FORWARD));
        var hints = new Hints(24, 40, 8, 2, Orientation.HORIZONTAL, CONTAINER, List.of());
        var result = new BucketChainLayout().layout(request(Kind.BUCKETS, units, links, hints));
        var container = result.nodeBounds().get(CONTAINER);
        var trailing = result.nodeBounds().get(id(30));
        assertTrue(trailing.y() >= container.bottom(), "unlinked units do not cover the bucket list");
        assertFalse(overlaps(trailing, result.nodeBounds().get(id(10))));
        assertTrue(result.contentBounds().bottom() >= trailing.bottom());
    }

    @Test void pagesWithoutSlotsFallBackToTheMeasuredGrid() {
        var units = List.of(node(1, 60, 30), node(2, 70, 30), node(3, 80, 30));
        var hints = new Hints(8, 12, 4, 3, Orientation.HORIZONTAL, id(1), List.of());
        var result = new BucketChainLayout().layout(request(Kind.BUCKETS, units, List.of(), hints));
        assertEquals(new ArrayLayout().layout(request(Kind.BUCKETS, units, List.of(), hints)).nodeBounds(), result.nodeBounds());
    }
}
