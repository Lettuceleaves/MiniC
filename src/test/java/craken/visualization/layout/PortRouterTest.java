package craken.visualization.layout;

import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static craken.visualization.layout.LayoutRequest.*;
import static craken.visualization.layout.LayoutFixtures.*;

@Tag("visualization-layout")
final class PortRouterTest {
    @Test void avoidsTheUnrelatedMiddleCard() {
        var request = request(Kind.LINEAR, List.of(node(1, 60, 30), node(2, 60, 30), node(3, 60, 30)),
                List.of(edge(1, 1, 3)), Hints.defaults());
        var result = new LinearLayout().layout(request);
        var middle = result.nodeBounds().get(id(2));
        var route = result.edgePaths().get(1L); var previous = route.start();
        for (var segment : route.segments()) {
            assertFalse(PortRouter.crosses(previous, segment.end(), middle)); previous = segment.end();
        }
        assertTrue(route.segments().size() > 1);
    }
    @Test void anchorsAnArraySlotInsteadOfTheArrayCenter() {
        var outer = id(1); var cell = id(2); var target = id(3);
        var cellPort = new PortRef(cell, "value");
        var unit = new Unit(outer, new Size(100, 60),
                List.of(new Member(outer, new Rect(0, 0, 100, 60)), new Member(cell, new Rect(40, 20, 30, 30))),
                List.of(new Port(cellPort, new Point(70, 35), Side.EAST)), List.of(new Rect(45, 25, 15, 15)));
        var request = request(Kind.LINEAR, List.of(unit, node(3, 60, 30)),
                List.of(new Link(1, cellPort, PortRef.node(target), Direction.NONE)), Hints.defaults());
        var result = new LinearLayout().layout(request);
        assertEquals(new Point(78, 43), result.edgePaths().get(1L).start());
        assertEquals(new Rect(48, 28, 30, 30), result.nodeBounds().get(cell));
    }
    @Test void explicitSelfLoopReturnsToTheSamePort() {
        var port = new PortRef(id(1), "slot");
        var unit = new Unit(id(1), new Size(60, 30), List.of(new Member(id(1), new Rect(0, 0, 60, 30))),
                List.of(new Port(port, new Point(60, 15), Side.EAST)), List.of());
        var result = new LinearLayout().layout(request(Kind.LINEAR, List.of(unit),
                List.of(new Link(1, port, port, Direction.FORWARD)), Hints.defaults()));
        assertEquals(new Point(68, 23), result.edgePaths().get(1L).start());
        assertEquals(result.edgePaths().get(1L).start(), result.edgePaths().get(1L).end());
        assertTrue(result.edgePaths().get(1L).segments().size() >= 3);
    }
    @Test void rejectsPortsOutsideTheirMemberAndFixedPortsInsideItsBody() {
        var members = List.of(new Member(id(1), new Rect(0, 0, 100, 60)), new Member(id(2), new Rect(20, 20, 30, 30)));
        assertThrows(IllegalArgumentException.class, () -> new Unit(id(1), new Size(100, 60), members,
                List.of(new Port(new PortRef(id(2), "slot"), new Point(90, 30), Side.EAST)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Unit(id(1), new Size(100, 60), members,
                List.of(new Port(new PortRef(id(2), "slot"), new Point(35, 35), Side.EAST)), List.of()));
    }
    @Test void rejectsFiniteInputsWhoseRectangleExtentsOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new Rect(Double.MAX_VALUE, 0, Double.MAX_VALUE, 1));
    }
    @Test void selfLoopAvoidsTheAdjacentCardAtItsFixedPort() {
        var port = new PortRef(id(1), "slot");
        var unit = new Unit(id(1), new Size(60, 30), List.of(new Member(id(1), new Rect(0, 0, 60, 30))),
                List.of(new Port(port, new Point(60, 15), Side.EAST)), List.of());
        var hints = new Hints(4, 4, 8, 1, Orientation.HORIZONTAL, null, List.of());
        var result = new LinearLayout().layout(request(Kind.LINEAR, List.of(unit, node(2, 60, 30)),
                List.of(new Link(1, port, port, Direction.FORWARD)), hints));
        var other = result.nodeBounds().get(id(2));
        var route = result.edgePaths().get(1L); var previous = route.start();
        for (var segment : route.segments()) {
            assertFalse(PortRouter.crosses(previous, segment.end(), other)); previous = segment.end();
        }
        assertEquals(route.start(), route.end());
    }
    @Test void routesCellPortsThroughTheirContainingRowFrameButAroundSiblings() {
        var cell = new PortRef(id(3), "field:next");
        var unit = new Unit(id(1), new Size(180, 120), List.of(
                new Member(id(1), new Rect(0, 0, 180, 120)), new Member(id(2), new Rect(10, 20, 160, 80)),
                new Member(id(3), new Rect(20, 40, 50, 30)), new Member(id(4), new Rect(90, 40, 50, 30))),
                List.of(new Port(cell, new Point(70, 55), Side.EAST)), List.of(new Rect(30, 45, 25, 12)));
        var result = new LinearLayout().layout(request(Kind.LINEAR, List.of(unit, node(5, 60, 30)),
                List.of(new Link(1, cell, PortRef.node(id(5)), Direction.FORWARD)), Hints.defaults()));
        var route = result.edgePaths().get(1L); var previous = route.start();
        for (var segment : route.segments()) {
            assertFalse(PortRouter.crosses(previous, segment.end(), result.nodeBounds().get(id(4)))); previous = segment.end();
        }
        assertEquals(new Point(78, 63), route.start());
    }
    @Test void aFixedEastPortDoesNotExitThroughItsOwnCardWhenTargetIsToTheWest() {
        var east = new PortRef(id(2), "east");
        var unit = new Unit(id(2), new Size(60, 30), List.of(new Member(id(2), new Rect(0, 0, 60, 30))),
                List.of(new Port(east, new Point(60, 15), Side.EAST)), List.of());
        var result = new LinearLayout().layout(request(Kind.LINEAR, List.of(node(1, 60, 30), unit),
                List.of(new Link(1, east, PortRef.node(id(1)), Direction.FORWARD)), Hints.defaults()));
        var route = result.edgePaths().get(1L); var previous = route.start();
        for (var segment : route.segments()) {
            assertFalse(PortRouter.crosses(previous, segment.end(), result.nodeBounds().get(id(2)))); previous = segment.end();
        }
    }
}
