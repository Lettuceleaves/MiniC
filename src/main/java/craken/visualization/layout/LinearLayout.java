package craken.visualization.layout;

import craken.visualization.api.ViewLocation;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;

public final class LinearLayout implements LayoutEngine {
    @Override public LayoutResult layout(LayoutRequest request, CancellationToken cancellation) {
        cancellation.check();
        var ordered = LayoutSupport.ordered(request);
        if (request.hints().order().isEmpty()) {
            var chain = topologyOrder(request.units(), request.links());
            if (chain.isPresent()) {
                var byNode = new HashMap<ViewLocation, Unit>(); request.units().forEach(u -> byNode.put(u.node(), u));
                ordered = chain.get().stream().map(byNode::get).toList();
            }
        }
        var positions = new LinkedHashMap<ViewLocation, Point>();
        double x = request.hints().padding(), y = x;
        for (var unit : ordered) {
            cancellation.check(); positions.put(unit.node(), new Point(x, y));
            if (request.hints().orientation() == Orientation.HORIZONTAL)
                x += unit.size().width() + request.hints().horizontalGap();
            else y += unit.size().height() + request.hints().verticalGap();
        }
        return LayoutSupport.finish(request, positions, cancellation, "linear");
    }
    /** Empty means a cycle/branch/disconnected macro graph, which a host dispatches to GRAPH before layout. */
    public static Optional<List<ViewLocation>> topologyOrder(List<Unit> units, List<Link> links) {
        if (units.isEmpty()) return Optional.of(List.of());
        var owners = new HashMap<ViewLocation, ViewLocation>(); var adjacent = new HashMap<ViewLocation, Set<ViewLocation>>();
        var flow = new HashMap<ViewLocation, Integer>();
        for (var unit : units) { adjacent.put(unit.node(), new LinkedHashSet<>()); for (var member : unit.members()) owners.put(member.node(), unit.node()); }
        for (var link : links) {
            if (link.tail().node().equals(link.head().node())) return Optional.empty();
            var a = owners.get(link.tail().node()); var b = owners.get(link.head().node());
            if (a == null || b == null) return Optional.empty();
            if (a.equals(b)) continue;
            adjacent.get(a).add(b); adjacent.get(b).add(a);
            int sign = link.direction() == Direction.FORWARD ? 1 : link.direction() == Direction.BACKWARD ? -1 : 0;
            flow.merge(a, sign, Integer::sum); flow.merge(b, -sign, Integer::sum);
        }
        if (adjacent.values().stream().anyMatch(next -> next.size() > 2)
                || adjacent.values().stream().mapToLong(Set::size).sum() / 2 != units.size() - 1) return Optional.empty();
        var start = adjacent.keySet().stream().filter(n -> adjacent.get(n).size() < 2)
                .sorted(Comparator.comparingInt((ViewLocation n) -> flow.getOrDefault(n, 0) > 0 ? 0 : 1).thenComparingLong(ViewLocation::nodeId))
                .findFirst().orElse(null);
        if (start == null) return Optional.empty();
        var result = new ArrayList<ViewLocation>(); var visited = new HashSet<ViewLocation>(); var current = start;
        while (current != null && visited.add(current)) {
            result.add(current); current = adjacent.get(current).stream().filter(n -> !visited.contains(n)).findFirst().orElse(null);
        }
        return result.size() == units.size() ? Optional.of(List.copyOf(result)) : Optional.empty();
    }
}
