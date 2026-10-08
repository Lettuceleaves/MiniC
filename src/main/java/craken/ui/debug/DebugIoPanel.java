package craken.ui.debug;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import craken.ui.component.feedback.UiTooltip;
import craken.ui.component.swing.UiSwingFocus;
import craken.ui.component.swing.UiSwingNode;
import craken.ui.component.swing.UiSwingNodeSurface;
import craken.ui.component.terminal.UiTerminalWidget;

import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * 调试会话的用户程序输入输出面板（IO 列表项内容）。
 *
 * <p>与运行功能的 IO 项共用同一个终端组件 {@link UiTerminalWidget}，字体、字号、配色与行距
 * 完全一致；区别只在数据源：解释器不创建子进程，标准输出/标准错误由调试线程推送到终端的读取端，
 * 键盘输入按行排队交给程序的下一次读取。标准错误用红色显示。</p>
 */
final class DebugIoPanel extends BorderPane implements AutoCloseable {
    private static final char ESCAPE = '\u001b';
    private static final String ERROR_COLOR = ESCAPE + "[31m";
    private static final String ERROR_RESET = ESCAPE + "[39m";
    /** 关闭时唤醒阻塞的终端读取线程。 */
    private static final Object CLOSED = new Object();
    private final String title;
    private final Object widgetLock = new Object();
    private final UiSwingNode surface = new UiSwingNode();
    private final Label status = new Label("尚未开始");
    private final Feed feed = new Feed();
    private volatile UiTerminalWidget widget;
    private volatile Connector connector;
    private final UiSwingFocus focus = new UiSwingFocus(surface, () -> widget);
    private volatile Consumer<String> onInput = text -> { };
    private volatile boolean closed;
    private volatile boolean finished;
    private volatile boolean surfaceAttached;
    private boolean started;

    DebugIoPanel(Path sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        title = "输入输出 · " + sourcePath.getFileName();
        setMinSize(0, 0);
        getStyleClass().add("terminal-panel");
        Label caption = new Label(title);
        caption.setId("debug-io-title");
        caption.getStyleClass().add("terminal-title");
        caption.setTooltip(new UiTooltip("调试程序输入输出",
                sourcePath + "\n使用与运行 IO 项相同的终端字体", ""));
        status.setId("debug-io-status");
        status.getStyleClass().add("terminal-status");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, caption, status, spacer,
                action("清空", "清空调试程序输出历史", "debug-io-clear", this::clearOutput));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 8, 0, 12));
        toolbar.setMinHeight(32);
        toolbar.setPrefHeight(32);
        toolbar.getStyleClass().add("interaction-toolbar");
        setTop(toolbar);

        surface.setId("debug-io-surface");
        surface.setFocusTraversable(true);
        surface.setAccessibleText("调试程序输入输出");
        surface.sceneProperty().addListener((observable, previous, scene) -> {
            surfaceAttached = scene != null;
            UiTerminalWidget terminal = widget;
            if (terminal == null) return;
            if (!surfaceAttached) {
                SwingUtilities.invokeLater(() -> UiSwingNodeSurface.release(terminal));
            } else {
                // 等 SwingNode 内部 scene 监听先重建承载窗，再恢复局部绘制优化。
                Platform.runLater(() -> {
                    if (closed || terminal != widget || !surfaceAttached) return;
                    SwingUtilities.invokeLater(() -> {
                        if (!closed && terminal == widget && surfaceAttached) UiSwingNodeSurface.prepare(terminal);
                    });
                });
            }
        });
        StackPane viewport = new StackPane(surface);
        viewport.setMinSize(0, 0);
        viewport.setPrefSize(640, 160);
        viewport.getStyleClass().add("terminal-viewport");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);
        setCenter(viewport);
    }

    /** 输入行提交回调：收到的是不含换行的整行，始终在 JavaFX 线程调用。 */
    void setOnInput(Consumer<String> action) {
        onInput = Objects.requireNonNull(action, "action");
    }

    boolean isClosed() { return closed; }

    boolean isFinished() { return finished; }

    /** 供包内测试读取终端缓冲区。 */
    UiTerminalWidget widget() { return widget; }

    /** 一次新的调试运行：面板按次新建，这里只把状态从“尚未开始”切到“调试中”。 */
    void begin() {
        if (closed) return;
        finished = false;
        status.setText("调试中");
    }

    void appendStdout(String text) {
        feed.write(terminalNewlines(text));
    }

    /** 标准错误用红色显示，与标准输出共用同一条终端流，保持到达顺序。 */
    void appendStderr(String text) {
        if (text == null || text.isEmpty()) return;
        feed.write(ERROR_COLOR + terminalNewlines(text) + ERROR_RESET);
    }

    /**
     * 解释器不经过 pty，程序输出的 LF 需要补成 CRLF，才和运行（ConPTY 的 ONLCR）落在同一列。
     * 单独的 CR（进度条覆盖同一行）保持原样。
     */
    private static String terminalNewlines(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.replace("\r\n", "\n").replace("\n", "\r\n");
    }

    /** 程序正常结束或异常终止：保留输出，忽略后续键盘输入。 */
    void finish(String description) {
        if (closed) return;
        finished = true;
        status.setText(description);
    }

    /** 调试命令失败：把原因写进输出，再按结束状态收口。 */
    void failure(String message) {
        appendStderr("调试失败：" + message + "\n");
        finish("调试失败");
    }

    void clearOutput() {
        UiTerminalWidget current = widget;
        if (current != null) SwingUtilities.invokeLater(() -> current.getTerminalPanel().clearBuffer());
    }

    /** 通道挂载：创建终端；未挂载时输出留在队列里，显示后再补画。 */
    void start() {
        ensureStarted();
    }

    /** 用户选择该项时把键盘焦点交给终端。 */
    void activate() {
        if (closed) return;
        ensureStarted();
        focus.requestFocus();
    }

    /** 无鼠标环境（测试）的按键入口：与终端键盘发送路径一致。 */
    void typeText(String text) {
        Connector current = connector;
        if (current != null) current.typed(text);
    }

    private void ensureStarted() {
        if (closed || started) return;
        started = true;
        SwingUtilities.invokeLater(() -> {
            if (closed) return;
            try {
                UiTerminalWidget terminal = new UiTerminalWidget(new UiTerminalWidget.TabDefaultFontSettings());
                terminal.setMinimumSize(new Dimension(0, 0));
                terminal.getTerminalPanel().setMinimumSize(new Dimension(0, 0));
                terminal.getTerminalPanel().setFocusTraversalKeysEnabled(false);
                terminal.getTerminalPanel().enableInputMethods(true);
                Connector connection = new Connector();
                synchronized (widgetLock) {
                    if (closed) {
                        terminal.close();
                        return;
                    }
                    widget = terminal;
                    connector = connection;
                }
                surface.setContent(terminal);
                if (!closed && terminal == widget && surfaceAttached) UiSwingNodeSurface.prepare(terminal);
                terminal.createTerminalSession(connection);
                terminal.start();
                Platform.runLater(focus::focusContentIfOwned);
            } catch (RuntimeException | LinkageError error) {
                feed.write(ERROR_COLOR + "输入输出初始化失败：" + error.getMessage() + "\n" + ERROR_RESET);
                status.setText("输入输出初始化失败");
            }
        });
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        focus.close();
        feed.close();
        Connector connection = connector;
        connector = null;
        if (connection != null) connection.close();
        UiTerminalWidget previous;
        synchronized (widgetLock) {
            previous = widget;
            widget = null;
        }
        if (previous != null) SwingUtilities.invokeLater(() -> {
            try { UiSwingNodeSurface.release(previous); }
            finally { previous.close(); }
        });
        status.setText("已关闭");
    }

    private static Button action(String text, String help, String id, Runnable handler) {
        Button button = new Button(text);
        button.setId(id);
        button.setPadding(new Insets(2, 6, 2, 6));
        button.setMinHeight(24);
        button.setPrefHeight(24);
        button.getStyleClass().add("interaction-action");
        button.setAccessibleText(help);
        button.setTooltip(new UiTooltip(help));
        button.setOnAction(event -> handler.run());
        return button;
    }

    /** 终端读取端：调试线程推送文本，终端读取线程阻塞等待；关闭用哨兵唤醒。 */
    private final class Feed {
        private final BlockingQueue<Object> chunks = new LinkedBlockingQueue<>();

        void write(String text) {
            if (closed || text == null || text.isEmpty()) return;
            chunks.add(text);
        }

        void close() {
            chunks.add(CLOSED);
        }

        Object take() throws InterruptedException {
            return chunks.take();
        }

        boolean ready() {
            return !chunks.isEmpty();
        }
    }

    /**
     * 解释器终端的伪连接器：读端来自调试线程推送的文本，写端把整行输入排给程序的标准输入。
     * 本地回显可见字符，并按终端惯例处理退格、换行和光标/方向键转义序列。
     */
    private final class Connector implements TtyConnector {
        private final Object connectionLock = new Object();
        private final StringBuilder line = new StringBuilder();
        private char[] pending = new char[0];
        private int pendingOffset;
        private int escapeState;
        private boolean skipLineFeed;
        private boolean connectionClosed;

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            while (true) {
                if (pendingOffset < pending.length) {
                    int count = Math.min(length, pending.length - pendingOffset);
                    System.arraycopy(pending, pendingOffset, buffer, offset, count);
                    pendingOffset += count;
                    return count;
                }
                Object chunk;
                try {
                    chunk = feed.take();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
                if (chunk == CLOSED) return -1;
                pending = ((String) chunk).toCharArray();
                pendingOffset = 0;
            }
        }

        @Override public void write(byte[] bytes) {
            typed(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override public void write(String text) {
            typed(text);
        }

        /** 键盘/粘贴输入：本地回显可见字符，整行回车后交给程序的标准输入。 */
        void typed(String text) {
            if (connectionClosed || finished || text == null || text.isEmpty()) return;
            StringBuilder echo = new StringBuilder();
            String submit = null;
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                if (escapeState != 0) {
                    swallowEscape(character);
                    continue;
                }
                if (character == ESCAPE) {
                    escapeState = 1;
                    continue;
                }
                if (character == '\n' && skipLineFeed) {
                    skipLineFeed = false;
                    continue;
                }
                skipLineFeed = false;
                if (character == '\r' || character == '\n') {
                    echo.append("\r\n");
                    submit = line.toString();
                    line.setLength(0);
                    if (character == '\r') skipLineFeed = true;
                } else if (character == '\u007f' || character == '\b') {
                    if (line.length() > 0) {
                        line.deleteCharAt(line.length() - 1);
                        echo.append("\b \b");
                    }
                } else if (character == '\t' || character >= 0x20) {
                    line.append(character);
                    echo.append(character);
                }
            }
            if (echo.length() > 0) feed.write(echo.toString());
            if (submit != null) {
                String completed = submit;
                Platform.runLater(() -> onInput.accept(completed));
            }
        }

        /** 方向键、功能键发送 CSI/SS3 序列：整段吞掉，避免 `[` `A` 被当成正文。 */
        private void swallowEscape(char character) {
            escapeState = switch (escapeState) {
                case 1 -> character == '[' ? 2 : character == 'O' ? 3 : 0;
                case 2 -> character >= 0x40 && character <= 0x7e ? 0 : 2;
                case 3 -> 0;
                default -> 0;
            };
        }

        @Override public boolean isConnected() { return !connectionClosed; }
        @Override public void resize(TermSize size) { }
        @Override public boolean ready() { return pendingOffset < pending.length || feed.ready(); }
        @Override public String getName() { return DebugIoPanel.this.title + "（调试）"; }

        @Override
        public int waitFor() throws InterruptedException {
            synchronized (connectionLock) {
                while (!connectionClosed) connectionLock.wait();
            }
            return 0;
        }

        @Override
        public void close() {
            synchronized (connectionLock) {
                connectionClosed = true;
                connectionLock.notifyAll();
            }
        }
    }
}
