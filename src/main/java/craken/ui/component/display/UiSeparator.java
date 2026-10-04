package craken.ui.component.display;

import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import craken.ui.component.UiComponent;

/** 对内容分组的分隔线。 */
public final class UiSeparator extends Separator implements UiComponent {
    public UiSeparator() {
        this(Orientation.HORIZONTAL);
    }

    public UiSeparator(Orientation orientation) {
        super(orientation);
        setMinSize(0, 0);
        getStyleClass().add("ui-separator");
        if (orientation == Orientation.HORIZONTAL) {
            setPrefSize(160, 1);
        } else {
            setPrefSize(1, 120);
        }
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
