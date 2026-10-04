package craken.visualization.layout;

import craken.visualization.api.ViewLocation;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;
import static craken.visualization.layout.LayoutResult.*;

/** Explicit ports are in member-local geometry; automatic node ports intersect the member boundary. */
public final class PortRouter {
    public Map<Long, EdgePath> route(LayoutRequest request, Map<ViewLocation, Rect> bounds, CancellationToken cancellation) {
        var owners = new HashMap<ViewLocation, Unit>();
        var ports = new HashMap<PortRef, Port>();
        for (var unit : request.units()) {
            for (var member : unit.members()) owners.put(member.node(), unit);
            for (var port : unit.ports()) ports.put(port.ref(), port);
        }
        var result = new LinkedHashMap<Long, EdgePath>();
        for (var link : request.links()) {
            cancellation.check();
            var tailUnit = owners.get(link.tail().node()); var headUnit = owners.get(link.head().node());
            var tailBox = bounds.get(link.tail().node()); var headBox = bounds.get(link.head().node());
            var start = anchor(ports.get(link.tail()), tailUnit, bounds, bounds.get(link.head().node()).center());
            var end = anchor(ports.get(link.head()), headUnit, bounds, bounds.get(link.tail().node()).center());
            var obstacles = new ArrayList<Rect>();
            for (var unit : request.units()) {
                if (!unit.node().equals(tailUnit.node()) && !unit.node().equals(headUnit.node())) obstacles.add(bounds.get(unit.node()));
                else {
                    var origin = bounds.get(unit.node());
                    for (var rect : unit.textObstacles()) obstacles.add(new Rect(origin.x() + rect.x(), origin.y() + rect.y(), rect.width(), rect.height()));
                    for (var member : unit.members()) {
                        var memberBox = bounds.get(member.node());
                        // A row/group enclosing an endpoint is a transparent composition frame.
                        if (!member.node().equals(unit.node()) && !encloses(memberBox, tailBox) && !encloses(memberBox, headBox))
                            obstacles.add(memberBox);
                    }
                }
            }
            if (link.tail().equals(link.head())) {
                var box = bounds.get(link.tail().node());
                obstacles.add(box);
                result.put(link.id(), loop(start, ports.get(link.tail()).side(), box, obstacles));
                continue;
            }
            if (link.tail().node().equals(link.head().node())) {
                obstacles.add(tailBox);
                result.put(link.id(), loopBetween(start, ports.get(link.tail()).side(), end,
                        ports.get(link.head()).side(), tailBox, obstacles, cancellation));
                continue;
            }
            // Boundary ports must enter/leave the concrete card from outside. Ancestor frames remain traversable.
            if (!encloses(tailBox, headBox)) obstacles.add(tailBox);
            if (!encloses(headBox, tailBox)) obstacles.add(headBox);
            result.put(link.id(), path(shortest(start, end, obstacles, cancellation)));
        }
        return Map.copyOf(result);
    }
    private static boolean encloses(Rect outer, Rect inner) {
        return outer.contains(new Point(inner.x(), inner.y())) && outer.contains(new Point(inner.right(), inner.bottom()));
    }
    private static EdgePath loopBetween(Point start, Side tailSide, Point end, Side headSide, Rect box,
                                        List<Rect> obstacles, CancellationToken token) {
        var tail = outward(tailSide, start, box); var head = outward(headSide, end, box);
        if (start.equals(end)) return loop(start, tail, box, obstacles);
        for (double gap : new double[]{12, 6, 3, 1}) {
            token.check(); var a = stub(start, tail, gap); var b = stub(end, head, gap);
            if (!clear(List.of(start, a), obstacles) || !clear(List.of(b, end), obstacles)) continue;
            try {
                var points = new ArrayList<Point>(); points.add(start); points.addAll(shortest(a, b, obstacles, token)); points.add(end);
                if (clear(points, obstacles)) return path(points);
            } catch (LayoutException failure) {
                if (failure.code() != LayoutException.Code.INVALID_RESULT) throw failure;
            }
        }
        throw new LayoutException(LayoutException.Code.INVALID_RESULT, "No collision-free route between self-reference ports");
    }
    private static Side outward(Side side, Point anchor, Rect box) {
        if (side != Side.AUTO) return side;
        if (Math.abs(anchor.x() - box.right()) < 1e-7) return Side.EAST;
        if (Math.abs(anchor.x() - box.x()) < 1e-7) return Side.WEST;
        return Math.abs(anchor.y() - box.y()) < 1e-7 ? Side.NORTH : Side.SOUTH;
    }
    private static Point stub(Point point, Side side, double gap) {
        return new Point(point.x() + (side == Side.EAST ? gap : side == Side.WEST ? -gap : 0),
                point.y() + (side == Side.SOUTH ? gap : side == Side.NORTH ? -gap : 0));
    }
    private static EdgePath loop(Point start, Side side, Rect box, List<Rect> obstacles) {
        for (double gap : new double[]{12, 6, 3, 1}) {
            if (side != Side.AUTO) {
                double dx = side == Side.EAST ? 1 : side == Side.WEST ? -1 : 0;
                double dy = side == Side.SOUTH ? 1 : side == Side.NORTH ? -1 : 0;
                for (double sign : new double[]{1, -1}) {
                    double tx = -dy * sign, ty = dx * sign;
                    var candidate = List.of(start, new Point(start.x() + dx * gap, start.y() + dy * gap),
                            new Point(start.x() + dx * gap + tx * gap, start.y() + dy * gap + ty * gap),
                            new Point(start.x() + dx * gap / 2 + tx * gap, start.y() + dy * gap / 2 + ty * gap),
                            new Point(start.x() + dx * gap / 2, start.y() + dy * gap / 2), start);
                    if (clear(candidate, obstacles)) return path(candidate);
                }
            } else {
                var corners = List.of(
                        List.of(new Point(box.right(), box.center().y()), new Point(box.right() + gap, box.center().y()),
                                new Point(box.right() + gap, box.y() - gap), new Point(box.center().x(), box.y() - gap), new Point(box.center().x(), box.y())),
                        List.of(new Point(box.center().x(), box.y()), new Point(box.center().x(), box.y() - gap),
                                new Point(box.x() - gap, box.y() - gap), new Point(box.x() - gap, box.center().y()), new Point(box.x(), box.center().y())),
                        List.of(new Point(box.x(), box.center().y()), new Point(box.x() - gap, box.center().y()),
                                new Point(box.x() - gap, box.bottom() + gap), new Point(box.center().x(), box.bottom() + gap), new Point(box.center().x(), box.bottom())),
                        List.of(new Point(box.center().x(), box.bottom()), new Point(box.center().x(), box.bottom() + gap),
                                new Point(box.right() + gap, box.bottom() + gap), new Point(box.right() + gap, box.center().y()), new Point(box.right(), box.center().y())));
                for (var candidate : corners) if (clear(candidate, obstacles)) return path(candidate);
            }
        }
        throw new LayoutException(LayoutException.Code.INVALID_RESULT, "No collision-free self-loop port route");
    }
    private static Point anchor(Port port, Unit owner, Map<ViewLocation, Rect> bounds, Point toward) {
        var box = bounds.get(port.ref().node());
        if (port.side() != Side.AUTO) {
            var outer = bounds.get(owner.node());
            return new Point(outer.x() + port.anchor().x(), outer.y() + port.anchor().y());
        }
        var center = box.center(); double dx = toward.x() - center.x(), dy = toward.y() - center.y();
        if (dx == 0 && dy == 0) return new Point(box.right(), center.y());
        double factor = 1 / Math.max(Math.abs(dx) / (box.width() / 2), Math.abs(dy) / (box.height() / 2));
        return new Point(center.x() + dx * factor, center.y() + dy * factor);
    }
    private static EdgePath path(List<Point> points) {
        return new EdgePath(points.getFirst(), points.stream().skip(1).map(Line::new).map(s -> (Segment)s).toList());
    }
    private static List<Point> shortest(Point start, Point end, List<Rect> obstacles, CancellationToken token) {
        var direct = List.of(start, end); if (clear(direct, obstacles)) return direct;
        for (var elbow : List.of(new Point(start.x(), end.y()), new Point(end.x(), start.y()))) {
            var candidate = List.of(start, elbow, end); if (clear(candidate, obstacles)) return candidate;
        }
        double left = Math.min(start.x(), end.x()), right = Math.max(start.x(), end.x());
        double top = Math.min(start.y(), end.y()), bottom = Math.max(start.y(), end.y());
        for (var rect : obstacles) { left = Math.min(left, rect.x()); right = Math.max(right, rect.right()); top = Math.min(top, rect.y()); bottom = Math.max(bottom, rect.bottom()); }
        for (double y : new double[]{top - 4, bottom + 4}) {
            var candidate = List.of(start, new Point(start.x(), y), new Point(end.x(), y), end);
            if (clear(candidate, obstacles)) return candidate;
        }
        for (double x : new double[]{left - 4, right + 4}) {
            var candidate = List.of(start, new Point(x, start.y()), new Point(x, end.y()), end);
            if (clear(candidate, obstacles)) return candidate;
        }
        // Sparse corner visibility fallback. Rectangles are expanded to retain clearance.
        var vertices = new LinkedHashSet<Point>(); vertices.add(start); vertices.add(end);
        for (var rect : obstacles) {
            vertices.add(new Point(rect.x() - 2, rect.y() - 2)); vertices.add(new Point(rect.right() + 2, rect.y() - 2));
            vertices.add(new Point(rect.right() + 2, rect.bottom() + 2)); vertices.add(new Point(rect.x() - 2, rect.bottom() + 2));
        }
        var points = new ArrayList<>(vertices); int count = points.size();
        var distances = new double[count]; Arrays.fill(distances, Double.POSITIVE_INFINITY); distances[0] = 0;
        var previous = new int[count]; Arrays.fill(previous, -1); var visited = new boolean[count];
        for (int step = 0; step < count; step++) {
            token.check(); int current = -1;
            for (int i = 0; i < count; i++) if (!visited[i] && (current < 0 || distances[i] < distances[current])) current = i;
            if (current < 0 || !Double.isFinite(distances[current])) break;
            if (current == 1) {
                var path = new ArrayList<Point>();
                for (int i = 1; i >= 0; i = previous[i]) path.add(points.get(i));
                Collections.reverse(path); return path;
            }
            visited[current] = true;
            for (int i = 0; i < count; i++) if (!visited[i] && clear(List.of(points.get(current), points.get(i)), obstacles)) {
                double d = distances[current] + Math.hypot(points.get(current).x() - points.get(i).x(), points.get(current).y() - points.get(i).y());
                if (d < distances[i]) { distances[i] = d; previous[i] = current; }
            }
        }
        throw new LayoutException(LayoutException.Code.INVALID_RESULT, "No collision-free port route");
    }
    private static boolean clear(List<Point> points, List<Rect> obstacles) {
        for (int i = 1; i < points.size(); i++) for (var rect : obstacles)
            if (crosses(points.get(i - 1), points.get(i), rect)) return false;
        return true;
    }
    public static boolean crosses(Point a, Point b, Rect rect) {
        // Open interior intersection; a path may touch its endpoint's boundary.
        double minX = rect.x() + 1e-7, maxX = rect.right() - 1e-7;
        double minY = rect.y() + 1e-7, maxY = rect.bottom() - 1e-7;
        if (minX > maxX || minY > maxY) return false;
        double lower = 0, upper = 1;
        double[] starts = {a.x(), a.y()}, deltas = {b.x() - a.x(), b.y() - a.y()};
        double[] mins = {minX, minY}, maxs = {maxX, maxY};
        for (int axis = 0; axis < 2; axis++) {
            if (Math.abs(deltas[axis]) < 1e-12) { if (starts[axis] < mins[axis] || starts[axis] > maxs[axis]) return false; }
            else {
                double first = (mins[axis] - starts[axis]) / deltas[axis], last = (maxs[axis] - starts[axis]) / deltas[axis];
                lower = Math.max(lower, Math.min(first, last)); upper = Math.min(upper, Math.max(first, last));
                if (lower > upper) return false;
            }
        }
        return lower <= upper;
    }
}
