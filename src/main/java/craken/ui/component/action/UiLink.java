package craken.ui.component.action;

import javafx.scene.control.Hyperlink;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 执行导航动作的文本链接。 */
public final class UiLink extends Hyperlink implements UiComponent {

    public UiLink() {
        this("");
    }

    public UiLink(String text) {
        super(text);
        setMinSize(0, 0);
        getStyleClass().add("ui-link");
        setPrefSize(Region.USE_COMPUTED_SIZE, 28);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
