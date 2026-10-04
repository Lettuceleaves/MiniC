package craken.ui.component.input;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import craken.ui.component.UiComponent;

/** 将字段标签、输入组件和校验信息组合为一个表单字段。 */
public final class UiField extends VBox implements UiComponent {
    private final Label label = new Label();
    private final Label message = new Label();
    private Node input;

    public UiField(String labelText, Node input) {
        setMinSize(0, 0);
        getStyleClass().add("ui-field");
        label.getStyleClass().add("ui-field-label");
        message.getStyleClass().add("ui-field-message");
        label.setText(labelText);
        setInput(input);
        getChildren().add(message);
        message("");
        setPrefSize(240, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }

    public String label() {
        return label.getText();
    }

    public void label(String value) {
        label.setText(value);
    }

    public Node input() {
        return input;
    }

    public void setInput(Node value) {
        if (input != null) {
            getChildren().remove(input);
        }
        input = value;
        if (value != null) {
            int messageIndex = getChildren().indexOf(message);
            int insertionIndex = messageIndex < 0 ? getChildren().size() : messageIndex;
            getChildren().add(insertionIndex, value);
        }
        label.setLabelFor(value);
        if (!getChildren().contains(label)) {
            getChildren().add(0, label);
        }
    }

    public String message() {
        return message.getText();
    }

    public void message(String value) {
        message.setText(value);
        message.setManaged(value != null && !value.isBlank());
        message.setVisible(message.isManaged());
    }
}
