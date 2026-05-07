package minic.ui;

import minic.uiapi.UiSourceSpanDto;

import java.util.Objects;

/**
 * AST 树视图中的一行。
 *
 * @param label 展示文本
 * @param depth 树深度
 * @param active 是否高亮
 * @param range 源码范围；不存在时为 {@code null}
 */
public record MiniCAstTreeLine(String label, int depth, boolean active, UiSourceSpanDto range) {
    public MiniCAstTreeLine {
        Objects.requireNonNull(label, "label");
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
    }
}
