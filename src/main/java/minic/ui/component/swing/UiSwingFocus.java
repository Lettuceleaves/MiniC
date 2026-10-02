package minic.ui.component.swing;

import javafx.application.Platform;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.function.Supplier;

/** FX 决定焦点归属；延后的 Swing 子控件请求不能覆盖后续的用户选择。 */
public final class UiSwingFocus implements AutoCloseable {
    private final UiSwingNode node;
    private final Supplier<? extends Component> content;
    private volatile boolean focused;
    private volatile boolean closed;
    private volatile long revision;

    public UiSwingFocus(UiSwingNode node, Supplier<? extends Component> content) {
        this.node = node;
        this.content = content;
        node.focusedProperty().addListener((observable, previous, current) -> {
            focused = current;
            cancelPending();
            if (current) {
                long request = revision;
                // SwingNode 的内部 focused 监听可能后注册；先让它提交原生承载窗激活，
                // 再聚焦子控件。这里只延后内部焦点，不延后申请 FX 焦点。
                Platform.runLater(() -> {
                    if (!closed && focused && revision == request) focusContentIfOwned();
                });
            }
        });
        node.sceneProperty().addListener((observable, previous, current) -> {
            focused = current != null && node.isFocused();
            cancelPending();
        });
    }

    /** 用户明确选择此区域时在 FX 线程同步调用，不延后申请 FX 焦点。 */
    public void requestFocus() {
        if (closed) return;
        node.requestUserFocus();
        focusContentIfOwned();
    }

    /** 内容异步就绪时只补全当前区域的内部焦点，不申请其他区域的焦点。 */
    public void focusContentIfOwned() {
        long request = ++revision;
        if (closed || !focused) return;
        Component target = content.get();
        if (target == null) return;
        SwingUtilities.invokeLater(() -> {
            if (!closed && focused && revision == request) target.requestFocusInWindow();
        });
    }

    /** 在 FX 线程切换内容或卸载时取消尚未执行的子控件请求。 */
    public void cancelPending() { revision++; }

    @Override
    public void close() {
        closed = true;
        cancelPending();
    }
}
