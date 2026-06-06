package minic.uilocal.control;

import javafx.geometry.Point2D;

public interface MiniCViewportAdapter {
    MiniCViewportAdapter NOOP = () -> MiniCControlTargetType.NONE;

    MiniCControlTargetType type();

    default boolean canZoom() {
        return false;
    }

    default void zoomAt(Point2D localPoint, double delta) {
    }

    default boolean canScrollVertical() {
        return false;
    }

    default void scrollVertical(double delta) {
    }

    default boolean canScrollHorizontal() {
        return false;
    }

    default void scrollHorizontal(double delta) {
    }

    default boolean canPan() {
        return false;
    }

    default void pan(double deltaX, double deltaY) {
    }

    default boolean isActiveFullyVisible() {
        return true;
    }

    default void centerActiveIfNeeded() {
        if (!isActiveFullyVisible()) {
            centerActive();
        }
    }

    default void centerActive() {
    }

    static MiniCViewportAdapter noop() {
        return NOOP;
    }
}
