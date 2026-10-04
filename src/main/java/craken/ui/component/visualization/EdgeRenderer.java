package craken.ui.component.visualization;

import craken.visualization.layout.LayoutRequest.Direction;
import craken.visualization.layout.LayoutRequest.Point;
import craken.visualization.layout.LayoutResult;
import craken.visualization.layout.LayoutResult.EdgePath;
import javafx.scene.Group;
import javafx.scene.shape.*;
import java.util.*;

public final class EdgeRenderer {
    private static final double ARROW_LENGTH = 9, ARROW_HALF_WIDTH = 3.5;
    public Group render(EdgePath edge, Direction direction) {
        ViewNodeRenderer.requireFxThread(); Objects.requireNonNull(edge); Objects.requireNonNull(direction);
        var path = new Path(); path.setFill(null); path.setStroke(ViewNodeRenderer.color(VisualizationTheme.NODE_BORDER));
        path.setStrokeWidth(1); path.setMouseTransparent(true);
        path.getElements().add(new MoveTo(edge.start().x(), edge.start().y()));
        for (var segment : edge.segments()) {
            if (segment instanceof LayoutResult.Cubic cubic)
                path.getElements().add(new CubicCurveTo(cubic.control1().x(), cubic.control1().y(), cubic.control2().x(), cubic.control2().y(), cubic.end().x(), cubic.end().y()));
            else path.getElements().add(new LineTo(segment.end().x(), segment.end().y()));
        }
        var group = new Group(path); group.setMouseTransparent(true);
        if (direction == Direction.FORWARD || direction == Direction.BOTH) arrow(group, edge.end(), endTangent(edge));
        if (direction == Direction.BACKWARD || direction == Direction.BOTH) arrow(group, edge.start(), startTangent(edge));
        return group;
    }
    private static void arrow(Group group, Point tip, Point tangent) {
        if (tangent == null) return;
        double length = Math.hypot(tangent.x(), tangent.y()), dx = tangent.x() / length, dy = tangent.y() / length;
        double baseX = tip.x() - ARROW_LENGTH * dx, baseY = tip.y() - ARROW_LENGTH * dy;
        var polygon = new Polygon(tip.x(), tip.y(), baseX - dy * ARROW_HALF_WIDTH, baseY + dx * ARROW_HALF_WIDTH,
                baseX + dy * ARROW_HALF_WIDTH, baseY - dx * ARROW_HALF_WIDTH);
        polygon.setFill(ViewNodeRenderer.color(VisualizationTheme.NODE_BORDER)); group.getChildren().add(polygon);
    }
    private static Point endTangent(EdgePath edge) {
        var points = flattened(edge);
        Point tip = points.getLast();
        for (int i = points.size() - 2; i >= 0; i--) {
            var vector = vector(points.get(i), tip); if (vector != null) return vector;
        }
        return null;
    }
    private static Point startTangent(EdgePath edge) {
        var points = flattened(edge); Point tip = points.getFirst();
        for (int i = 1; i < points.size(); i++) {
            var vector = vector(points.get(i), tip); if (vector != null) return vector;
        }
        return null;
    }
    private static List<Point> flattened(EdgePath edge) {
        var result = new ArrayList<Point>(); result.add(edge.start());
        for (var segment : edge.segments()) {
            if (segment instanceof LayoutResult.Cubic cubic) { result.add(cubic.control1()); result.add(cubic.control2()); }
            result.add(segment.end());
        }
        return result;
    }
    private static Point vector(Point from, Point to) {
        double dx = to.x() - from.x(), dy = to.y() - from.y();
        return Math.hypot(dx, dy) < 1e-7 ? null : new Point(dx, dy);
    }
}
