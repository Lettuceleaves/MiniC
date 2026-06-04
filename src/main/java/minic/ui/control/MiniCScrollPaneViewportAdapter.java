package minic.ui.control;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

import java.util.Objects;
import java.util.function.DoubleConsumer;

public final class MiniCScrollPaneViewportAdapter implements MiniCViewportAdapter {
    public static final String ADAPTER_PROPERTY = "minic.ui.control.scrollPaneViewportAdapter";

    private final ScrollPane scrollPane;

    public MiniCScrollPaneViewportAdapter(ScrollPane scrollPane) {
        this.scrollPane = Objects.requireNonNull(scrollPane, "scrollPane");
    }

    @Override
    public MiniCControlTargetType type() {
        return MiniCControlTargetType.SCROLL;
    }

    @Override
    public boolean canScrollVertical() {
        return true;
    }

    @Override
    public void scrollVertical(double delta) {
        scrollBy(delta, false);
    }

    @Override
    public boolean canScrollHorizontal() {
        return true;
    }

    @Override
    public void scrollHorizontal(double delta) {
        scrollBy(delta, true);
    }

    @Override
    public boolean canPan() {
        return true;
    }

    @Override
    public void pan(double deltaX, double deltaY) {
        scrollHorizontal(deltaX);
        scrollVertical(deltaY);
    }

    private void scrollBy(double delta, boolean horizontal) {
        Node content = scrollPane.getContent();
        if (content == null) {
            return;
        }
        Bounds viewport = scrollPane.getViewportBounds();
        Bounds contentBounds = content.getLayoutBounds();
        double contentSize = horizontal ? contentBounds.getWidth() : contentBounds.getHeight();
        double viewportSize = horizontal ? viewport.getWidth() : viewport.getHeight();
        double value = horizontal ? scrollPane.getHvalue() : scrollPane.getVvalue();
        double min = horizontal ? scrollPane.getHmin() : scrollPane.getVmin();
        double max = horizontal ? scrollPane.getHmax() : scrollPane.getVmax();
        DoubleConsumer setter = horizontal ? scrollPane::setHvalue : scrollPane::setVvalue;
        setAxisByDelta(value, delta, contentSize, viewportSize, min, max, setter);
    }

    private void setAxisByDelta(
            double value,
            double delta,
            double contentSize,
            double viewportSize,
            double min,
            double max,
            DoubleConsumer setter
    ) {
        double maxOffset = Math.max(0, contentSize - viewportSize);
        if (maxOffset <= 0 || max <= min) {
            setter.accept(min);
            return;
        }
        double currentOffset = normalized(value, min, max) * maxOffset;
        double target = clamp((currentOffset + delta) / maxOffset);
        setter.accept(min + target * (max - min));
    }

    private double normalized(double value, double min, double max) {
        if (max <= min) {
            return 0;
        }
        return clamp((value - min) / (max - min));
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
