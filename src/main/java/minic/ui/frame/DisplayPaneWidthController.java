package minic.ui.frame;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/** 右侧展示区的宽度状态和可随时打断的分隔条动画。 */
final class DisplayPaneWidthController {
    static final Duration ANIMATION_DURATION = Duration.millis(240);
    private static final Interpolator EASE_OUT = Interpolator.SPLINE(0, 0, 0.58, 1);

    private final SplitPane split;
    private final SplitPane.Divider divider;
    private final double defaultWidth;
    private WidthMode mode = WidthMode.DEFAULT;
    private Timeline animation;

    DisplayPaneWidthController(SplitPane split, double defaultWidth) {
        this.split = split;
        this.divider = split.getDividers().getFirst();
        this.defaultWidth = defaultWidth;
        updateResizePolicy();
        split.widthProperty().addListener((observable, previous, current) -> synchronizeSize());
        split.skinProperty().addListener((observable, previous, current) -> synchronizeSize());
        split.sceneProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                stopAnimation();
            } else {
                synchronizeSize();
            }
        });
        split.addEventFilter(MouseEvent.MOUSE_PRESSED, this::takeOverForDrag);
        synchronizeSize();
    }

    void expand() {
        animateTo(WidthMode.EXPANDED);
    }

    void collapse() {
        animateTo(WidthMode.COLLAPSED);
    }

    void restoreDefault() {
        animateTo(WidthMode.DEFAULT);
    }

    private void animateTo(WidthMode targetMode) {
        stopAnimation();
        mode = targetMode;
        updateResizePolicy();
        if (split.getWidth() <= 0) {
            return;
        }
        double start = divider.getPosition();
        double target = targetPosition();
        if (Math.abs(start - target) < 0.000001) {
            return;
        }
        Timeline next = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(divider.positionProperty(), start)),
                new KeyFrame(ANIMATION_DURATION,
                        new KeyValue(divider.positionProperty(), target, EASE_OUT))
        );
        animation = next;
        next.setOnFinished(event -> {
            if (animation == next) {
                animation = null;
            }
        });
        next.play();
    }

    private void synchronizeSize() {
        // 窗口缩放直接跟手；预设状态仍贴住对应极限或保持固定默认宽度。
        stopAnimation();
        if (mode != WidthMode.CUSTOM && split.getWidth() > 0) {
            divider.setPosition(targetPosition());
        }
    }

    private void updateResizePolicy() {
        // 全展开时让右区吸收窗口增量；其他状态保留右区的像素宽度。
        boolean expanded = mode == WidthMode.EXPANDED;
        SplitPane.setResizableWithParent(split.getItems().get(0), !expanded);
        SplitPane.setResizableWithParent(split.getItems().get(1), expanded);
    }

    private double targetPosition() {
        return switch (mode) {
            case EXPANDED -> 0;
            case COLLAPSED -> 1;
            case DEFAULT -> {
                double width = split.getWidth() - split.getInsets().getLeft() - split.getInsets().getRight();
                double dividerWidth = split.lookupAll(".split-pane-divider").stream()
                        .filter(node -> node.getParent() == split)
                        .mapToDouble(node -> node.prefWidth(-1))
                        .findFirst().orElse(0);
                // SplitPane 的位置位于分隔条中心，恢复宽度时要包含半条分隔线。
                yield width <= 0 ? 1 : Math.max(0, Math.min(1,
                        1 - (defaultWidth + dividerWidth / 2) / width));
            }
            case CUSTOM -> divider.getPosition();
        };
    }

    private void takeOverForDrag(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !(event.getTarget() instanceof Node target)) {
            return;
        }
        for (Node node = target; node != null && node != split; node = node.getParent()) {
            if (node.getParent() == split && node.getStyleClass().contains("split-pane-divider")) {
                stopAnimation();
                mode = WidthMode.CUSTOM;
                updateResizePolicy();
                // 不消费事件，让 SplitPane 原生拖动立即接管。
                return;
            }
        }
    }

    private void stopAnimation() {
        if (animation == null) {
            return;
        }
        // Timeline 的属性更新可能领先本帧布局；中断时从实际已显示的边界接续。
        double current = displayedPosition();
        animation.stop();
        animation = null;
        divider.setPosition(current);
    }

    private double displayedPosition() {
        double width = split.getWidth() - split.getInsets().getLeft() - split.getInsets().getRight();
        if (width <= 0) {
            return divider.getPosition();
        }
        return split.lookupAll(".split-pane-divider").stream()
                .filter(node -> node.getParent() == split && node.getLayoutBounds().getWidth() > 0)
                .mapToDouble(node -> (node.getBoundsInParent().getMinX()
                        + node.getLayoutBounds().getWidth() / 2 - split.getInsets().getLeft()) / width)
                .findFirst().orElse(divider.getPosition());
    }

    private enum WidthMode {
        DEFAULT, EXPANDED, COLLAPSED, CUSTOM
    }
}
