package minic.ui.interaction;

import javafx.scene.Node;

import java.util.Objects;

/** 一个列表项拥有一个有类型的交互容器；切换不会重建或关闭该容器。 */
public final class InteractionItem<T extends Node> implements AutoCloseable {
    private final String title;
    private final T content;
    private final Runnable onShown;
    private final Runnable onSelected;
    private final Runnable onClosed;
    private boolean closed;

    public InteractionItem(String title, T content) {
        this(title, content, () -> {}, () -> {});
    }

    public InteractionItem(String title, T content, Runnable onSelected, Runnable onClosed) {
        this(title, content, onSelected, onSelected, onClosed);
    }

    /** 挂载只负责准备内容，显式选择才决定是否切换输入焦点。 */
    public InteractionItem(String title, T content, Runnable onShown, Runnable onSelected, Runnable onClosed) {
        this.title = Objects.requireNonNull(title, "title");
        this.content = Objects.requireNonNull(content, "content");
        this.onShown = Objects.requireNonNull(onShown, "onShown");
        this.onSelected = Objects.requireNonNull(onSelected, "onSelected");
        this.onClosed = Objects.requireNonNull(onClosed, "onClosed");
    }

    public String title() { return title; }

    public T content() { return content; }

    public boolean isClosed() { return closed; }

    void shown() {
        if (!closed) onShown.run();
    }

    void selected() {
        if (!closed) onSelected.run();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        onClosed.run();
    }

    @Override
    public String toString() { return title; }
}
