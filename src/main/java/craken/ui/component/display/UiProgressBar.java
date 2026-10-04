package craken.ui.component.display;

import javafx.scene.control.ProgressBar;
import craken.ui.component.UiComponent;

/** 水平任务进度。 */
public final class UiProgressBar extends ProgressBar implements UiComponent {

    public UiProgressBar() {
        this(INDETERMINATE_PROGRESS);
    }

    public UiProgressBar(double progress) {
        super(progress);
        setMinSize(0, 0);
        getStyleClass().add("ui-progress-bar");
        setPrefSize(180, 8);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
