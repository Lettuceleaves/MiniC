package minic.ui.control;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

import java.util.Objects;

public final class MiniCViewportPointMapper {
    private MiniCViewportPointMapper() {
    }

    public static Point2D toViewportPoint(Node localNode, double localX, double localY, ScrollPane scrollPane) {
        Objects.requireNonNull(localNode, "localNode");
        Objects.requireNonNull(scrollPane, "scrollPane");
        Node content = scrollPane.getContent();
        if (content == null) {
            return new Point2D(localX, localY);
        }
        Point2D contentPoint = content.sceneToLocal(localNode.localToScene(localX, localY));
        Bounds viewport = scrollPane.getViewportBounds();
        Bounds contentBounds = content.getLayoutBounds();
        double visibleMinX = visibleMin(
                scrollPane.getHvalue(),
                scrollPane.getHmin(),
                scrollPane.getHmax(),
                contentBounds.getMinX(),
                contentBounds.getWidth(),
                viewport.getWidth()
        );
        double visibleMinY = visibleMin(
                scrollPane.getVvalue(),
                scrollPane.getVmin(),
                scrollPane.getVmax(),
                contentBounds.getMinY(),
                contentBounds.getHeight(),
                viewport.getHeight()
        );
        return new Point2D(contentPoint.getX() - visibleMinX, contentPoint.getY() - visibleMinY);
    }

    private static double visibleMin(
            double value,
            double min,
            double max,
            double contentMin,
            double contentSize,
            double viewportSize
    ) {
        double maxOffset = Math.max(0, contentSize - viewportSize);
        return contentMin + normalized(value, min, max) * maxOffset;
    }

    private static double normalized(double value, double min, double max) {
        if (max <= min) {
            return 0;
        }
        return Math.max(0, Math.min(1, (value - min) / (max - min)));
    }
}
