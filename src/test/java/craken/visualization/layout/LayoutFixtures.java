package craken.visualization.layout;

import craken.visualization.api.*;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;

final class LayoutFixtures {
    static ViewLocation id(long n) { return new ViewLocation(1, 1, n); }
    static Unit node(long n, double width, double height) { return Unit.simple(id(n), width, height); }
    static Link edge(long n, long a, long b) { return new Link(n, PortRef.node(id(a)), PortRef.node(id(b)), Direction.FORWARD); }
    static LayoutRequest request(Kind kind, List<Unit> nodes, List<Link> edges, Hints hints) {
        return new LayoutRequest(new Stamp(new PageRef(1, 1), 1, 3, 2, 5, 800), kind, nodes, edges, hints, Map.of());
    }
    static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() - 1e-7 && b.x() < a.right() - 1e-7
                && a.y() < b.bottom() - 1e-7 && b.y() < a.bottom() - 1e-7;
    }
}
