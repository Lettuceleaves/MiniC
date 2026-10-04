package craken.ui.interaction.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.pty4j.PtyProcess;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import craken.ui.component.feedback.UiTooltip;
import craken.ui.component.terminal.UiTerminalWidget;
import craken.ui.component.swing.UiSwingNodeSurface;
import craken.ui.component.swing.UiSwingFocus;
import craken.ui.component.swing.UiSwingNode;

import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** 标准终端：JediTerm 解释 VT 序列并处理键盘，ConPTY 承载原生 PowerShell。 */
public final class TerminalPanel extends BorderPane implements AutoCloseable {
    private final Path workingDirectory;
    private final Path executable;
    private final UiSwingNode surface = new UiSwingNode();
    private final Label status = new Label("尚未启动");
    private final Label message = new Label("正在启动 PowerShell…");
    private final Button stop;
    private final Object widgetLock = new Object();
    private volatile JediTermWidget widget;
    private final UiSwingFocus focus = new UiSwingFocus(surface, () -> widget);
    private volatile PowerShellSession session;
    private volatile long generation;
    private volatile boolean closed;
    private volatile boolean surfaceAttached;

    public TerminalPanel(Path workingDirectory) {
        this(workingDirectory, null);
    }

    /** 独立运行会话；重启时仍在相同工作目录执行同一产物。 */
    public TerminalPanel(Path workingDirectory, Path executable) {
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath().normalize();
        this.executable = executable == null ? null : executable.toAbsolutePath().normalize();
        setMinSize(0, 0);
        getStyleClass().add("terminal-panel");
        Label title = new Label(executable == null ? "PowerShell" : "运行 · " + this.executable.getFileName());
        title.getStyleClass().add("terminal-title");
        title.setTooltip(new UiTooltip(executable == null ? "初始目录" : "运行产物",
                executable == null ? this.workingDirectory.toString()
                        : this.executable + "\n工作目录：" + this.workingDirectory, ""));
        status.getStyleClass().add("terminal-status");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        stop = action("停止", "结束当前会话；中断命令请按 Ctrl+C", "terminal-stop", this::stop);
        HBox toolbar = new HBox(8, title, status, spacer,
                action("清空", "清空终端滚动历史", "terminal-clear", this::clearOutput), stop,
                action("重启", executable == null ? "在项目根目录重新启动 PowerShell" : "在初始目录重新运行此产物",
                        "terminal-restart", this::restart));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 8, 0, 12));
        toolbar.setMinHeight(32);
        toolbar.setPrefHeight(32);
        toolbar.getStyleClass().add("interaction-toolbar");
        setTop(toolbar);

        surface.setId("terminal-surface");
        surface.setFocusTraversable(true);
        surface.setAccessibleText(executable == null ? "PowerShell 终端" : "程序运行终端");
        surface.addEventFilter(KeyEvent.KEY_PRESSED, this::forwardSpecialKey);
        surface.sceneProperty().addListener((observable, previous, scene) -> {
            boolean attached = scene != null;
            surfaceAttached = attached;
            JediTermWidget terminal = widget;
            long current = generation;
            if (terminal == null) return;
            // 捕获本次挂载对应的 widget，旧场景的回调不能释放重启后的新实例。
            if (!attached) {
                SwingUtilities.invokeLater(() -> UiSwingNodeSurface.release(terminal));
            } else {
                // SwingNode 的内部 scene 监听可能后注册；等它先提交新承载窗的创建。
                Platform.runLater(() -> {
                    if (!isCurrent(current) || terminal != widget || !surfaceAttached) return;
                    SwingUtilities.invokeLater(() -> {
                        if (isCurrent(current) && terminal == widget && surfaceAttached) {
                            UiSwingNodeSurface.prepare(terminal);
                        }
                    });
                });
            }
        });
        message.getStyleClass().add("terminal-status");
        message.setWrapText(true);
        message.setMouseTransparent(true);
        StackPane viewport = new StackPane(surface, message);
        viewport.setMinSize(0, 0);
        viewport.setPrefSize(640, 160);
        viewport.getStyleClass().add("terminal-viewport");
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);
        setCenter(viewport);
        updateState(PowerShellSession.State.NEW);
    }

    public Path workingDirectory() { return workingDirectory; }

    /** 挂载或后台运行时只启动会话，不改变当前输入焦点。 */
    public void start() {
        if (closed) return;
        if (session == null) startSession();
    }

    /** 用户选择此面板时同步切换焦点，不重启 shell。 */
    public void activate() {
        if (closed) return;
        start();
        focus.requestFocus();
    }

    public void clearOutput() {
        JediTermWidget current = widget;
        if (current != null) SwingUtilities.invokeLater(() -> current.getTerminalPanel().clearBuffer());
    }

    public void stop() {
        PowerShellSession current = session;
        if (current != null) current.close();
    }

    public void restart() {
        if (closed) return;
        focus.cancelPending();
        generation++;
        stop();
        disposeWidget();
        startSession();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        focus.close();
        generation++;
        stop();
        disposeWidget();
        updateState(PowerShellSession.State.CLOSED);
    }

    // 供同包集成测试验证真正的终端键盘、VT 渲染和 PTY 生命周期。
    JediTermWidget widget() { return widget; }
    PowerShellSession session() { return session; }

    private void startSession() {
        long current = ++generation;
        updateState(PowerShellSession.State.STARTING);
        message.setText(executable == null ? "正在启动 PowerShell…" : "正在运行 " + executable.getFileName() + "…");
        message.setVisible(true);
        CompletableFuture<JediTermWidget> ready = new CompletableFuture<>();
        PowerShellSession next = new PowerShellSession(workingDirectory, executable, new PowerShellSession.Listener() {
            @Override
            public void onStarted(PtyProcess process) {
                PowerShellSession owner = session;
                ready.thenAccept(terminal -> SwingUtilities.invokeLater(() -> {
                    if (!isCurrent(current)) return;
                    terminal.createTerminalSession(new Connector(process, owner));
                    owner.resize(terminal.getTerminalTextBuffer().getWidth(), terminal.getTerminalTextBuffer().getHeight());
                    terminal.start();
                    onFx(current, () -> {
                        message.setVisible(false);
                        focus.focusContentIfOwned();
                    });
                }));
            }

            @Override
            public void onStateChanged(PowerShellSession.State state) {
                onFx(current, () -> updateState(state));
            }

            @Override
            public void onExit(int exitCode) {
                onFx(current, () -> status.setText("已退出（" + exitCode + "）"));
            }

            @Override
            public void onError(Throwable error) {
                onFx(current, () -> {
                    message.setText("PowerShell 连接失败：" + error.getMessage());
                    message.setVisible(true);
                });
            }
        });
        session = next;
        SwingUtilities.invokeLater(() -> {
            if (!isCurrent(current)) return;
            try {
                JediTermWidget terminal = new Widget();
                terminal.setMinimumSize(new Dimension(0, 0));
                terminal.getTerminalPanel().setMinimumSize(new Dimension(0, 0));
                terminal.getTerminalPanel().setFocusTraversalKeysEnabled(false);
                terminal.getTerminalPanel().enableInputMethods(true);
                synchronized (widgetLock) {
                    if (!isCurrent(current)) {
                        terminal.close();
                        ready.cancel(false);
                        return;
                    }
                    widget = terminal;
                }
                surface.setContent(terminal);
                if (isCurrent(current) && terminal == widget && surfaceAttached) {
                    UiSwingNodeSurface.prepare(terminal);
                }
                ready.complete(terminal);
            } catch (RuntimeException | LinkageError error) {
                ready.completeExceptionally(error);
                next.close();
                onFx(current, () -> {
                    message.setText("终端初始化失败：" + error.getMessage());
                    message.setVisible(true);
                });
            }
        });
        next.start();
    }

    private boolean isCurrent(long current) { return !closed && current == generation; }

    private void forwardSpecialKey(KeyEvent event) {
        // JavaFX 为独立修饰键提供 NUL；不要把它当作终端输入。
        if (event.getCode().isModifierKey()) {
            event.consume();
            return;
        }
        int code = event.getCode().getCode();
        int character;
        if (event.getCode() == KeyCode.ESCAPE) {
            character = 27;
        } else {
            if (!event.isControlDown() || event.isShiftDown() || event.isAltDown() || event.isMetaDown()) return;
            character = code >= KeyCode.A.getCode() && code <= KeyCode.Z.getCode()
                    ? code - KeyCode.A.getCode() + 1 : switch (event.getCode()) {
                    case OPEN_BRACKET -> 27;
                    case BACK_SLASH -> 28;
                    case CLOSE_BRACKET -> 29;
                    default -> -1;
                };
        }
        if (character < 0) return;
        // Windows JavaFX 的按下事件仍是字母 c；JediTerm 需要 AWT 的控制字符 3。
        // 只规范化这类按键，保留 SwingNode 对文本、输入法和其他快捷键的原生转发。
        event.consume();
        JediTermWidget current = widget;
        if (current == null) return;
        int modifiers = (event.isControlDown() ? InputEvent.CTRL_DOWN_MASK : 0)
                | (event.isShiftDown() ? InputEvent.SHIFT_DOWN_MASK : 0)
                | (event.isAltDown() ? InputEvent.ALT_DOWN_MASK : 0)
                | (event.isMetaDown() ? InputEvent.META_DOWN_MASK : 0);
        SwingUtilities.invokeLater(() -> {
            if (closed || current != widget) return;
            var panel = current.getTerminalPanel();
            panel.processKeyEvent(new java.awt.event.KeyEvent(panel,
                    java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                    modifiers, code, (char) character));
        });
    }

    private void disposeWidget() {
        JediTermWidget previous;
        synchronized (widgetLock) {
            previous = widget;
            widget = null;
        }
        if (previous != null) SwingUtilities.invokeLater(() -> {
            try {
                UiSwingNodeSurface.release(previous);
            } finally {
                previous.close();
            }
        });
    }

    private void updateState(PowerShellSession.State state) {
        status.setText(switch (state) {
            case NEW -> "尚未启动";
            case STARTING -> "启动中…";
            case RUNNING -> "运行中";
            case STOPPING -> "停止中…";
            case EXITED -> "已退出";
            case FAILED -> "连接失败";
            case CLOSED -> "已停止";
        });
        stop.setDisable(closed || (state != PowerShellSession.State.RUNNING
                && state != PowerShellSession.State.STARTING));
    }

    private void onFx(long current, Runnable action) {
        Platform.runLater(() -> { if (isCurrent(current)) action.run(); });
    }

    private static Button action(String text, String help, String id, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.setPadding(new Insets(2, 6, 2, 6));
        button.setMinHeight(24);
        button.setPrefHeight(24);
        button.getStyleClass().add("interaction-action");
        button.setAccessibleText(help);
        button.setTooltip(new UiTooltip(help));
        button.setOnAction(event -> action.run());
        return button;
    }

    /** 只有 JediTerm 读取 PTY 输出；关闭交给 session 统一清理整个进程树。 */
    private static final class Connector extends ProcessTtyConnector {
        private final PowerShellSession session;

        Connector(PtyProcess process, PowerShellSession session) {
            super(process, StandardCharsets.UTF_8, List.of("powershell.exe"));
            this.session = session;
        }

        @Override public String getName() { return "PowerShell"; }
        @Override public void write(byte[] bytes) throws IOException {
            if (!session.write(bytes)) throw new IOException("PowerShell session has ended");
        }
        @Override public void resize(TermSize size) { session.resize(size.getColumns(), size.getRows()); }
        @Override public void close() { session.close(); }
    }

    static final class Widget extends UiTerminalWidget { }

    static final class Settings extends UiTerminalWidget.Settings { }
}
