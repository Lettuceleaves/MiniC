package craken.ui.component.layout;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 应用内部的主要功能窗口，不是 JavaFX Stage。 */
public final class UiWindow extends BorderPane implements UiComponent {

    public UiWindow() {
        setMinSize(0, 0);
        getStyleClass().add("ui-window");
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
