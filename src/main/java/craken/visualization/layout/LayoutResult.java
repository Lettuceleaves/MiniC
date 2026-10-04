package craken.visualization.layout;

import craken.visualization.api.ViewLocation;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;

/** Geometry carries its input stamp; styling is applied by the current rendering occurrence. */
public record LayoutResult(Stamp stamp, Map<ViewLocation, Rect> nodeBounds, Map<Long, EdgePath> edgePaths,
                           Rect contentBounds, String engine, String engineVersion) {
    public sealed interface Segment permits Line, Cubic { Point end(); }
    public record Line(Point end) implements Segment { public Line { Objects.requireNonNull(end); } }
    public record Cubic(Point control1, Point control2, Point end) implements Segment {
        public Cubic { Objects.requireNonNull(control1); Objects.requireNonNull(control2); Objects.requireNonNull(end); }
    }
    public record EdgePath(Point start, List<Segment> segments) {
        public EdgePath { Objects.requireNonNull(start); segments = List.copyOf(segments); }
        public Point end() { return segments.isEmpty() ? start : segments.getLast().end(); }
    }
    public LayoutResult {
        Objects.requireNonNull(stamp); nodeBounds = Map.copyOf(nodeBounds); edgePaths = Map.copyOf(edgePaths);
        Objects.requireNonNull(contentBounds); Objects.requireNonNull(engine); Objects.requireNonNull(engineVersion);
    }
}
