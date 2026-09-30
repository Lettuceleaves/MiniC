package minic.ui.component.action;

import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.layout.Region;
import minic.ui.component.UiComponent;

/** 同时提供主操作和附加菜单的按钮。 */
public final class UiSplitButton extends SplitMenuButton implements UiComponent {

    public UiSplitButton() {
        this(new MenuItem[0]);
    }

    public UiSplitButton(MenuItem... items) {
        super(items);
        setMinSize(0, 0);
        getStyleClass().add("ui-split-button");
        setPrefSize(Region.USE_COMPUTED_SIZE, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
