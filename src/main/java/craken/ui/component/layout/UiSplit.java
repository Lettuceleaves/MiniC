package craken.ui.component.layout;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import craken.ui.component.UiComponent;

/** 通过可拖动分隔条划分内容区域。 */
public final class UiSplit extends SplitPane implements UiComponent {

    public UiSplit(Node... items) {
        super(items);
        setMinSize(0, 0);
        getStyleClass().add("ui-split");
        setPrefSize(640, 400);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
