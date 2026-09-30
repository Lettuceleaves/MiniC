package minic.ui.component.action;

import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import minic.ui.component.UiComponent;

/** 在选中与未选中状态间切换的操作按钮。 */
public final class UiToggleButton extends ToggleButton implements UiComponent {

    public UiToggleButton() {
        this("");
    }

    public UiToggleButton(String text) {
        super(text);
        setMinSize(0, 0);
        getStyleClass().add("ui-toggle-button");
        setPrefSize(Region.USE_COMPUTED_SIZE, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
