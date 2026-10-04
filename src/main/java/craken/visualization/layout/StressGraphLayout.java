package craken.visualization.layout;

import craken.visualization.layout.graphviz.GraphvizProcessBridge;
import craken.visualization.layout.graphviz.DotGraphWriter;
import craken.visualization.layout.graphviz.PlainExtReader;
import java.util.*;

public final class StressGraphLayout implements LayoutEngine {
    private final GraphvizProcessBridge bridge;
    public StressGraphLayout(GraphvizProcessBridge bridge) { this.bridge = Objects.requireNonNull(bridge); }
    @Override public LayoutResult layout(LayoutRequest request, CancellationToken cancellation) {
        cancellation.check(); String version = bridge.version(cancellation);
        var encoded = new DotGraphWriter().write(request);
        var output = bridge.execute(encoded.dot(), cancellation);
        var result = new PlainExtReader().read(output.stdout(), encoded, request, version);
        boolean composedPorts = request.units().stream().flatMap(u -> u.ports().stream())
                .anyMatch(p -> p.side() != LayoutRequest.Side.AUTO || !request.owner(p.ref().node()).node().equals(p.ref().node()));
        if (composedPorts) {
            // Native macro positions remain authoritative. Concrete local slots use measured geometry.
            var routes = new PortRouter().route(request, result.nodeBounds(), cancellation);
            var extent = result.contentBounds();
            double left = extent.x(), top = extent.y(), right = extent.right(), bottom = extent.bottom();
            for (var route : routes.values()) {
                var points = new ArrayList<LayoutRequest.Point>(); points.add(route.start());
                for (var segment : route.segments()) {
                    points.add(segment.end());
                    if (segment instanceof LayoutResult.Cubic cubic) { points.add(cubic.control1()); points.add(cubic.control2()); }
                }
                for (var point : points) {
                    left = Math.min(left, point.x()); top = Math.min(top, point.y());
                    right = Math.max(right, point.x()); bottom = Math.max(bottom, point.y());
                }
            }
            return new LayoutResult(result.stamp(), result.nodeBounds(), routes,
                    new LayoutRequest.Rect(left, top, right - left, bottom - top), result.engine(), result.engineVersion());
        }
        cancellation.check(); return result;
    }
}
