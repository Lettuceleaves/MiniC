package minic.ui;

import java.util.Objects;

/**
 * Visual Pane 中的单项展示数据。
 *
 * @param label 标签
 * @param hot 是否当前热点项
 */
public record MiniCVisualItem(String label, boolean hot) {
    public MiniCVisualItem {
        Objects.requireNonNull(label, "label");
    }
}
