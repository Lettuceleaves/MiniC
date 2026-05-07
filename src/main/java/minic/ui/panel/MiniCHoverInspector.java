package minic.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Objects;

/**
 * Graph View hover inspector 的共享状态。
 */
public final class MiniCHoverInspector {
    private final ObjectProperty<MiniCHoverInspectorContent> content =
            new SimpleObjectProperty<>(MiniCHoverInspectorContent.empty());

    /**
     * 当前内容属性。
     *
     * @return 只读内容属性
     */
    public ReadOnlyObjectProperty<MiniCHoverInspectorContent> contentProperty() {
        return content;
    }

    /**
     * 显示 hover 内容。
     *
     * @param content 内容
     */
    public void show(MiniCHoverInspectorContent content) {
        this.content.set(Objects.requireNonNull(content, "content"));
    }

    /**
     * 清空 hover 内容。
     */
    public void clear() {
        content.set(MiniCHoverInspectorContent.empty());
    }
}
