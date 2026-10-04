package craken.ui.component.visualization;

import craken.visualization.layout.LayoutRequest.Direction;
import craken.visualization.layout.LayoutRequest.Point;
import craken.visualization.layout.LayoutResult;
import craken.visualization.layout.LayoutResult.EdgePath;
import javafx.scene.Group;
import javafx.scene.shape.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.Font;
import craken.visualization.style.EdgeStyle;
import java.util.*;

public final class EdgeRenderer {
    private static final double ARROW_LENGTH = 9, ARROW_HALF_WIDTH = 3.5;
    public Group render(EdgePath edge, Direction direction) {
        return render(edge, direction, EdgeStyle.DEFAULT);
    }
    public Group render(EdgePath edge, Direction direction, EdgeStyle style) {
        ViewNodeRenderer.requireFxThread(); Objects.requireNonNull(edge); Objects.requireNonNull(direction); Objects.requireNonNull(style);
        var path = new Path(); path.setFill(null); path.setStroke(stroke(style.lineColor()));
        path.setStrokeWidth(1); path.setMouseTransparent(true);
        path.getElements().add(new MoveTo(edge.start().x(), edge.start().y()));
        for (var segment : edge.segments()) {
            if (segment instanceof LayoutResult.Cubic cubic)
                path.getElements().add(new CubicCurveTo(cubic.control1().x(), cubic.control1().y(), cubic.control2().x(), cubic.control2().y(), cubic.end().x(), cubic.end().y()));
            else path.getElements().add(new LineTo(segment.end().x(), segment.end().y()));
        }
        var group = new Group(path); group.setMouseTransparent(true);
        if (direction == Direction.FORWARD || direction == Direction.BOTH) arrow(group, edge.end(), endTangent(edge), stroke(style.effectiveArrowColor()));
        if (direction == Direction.BACKWARD || direction == Direction.BOTH) arrow(group, edge.start(), startTangent(edge), stroke(style.effectiveArrowColor()));
        if (!style.label().isEmpty()) label(group, edge, style.label());
        return group;
    }
    private static Color stroke(String hex) { return Color.rgb(Integer.parseInt(hex.substring(1, 3), 16), Integer.parseInt(hex.substring(3, 5), 16), Integer.parseInt(hex.substring(5, 7), 16)); }
    private static void label(Group group, EdgePath edge, String value) {
        var text = new Text(value); text.setFont(Font.font("System", 14)); text.setFill(ViewNodeRenderer.color(VisualizationTheme.TEXT));
        var middle = midpoint(edge); var bounds = text.getLayoutBounds();
        double x = Math.max(4, middle.x() - bounds.getWidth() / 2), y = Math.max(4, middle.y() - bounds.getHeight() - 6);
        text.setLayoutX(x - bounds.getMinX()); text.setLayoutY(y - bounds.getMinY());
        var backdrop = new Rectangle(x - 3, y - 2, bounds.getWidth() + 6, bounds.getHeight() + 4);
        backdrop.setFill(ViewNodeRenderer.color(VisualizationTheme.CANVAS)); group.getChildren().addAll(backdrop, text);
    }
    private static Point midpoint(EdgePath edge) {
        var samples = new ArrayList<Point>(); samples.add(edge.start()); var start = edge.start();
        for (var segment : edge.segments()) {
            if (segment instanceof LayoutResult.Cubic c) for (int i = 1; i <= 20; i++) {
                double t = i / 20.0, s = 1 - t;
                samples.add(new Point(s*s*s*start.x() + 3*s*s*t*c.control1().x() + 3*s*t*t*c.control2().x() + t*t*t*c.end().x(),
                        s*s*s*start.y() + 3*s*s*t*c.control1().y() + 3*s*t*t*c.control2().y() + t*t*t*c.end().y()));
            } else samples.add(segment.end());
            start = segment.end();
        }
        double length = 0; for (int i = 1; i < samples.size(); i++) length += distance(samples.get(i - 1), samples.get(i));
        double remaining = length / 2;
        for (int i = 1; i < samples.size(); i++) {
            var a = samples.get(i - 1); var b = samples.get(i); double step = distance(a, b);
            if (step > 0 && remaining <= step) { double t = remaining / step; return new Point(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t); }
            remaining -= step;
        }
        return edge.start();
    }
    private static double distance(Point a, Point b) { return Math.hypot(b.x() - a.x(), b.y() - a.y()); }
    private static void arrow(Group group, Point tip, Point tangent, Color color) {
        if (tangent == null) return;
        double length = Math.hypot(tangent.x(), tangent.y()), dx = tangent.x() / length, dy = tangent.y() / length;
        double baseX = tip.x() - ARROW_LENGTH * dx, baseY = tip.y() - ARROW_LENGTH * dy;
        var polygon = new Polygon(tip.x(), tip.y(), baseX - dy * ARROW_HALF_WIDTH, baseY + dx * ARROW_HALF_WIDTH,
                baseX + dy * ARROW_HALF_WIDTH, baseY - dx * ARROW_HALF_WIDTH);
        polygon.setFill(color); group.getChildren().add(polygon);
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
