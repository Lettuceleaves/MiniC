package craken.ui.component.navigation;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import craken.ui.component.UiComponent;

/** 在同一区域切换相关内容。 */
public final class UiTabPane extends TabPane implements UiComponent {

    public UiTabPane(Tab... tabs) {
        super(tabs);
        setMinSize(0, 0);
        getStyleClass().add("ui-tab-pane");
        setPrefSize(480, 320);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
