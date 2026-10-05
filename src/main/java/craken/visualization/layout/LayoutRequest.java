package craken.visualization.layout;

import craken.visualization.api.PageRef;
import craken.visualization.api.ViewLocation;
import java.util.*;

/** One measured part. Coordinates and sizes are JavaFX logical pixels, without JavaFX objects. */
public record LayoutRequest(Stamp stamp, Kind kind, List<Unit> units, List<Link> links,
                            Hints hints, Map<ViewLocation, Point> previousPositions) {
    public enum Kind { POINT, ARRAY, LINEAR, TREE, GRAPH }
    public enum Side { AUTO, NORTH, EAST, SOUTH, WEST }
    public enum Direction { NONE, FORWARD, BACKWARD, BOTH }
    public enum Orientation { HORIZONTAL, VERTICAL }

    public record Stamp(PageRef page, long partId, long geometryVersion, long measurementVersion,
                        long epoch, double availableWidth) {
        public Stamp {
            Objects.requireNonNull(page);
            if (partId <= 0 || geometryVersion < 0 || measurementVersion < 0 || epoch < 0)
                throw new IllegalArgumentException("Invalid layout identity/version");
            nonnegative(availableWidth);
        }
    }
    public record Point(double x, double y) {
        public Point { finite(x); finite(y); }
    }
    public record Size(double width, double height) {
        public Size { positive(width); positive(height); }
    }
    public record Rect(double x, double y, double width, double height) {
        public Rect { finite(x); finite(y); nonnegative(width); nonnegative(height); finite(x + width); finite(y + height); }
        public double right() { return x + width; }
        public double bottom() { return y + height; }
        public Point center() { return new Point(x + width / 2, y + height / 2); }
        public boolean contains(Point p) {
            return p.x >= x - 1e-7 && p.x <= right() + 1e-7 && p.y >= y - 1e-7 && p.y <= bottom() + 1e-7;
        }
    }
    public record PortRef(ViewLocation node, String key) {
        public PortRef {
            Objects.requireNonNull(node);
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Blank port key");
        }
        public static PortRef node(ViewLocation node) { return new PortRef(node, "node"); }
    }
    public record Port(PortRef ref, Point anchor, Side side) {
        public Port { Objects.requireNonNull(ref); Objects.requireNonNull(anchor); Objects.requireNonNull(side); }
    }
    public record Member(ViewLocation node, Rect bounds) {
        public Member { Objects.requireNonNull(node); Objects.requireNonNull(bounds); }
    }
    public record Unit(ViewLocation node, Size size, List<Member> members, List<Port> ports,
                       List<Rect> textObstacles) {
        public Unit {
            Objects.requireNonNull(node); Objects.requireNonNull(size);
            members = List.copyOf(members); ports = List.copyOf(ports); textObstacles = List.copyOf(textObstacles);
            var outer = new Rect(0, 0, size.width, size.height);
            var memberIds = new HashSet<ViewLocation>();
            for (var member : members) {
                if (!member.node.page().equals(node.page()) || !memberIds.add(member.node)
                        || !outer.contains(new Point(member.bounds.x, member.bounds.y))
                        || !outer.contains(new Point(member.bounds.right(), member.bounds.bottom())))
                    throw new IllegalArgumentException("Invalid local member geometry");
            }
            if (!memberIds.contains(node)) throw new IllegalArgumentException("Outer member must be present");
            var portIds = new HashSet<PortRef>();
            var memberBounds = new HashMap<ViewLocation, Rect>();
            members.forEach(member -> memberBounds.put(member.node, member.bounds));
            for (var port : ports) {
                if (!memberIds.contains(port.ref.node) || !portIds.add(port.ref) || !outer.contains(port.anchor))
                    throw new IllegalArgumentException("Invalid local port");
                var bounds = memberBounds.get(port.ref.node);
                boolean onSide = switch (port.side) {
                    case AUTO -> true;
                    case NORTH -> Math.abs(port.anchor.y - bounds.y) < 1e-7;
                    case SOUTH -> Math.abs(port.anchor.y - bounds.bottom()) < 1e-7;
                    case WEST -> Math.abs(port.anchor.x - bounds.x) < 1e-7;
                    case EAST -> Math.abs(port.anchor.x - bounds.right()) < 1e-7;
                };
                if (!bounds.contains(port.anchor) || !onSide)
                    throw new IllegalArgumentException("Port must anchor its member boundary");
            }
            for (var obstacle : textObstacles)
                if (!outer.contains(new Point(obstacle.x, obstacle.y))
                        || !outer.contains(new Point(obstacle.right(), obstacle.bottom())))
                    throw new IllegalArgumentException("Text obstacle outside unit");
        }
        public static Unit simple(ViewLocation node, double width, double height) {
            var rect = new Rect(0, 0, width, height);
            return new Unit(node, new Size(width, height), List.of(new Member(node, rect)),
                    List.of(new Port(PortRef.node(node), rect.center(), Side.AUTO)), List.of());
        }
    }
    public record Link(long id, PortRef tail, PortRef head, Direction direction) {
        public Link {
            if (id <= 0) throw new IllegalArgumentException("Edge ID must be positive");
            Objects.requireNonNull(tail); Objects.requireNonNull(head); Objects.requireNonNull(direction);
        }
    }
    public record Hints(double horizontalGap, double verticalGap, double padding, int columns,
                        Orientation orientation, ViewLocation treeRoot, List<ViewLocation> order) {
        public Hints {
            nonnegative(horizontalGap); nonnegative(verticalGap); nonnegative(padding);
            if (columns <= 0) throw new IllegalArgumentException("Columns must be positive");
            Objects.requireNonNull(orientation); order = List.copyOf(order);
        }
        public static Hints defaults() { return new Hints(24, 40, 8, 1, Orientation.HORIZONTAL, null, List.of()); }
    }

    public LayoutRequest {
        Objects.requireNonNull(stamp); Objects.requireNonNull(kind); Objects.requireNonNull(hints);
        units = List.copyOf(units); links = List.copyOf(links); previousPositions = Map.copyOf(previousPositions);
        var members = new HashSet<ViewLocation>();
        var ports = new HashSet<PortRef>();
        for (var unit : units) {
            if (!unit.node.page().equals(stamp.page)) throw new IllegalArgumentException("Foreign unit");
            for (var member : unit.members)
                if (!members.add(member.node)) throw new IllegalArgumentException("Duplicate member");
            for (var port : unit.ports) ports.add(port.ref);
        }
        var edgeIds = new HashSet<Long>();
        var pairs = new HashSet<Set<PortRef>>();
        for (var link : links) {
            var pair = new HashSet<PortRef>(List.of(link.tail, link.head));
            if (!edgeIds.add(link.id) || !pairs.add(pair) || !ports.contains(link.tail) || !ports.contains(link.head))
                throw new IllegalArgumentException("Duplicate edge or unknown endpoint");
        }
        if (hints.treeRoot != null && !members.contains(hints.treeRoot))
            throw new IllegalArgumentException("Unknown tree entry");
        if (new HashSet<>(hints.order).size() != hints.order.size() || !members.containsAll(hints.order))
            throw new IllegalArgumentException("Invalid layout order");
    }
    public Unit owner(ViewLocation node) {
        return units.stream().filter(u -> u.members.stream().anyMatch(m -> m.node.equals(node)))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown member"));
    }
    public Port port(PortRef ref) {
        return owner(ref.node).ports.stream().filter(p -> p.ref.equals(ref)).findFirst().orElseThrow();
    }
    static void finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite geometry");
    }
    static void nonnegative(double value) { finite(value); if (value < 0) throw new IllegalArgumentException("Negative geometry"); }
    static void positive(double value) { finite(value); if (value <= 0) throw new IllegalArgumentException("Non-positive size"); }

}
