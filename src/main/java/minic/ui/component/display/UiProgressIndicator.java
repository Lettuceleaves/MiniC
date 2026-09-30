package minic.ui.component.display;

import javafx.scene.control.ProgressIndicator;
import minic.ui.component.UiComponent;

/** 圆形任务进度。 */
public final class UiProgressIndicator extends ProgressIndicator implements UiComponent {

    public UiProgressIndicator() {
        this(INDETERMINATE_PROGRESS);
    }

    public UiProgressIndicator(double progress) {
        super(progress);
        setMinSize(0, 0);
        getStyleClass().add("ui-progress-indicator");
        setPrefSize(32, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
