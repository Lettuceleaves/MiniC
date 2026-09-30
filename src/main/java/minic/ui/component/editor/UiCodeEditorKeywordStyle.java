package minic.ui.component.editor;

import javafx.scene.paint.Color;

import java.util.Objects;

/** 编辑器中 C 关键词的可配置视觉样式。透明背景表示不单独填充背景。 */
public record UiCodeEditorKeywordStyle(
        Color foreground,
        Color background,
        boolean bold,
        boolean italic,
        boolean underline
) {
    public UiCodeEditorKeywordStyle {
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(background, "background");
    }
}
