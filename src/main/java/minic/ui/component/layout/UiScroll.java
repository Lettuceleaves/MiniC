package minic.ui.component.layout;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import minic.ui.component.UiComponent;

/** 为单个内容节点提供滚动视口。 */
public final class UiScroll extends ScrollPane implements UiComponent {

    public UiScroll() {
        this(null);
    }

    public UiScroll(Node content) {
        super(content);
        setMinSize(0, 0);
        setFitToWidth(true);
        getStyleClass().add("ui-scroll");
        setPrefSize(320, 240);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
