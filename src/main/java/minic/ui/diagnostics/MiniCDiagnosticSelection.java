package minic.ui;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import minic.uiapi.UiSourceRangeDto;

/**
 * 当前被用户选中的 diagnostic 范围。
 */
public final class MiniCDiagnosticSelection {
    private final ReadOnlyObjectWrapper<UiSourceRangeDto> selectedRange = new ReadOnlyObjectWrapper<>();

    /**
     * 选择 diagnostic。
     *
     * @param item diagnostic 项
     */
    public void select(MiniCDiagnosticItem item) {
        selectedRange.set(item == null ? null : item.range());
    }

    /**
     * 当前选中源码范围。
     *
     * @return 源码范围属性
     */
    public ReadOnlyObjectProperty<UiSourceRangeDto> selectedRangeProperty() {
        return selectedRange.getReadOnlyProperty();
    }
}
