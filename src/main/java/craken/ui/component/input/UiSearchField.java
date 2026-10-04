package craken.ui.component.input;

/** 为搜索场景提供明确语义的单行输入框。 */
public final class UiSearchField extends UiTextField {
    public UiSearchField() {
        getStyleClass().add("ui-search-field");
        setPromptText("搜索");
    }
}
