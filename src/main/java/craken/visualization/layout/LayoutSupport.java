package craken.visualization.layout;

import craken.visualization.api.ViewLocation;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;

final class LayoutSupport {
    static List<Unit> ordered(LayoutRequest request) {
        var rank = new HashMap<ViewLocation, Integer>();
        for (int i = 0; i < request.hints().order().size(); i++)
            rank.putIfAbsent(request.owner(request.hints().order().get(i)).node(), i);
        return request.units().stream().sorted(Comparator
                .comparingInt((Unit u) -> rank.getOrDefault(u.node(), Integer.MAX_VALUE))
                .thenComparingLong(u -> u.node().nodeId())).toList();
    }
    static LayoutResult finish(LayoutRequest request, Map<ViewLocation, Point> positions,
                               CancellationToken cancellation, String engine) {
        var bounds = new LinkedHashMap<ViewLocation, Rect>();
        double right = 0, bottom = 0;
        for (var unit : request.units()) {
            var point = positions.get(unit.node());
            for (var member : unit.members()) {
                var local = member.bounds();
                bounds.put(member.node(), new Rect(point.x() + local.x(), point.y() + local.y(), local.width(), local.height()));
            }
            right = Math.max(right, point.x() + unit.size().width() + request.hints().padding());
            bottom = Math.max(bottom, point.y() + unit.size().height() + request.hints().padding());
        }
        var paths = new PortRouter().route(request, bounds, cancellation);
        double left = 0, top = 0;
        for (var path : paths.values()) {
            var points = new ArrayList<Point>(); points.add(path.start());
            for (var segment : path.segments()) {
                points.add(segment.end());
                if (segment instanceof LayoutResult.Cubic c) { points.add(c.control1()); points.add(c.control2()); }
            }
            for (var point : points) {
                left = Math.min(left, point.x()); top = Math.min(top, point.y());
                right = Math.max(right, point.x()); bottom = Math.max(bottom, point.y());
            }
        }
        return new LayoutResult(request.stamp(), bounds, paths, new Rect(left, top, right - left, bottom - top), engine, "1");
    }
}
