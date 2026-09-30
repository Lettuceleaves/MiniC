package minic.ui.component.layout;

import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import minic.ui.component.UiComponent;

/** 使用行列约束排列子组件的网格。 */
public final class UiGrid extends GridPane implements UiComponent {

    public UiGrid() {
        setMinSize(0, 0);
        getStyleClass().add("ui-grid");
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
