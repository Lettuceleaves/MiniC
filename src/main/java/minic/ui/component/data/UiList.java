package minic.ui.component.data;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import minic.ui.component.UiComponent;

import java.util.Objects;

/**
 * 等尺寸、单轴延展的矩形列表。
 *
 * <p>横向列表固定高度，每项宽度相等；纵向列表固定宽度，每项高度相等。
 * 内容超出延展方向时，鼠标位于列表上方产生的滚轮事件会按整项翻动：
 * 上滚向上（横向时向左），下滚向下（横向时向右）。列表使用裁剪和位移
 * 实现滚动，不创建可见滚动条，也不会改变外部布局。</p>
 */
public final class UiList extends Region implements UiComponent {
    private final Orientation orientation;
    private final double itemExtent;
    private final double crossExtent;
    private final ObservableList<Node> items = FXCollections.observableArrayList();
    private final Pane content = new Pane() {
        @Override
        protected void layoutChildren() {
            // UiList.layoutChildren() is the sole owner of item geometry.
        }
    };
    private final Rectangle clip = new Rectangle();

    private double scrollOffset;

    public UiList(Orientation orientation, double itemExtent, double crossExtent) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.itemExtent = requirePositive(itemExtent, "itemExtent");
        this.crossExtent = requirePositive(crossExtent, "crossExtent");

        setMinSize(0, 0);
        setPickOnBounds(true);
        getStyleClass().addAll(
                "ui-list",
                orientation == Orientation.HORIZONTAL ? "ui-list-horizontal" : "ui-list-vertical"
        );

        content.setManaged(false);
        content.getStyleClass().add("ui-list-content");
        getChildren().add(content);

        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        items.addListener((ListChangeListener<Node>) ignored -> rebuildItems());
        widthProperty().addListener(ignored -> clampScrollOffset());
        heightProperty().addListener(ignored -> clampScrollOffset());
        addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() != 0 && scrollByWheel(event.getDeltaY())) {
                event.consume();
            }
        });

        applyFixedCrossExtent();
        updatePreferredPrimaryExtent();
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public double getItemExtent() {
        return itemExtent;
    }

    public double getCrossExtent() {
        return crossExtent;
    }

    public ObservableList<Node> getItems() {
        return items;
    }

    /** 将指定项完整移入视口；用于切换标签时跟随当前标签。 */
    public void revealItem(int index) {
        if (index < 0 || index >= items.size()) return;
        double viewport = orientation == Orientation.HORIZONTAL
                ? getWidth() - snappedLeftInset() - snappedRightInset()
                : getHeight() - snappedTopInset() - snappedBottomInset();
        double start = index * itemExtent;
        if (start < scrollOffset) setScrollOffset(start);
        else if (start + itemExtent > scrollOffset + viewport) {
            setScrollOffset(start + itemExtent - Math.max(0, viewport));
        }
    }

    public double getScrollOffset() {
        return scrollOffset;
    }

    public double getMaxScrollOffset() {
        double viewportExtent = orientation == Orientation.HORIZONTAL
                ? Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset())
                : Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset());
        return Math.max(0, items.size() * itemExtent - viewportExtent);
    }

    /** Used by the ScrollEvent handler and package tests. */
    boolean scrollByWheel(double deltaY) {
        if (deltaY == 0) {
            return false;
        }
        double previous = scrollOffset;
        setScrollOffset(previous + (deltaY < 0 ? itemExtent : -itemExtent));
        return Double.compare(previous, scrollOffset) != 0;
    }

    private void rebuildItems() {
        content.getChildren().setAll(items);
        for (Node item : items) {
            if (!item.getStyleClass().contains("ui-list-item")) {
                item.getStyleClass().add("ui-list-item");
            }
        }
        updatePreferredPrimaryExtent();
        clampScrollOffset();
        requestLayout();
    }

    private void applyFixedCrossExtent() {
        if (orientation == Orientation.HORIZONTAL) {
            setMinHeight(crossExtent);
            setPrefHeight(crossExtent);
            setMaxHeight(crossExtent);
            setMaxWidth(Double.MAX_VALUE);
        } else {
            setMinWidth(crossExtent);
            setPrefWidth(crossExtent);
            setMaxWidth(crossExtent);
            setMaxHeight(Double.MAX_VALUE);
        }
    }

    private void updatePreferredPrimaryExtent() {
        double preferred = items.size() * itemExtent;
        if (orientation == Orientation.HORIZONTAL) {
            setPrefWidth(preferred);
        } else {
            setPrefHeight(preferred);
        }
    }

    private void setScrollOffset(double value) {
        double clamped = Math.max(0, Math.min(value, getMaxScrollOffset()));
        if (Double.compare(clamped, scrollOffset) != 0) {
            scrollOffset = clamped;
            requestLayout();
        }
    }

    private void clampScrollOffset() {
        setScrollOffset(scrollOffset);
    }

    @Override
    protected void layoutChildren() {
        double left = snappedLeftInset();
        double top = snappedTopInset();
        double viewportWidth = Math.max(0, getWidth() - left - snappedRightInset());
        double viewportHeight = Math.max(0, getHeight() - top - snappedBottomInset());
        double contentWidth = orientation == Orientation.HORIZONTAL
                ? items.size() * itemExtent : viewportWidth;
        double contentHeight = orientation == Orientation.VERTICAL
                ? items.size() * itemExtent : viewportHeight;

        clampScrollOffset();
        double contentX = left - (orientation == Orientation.HORIZONTAL ? scrollOffset : 0);
        double contentY = top - (orientation == Orientation.VERTICAL ? scrollOffset : 0);
        content.resizeRelocate(contentX, contentY, contentWidth, contentHeight);

        for (int index = 0; index < items.size(); index++) {
            Node item = items.get(index);
            double x = orientation == Orientation.HORIZONTAL ? index * itemExtent : 0;
            double y = orientation == Orientation.VERTICAL ? index * itemExtent : 0;
            double width = orientation == Orientation.HORIZONTAL ? itemExtent : viewportWidth;
            double height = orientation == Orientation.VERTICAL ? itemExtent : viewportHeight;
            if (item.isResizable()) {
                item.resize(width, height);
            }
            item.relocate(x, y);
        }
    }

    private static double requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
