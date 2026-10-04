package craken.ui.component.feedback;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import craken.ui.component.UiComponent;

/** 页面或区域内持续显示的状态消息。 */
public final class UiMessage extends HBox implements UiComponent {
    public enum Kind { INFO, SUCCESS, WARNING, ERROR }
    private Kind kind;

    public UiMessage(Kind kind, Node... content) {
        getChildren().addAll(content);
        setMinSize(0, 0);
        getStyleClass().add("ui-message");
        setPrefSize(360, 40);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
        kind(kind);
    }

    public Kind kind() {
        return kind;
    }

    public void kind(Kind value) {
        if (value == null) {
            throw new IllegalArgumentException("message kind must not be null");
        }
        getStyleClass().removeIf(style -> style.startsWith("ui-message-") && !style.equals("ui-message"));
        kind = value;
        getStyleClass().add("ui-message-" + value.name().toLowerCase());
    }
}
