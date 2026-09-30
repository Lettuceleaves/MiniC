package minic.ui.component.layout;

import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import minic.ui.component.UiComponent;

/** 带标题且可折叠的内容区段。 */
public final class UiSection extends TitledPane implements UiComponent {

    public UiSection() {
        this("", null);
    }

    public UiSection(String title, Node content) {
        super(title, content);
        setMinSize(0, 0);
        getStyleClass().add("ui-section");
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
