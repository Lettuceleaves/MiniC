package craken.ui.component.editor;

import java.util.Objects;

/** 光标列表中的一项；id 供调用方识别，text 用于显示，不自动插入编辑器。 */
public record UiCodeEditorListItem(String id, String text) {
    public UiCodeEditorListItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
    }

    /** 使用显示文本同时作为标识。 */
    public UiCodeEditorListItem(String text) {
        this(text, text);
    }
}
