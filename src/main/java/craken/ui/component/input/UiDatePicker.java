package craken.ui.component.input;

import java.time.LocalDate;
import javafx.scene.control.DatePicker;
import craken.ui.component.UiComponent;

/** 日期输入与日历选择。 */
public final class UiDatePicker extends DatePicker implements UiComponent {

    public UiDatePicker() {
        this(null);
    }

    public UiDatePicker(LocalDate value) {
        super(value);
        setMinSize(0, 0);
        getStyleClass().add("ui-date-picker");
        setPrefSize(200, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
