package craken.ui.interaction;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;

import java.util.Objects;

/** 一个列表项拥有一个有类型的交互容器；切换不会重建或关闭该容器。 */
public final class InteractionItem<T extends Node> implements AutoCloseable {
    /** 列表项的可选结果标记；由内容自己维护，用于列表着色，默认 {@link #NONE}。 */
    public enum Result { NONE, PASSED, FAILED }

    private final String title;
    private final T content;
    private final Runnable onShown;
    private final Runnable onSelected;
    private final Runnable onClosed;
    private final ReadOnlyObjectWrapper<Result> result = new ReadOnlyObjectWrapper<>(Result.NONE);
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

    public Result result() { return result.get(); }

    public ReadOnlyObjectProperty<Result> resultProperty() { return result.getReadOnlyProperty(); }

    /** 更新结果标记；传 null 等价于 {@link Result#NONE}。 */
    public void setResult(Result next) {
        result.set(next == null ? Result.NONE : next);
    }

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
