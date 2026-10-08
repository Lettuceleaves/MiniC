package craken.ui.interaction.inputoutput;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * 一个 IO 列表项的内容宿主。
 *
 * <p>列表项、标题与位置由 {@link craken.ui.interaction.InteractionArea} 持有并保持不变；
 * 运行与调试每次开始时只替换这里的通道。替换会先挂载新通道，再关闭旧通道
 * （停止旧进程、释放旧视图）；只有宿主已经位于场景中时才启动，未选中的项不会提前运行程序。</p>
 */
public final class InputOutputTab extends StackPane implements AutoCloseable {
    /**
     * 当前通道；三个回调分别对应列表项的挂载、显式选择和关闭。
     * 未提供的回调按空操作处理。
     */
    public record Channel(Node node, Runnable start, Runnable activate, Runnable close) {
        public Channel {
            Objects.requireNonNull(node, "node");
            start = start == null ? () -> { } : start;
            activate = activate == null ? () -> { } : activate;
            close = close == null ? () -> { } : close;
        }
    }

    private Channel channel;
    private boolean closed;

    public InputOutputTab() {
        setMinSize(0, 0);
        getStyleClass().add("input-output-tab");
    }

    public Channel channel() { return channel; }

    public boolean isClosed() { return closed; }

    /** 替换通道；已关闭的宿主不再接收新通道，直接释放传入的通道避免进程泄漏。 */
    public void show(Channel next) {
        Objects.requireNonNull(next, "next");
        if (closed) {
            next.close().run();
            return;
        }
        Channel previous = channel;
        channel = next;
        getChildren().setAll(next.node());
        if (previous != null && previous != next) previous.close().run();
        start();
    }

    /** 挂载时准备内容；未挂到场景（未选中的列表项）时不启动。 */
    public void start() {
        if (closed || channel == null || getScene() == null) return;
        channel.start().run();
    }

    /** 用户显式选择该项时切换输入焦点。 */
    public void activate() {
        if (closed || channel == null) return;
        channel.activate().run();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Channel previous = channel;
        channel = null;
        getChildren().clear();
        if (previous != null) previous.close().run();
    }
}
