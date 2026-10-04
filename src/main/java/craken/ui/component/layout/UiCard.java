package craken.ui.component.layout;

import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 聚合一个主题的信息和操作。 */
public final class UiCard extends BorderPane implements UiComponent {

    public UiCard() {
        this(null);
    }

    public UiCard(Node content) {
        setMinSize(0, 0);
        setCenter(content);
        getStyleClass().add("ui-card");
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
