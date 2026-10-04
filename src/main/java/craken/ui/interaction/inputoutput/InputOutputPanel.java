package craken.ui.interaction.inputoutput;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.SettingsProvider;
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
import craken.ui.component.swing.UiSwingFocus;
import craken.ui.component.swing.UiSwingNode;
import craken.ui.component.swing.UiSwingNodeSurface;
import craken.ui.component.terminal.UiTerminalWidget;
import craken.ui.component.terminal.UiTerminalPanel;

import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用户程序专用的输入输出交互面板。直接连接可执行文件，不创建命令行 shell。
 * 生命周期与焦点方法在 JavaFX 线程调用；结束后保留输出，新的 Enter 请求关闭所属项。
 */
public final class InputOutputPanel extends BorderPane implements AutoCloseable {
    private final Path workingDirectory;
    private final Path executable;
    private final UiSwingNode surface = new UiSwingNode();
    private final Label status = new Label("尚未启动");
    private final Label message = new Label();
    private final Button stop;
    private final Object widgetLock = new Object();
    private final Object enterLock = new Object();
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private volatile Widget widget;
    private final UiSwingFocus focus = new UiSwingFocus(surface, () -> widget);
    private volatile ProgramSession session;
    private volatile Runnable onCloseRequest = () -> { };
    private volatile boolean closed;
    private volatile boolean finished;
    private volatile Integer exitCode;
    private volatile boolean processStarted;
    private volatile boolean surfaceAttached;
    private boolean outputDrained;
    private boolean enterDown;
    private boolean closeArmed;
    private String failure;

    public InputOutputPanel(Path workingDirectory, Path executable) {
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath().normalize();
        this.executable = Objects.requireNonNull(executable, "executable").toAbsolutePath().normalize();
        setMinSize(0, 0);
        getStyleClass().addAll("terminal-panel", "input-output-panel");
        Label title = new Label("输入输出 · " + this.executable.getFileName());
        title.getStyleClass().add("terminal-title");
        title.setTooltip(new UiTooltip("运行产物", this.executable + "\n工作目录：" + this.workingDirectory, ""));
        status.setId("input-output-status");
        status.getStyleClass().add("terminal-status");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        stop = action("停止", "停止当前程序；中断程序请按 Ctrl+C", "input-output-stop", this::stop);
        HBox toolbar = new HBox(8, title, status, spacer,
                action("清空", "清空程序输出历史", "input-output-clear", this::clearOutput), stop);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 8, 0, 12));
        toolbar.setMinHeight(32);
        toolbar.setPrefHeight(32);
        toolbar.getStyleClass().add("interaction-toolbar");
        setTop(toolbar);

        surface.setId("input-output-surface");
        surface.setFocusTraversable(true);
        surface.setAccessibleText("用户程序输入输出");
        surface.addEventFilter(KeyEvent.ANY, this::filterFxKey);
        addEventFilter(KeyEvent.ANY, event -> {
            // 程序结束时焦点也可能仍停在停止/清空按钮；关闭仍属于整个输入输出项。
            if (finished && isEnter(event)) filterFxKey(event);
        });
        surface.sceneProperty().addListener((observable, previous, scene) -> {
            surfaceAttached = scene != null;
            Widget terminal = widget;
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
        message.setId("input-output-message");
        message.setVisible(false);
        message.managedProperty().bind(message.visibleProperty());
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
        updateState(ProgramSession.State.NEW);
    }

    public Path workingDirectory() { return workingDirectory; }
    public Path executable() { return executable; }
    public boolean isFinished() { return finished; }
    /** 尚未退出或启动失败时返回 null。 */
    public Integer exitCode() { return exitCode; }

    /** 关闭请求由容器执行；一次新的 Enter 完整击键触发一次，回调始终位于 FX 线程。 */
    public void setOnCloseRequest(Runnable action) {
        onCloseRequest = Objects.requireNonNull(action, "action");
    }

    /** 只启动一次，不申请输入焦点，也不因重新挂载而重复执行程序。 */
    public void start() {
        if (!closed && session == null) startSession();
    }

    /** 用户明确选择此项时同步切换焦点。 */
    public void activate() {
        if (closed) return;
        start();
        focus.requestFocus();
    }

    public void clearOutput() {
        Widget current = widget;
        if (current != null) SwingUtilities.invokeLater(() -> current.getTerminalPanel().clearBuffer());
    }

    /** 非阻塞停止当前程序及其子进程；不会关闭其他交互项。 */
    public void stop() {
        ProgramSession current = session;
        if (current != null) current.close();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        focus.close();
        stop();
        Widget previous;
        synchronized (widgetLock) {
            previous = widget;
            widget = null;
        }
        if (previous != null) SwingUtilities.invokeLater(() -> {
            try { UiSwingNodeSurface.release(previous); }
            finally { previous.close(); }
        });
        status.setText("已关闭");
        stop.setDisable(true);
    }

    JediTermWidget widget() { return widget; }
    ProgramSession session() { return session; }

    private void startSession() {
        updateState(ProgramSession.State.STARTING);
        CompletableFuture<Widget> ready = new CompletableFuture<>();
        ProgramSession next = new ProgramSession(workingDirectory, executable, new ProgramSession.Listener() {
            @Override
            public void onStarted(PtyProcess process) {
                processStarted = true;
                ProgramSession owner = session;
                ready.thenAccept(terminal -> SwingUtilities.invokeLater(() -> {
                    if (closed || terminal != widget) return;
                    terminal.createTerminalSession(new Connector(process, owner));
                    owner.resize(terminal.getTerminalTextBuffer().getWidth(), terminal.getTerminalTextBuffer().getHeight());
                    terminal.start();
                    onFx(() -> {
                        message.setVisible(false);
                        focus.focusContentIfOwned();
                    });
                }));
            }

            @Override public void onStateChanged(ProgramSession.State state) { onFx(() -> updateState(state)); }

            @Override
            public void onExit(int code) {
                onFx(() -> {
                    exitCode = code;
                    finishWhenDrained();
                });
            }

            @Override
            public void onError(Throwable error) {
                onFx(() -> {
                    failure = Objects.toString(error.getMessage(), error.getClass().getSimpleName());
                    if (!processStarted) {
                        message.setText("程序启动失败：" + failure);
                        message.setVisible(true);
                    }
                });
            }
        });
        session = next;
        SwingUtilities.invokeLater(() -> {
            if (closed) return;
            try {
                Widget terminal = new Widget();
                terminal.setMinimumSize(new Dimension(0, 0));
                terminal.getTerminalPanel().setMinimumSize(new Dimension(0, 0));
                terminal.getTerminalPanel().setFocusTraversalKeysEnabled(false);
                terminal.getTerminalPanel().enableInputMethods(true);
                synchronized (widgetLock) {
                    if (closed) {
                        terminal.close();
                        ready.cancel(false);
                        return;
                    }
                    widget = terminal;
                }
                surface.setContent(terminal);
                if (!closed && terminal == widget && surfaceAttached) UiSwingNodeSurface.prepare(terminal);
                ready.complete(terminal);
                // ConPTY 对快退出进程的首位输出 reader 只有有限等待时间；先备好显示再启动程序。
                next.start();
            } catch (RuntimeException | LinkageError error) {
                ready.completeExceptionally(error);
                onFx(() -> {
                    message.setText("输入输出面板初始化失败：" + error.getMessage());
                    message.setVisible(true);
                    complete("启动失败");
                });
                next.close();
            }
        });
    }

    private void updateState(ProgramSession.State state) {
        if (finished) return;
        status.setText(switch (state) {
            case NEW -> "尚未启动";
            case STARTING -> "启动中…";
            case RUNNING -> "运行中";
            case STOPPING -> "停止中…";
            case EXITED -> "正在读取剩余输出…";
            case FAILED -> "启动失败";
            case CLOSED -> "已停止";
        });
        stop.setDisable(closed || (state != ProgramSession.State.RUNNING && state != ProgramSession.State.STARTING));
        if (!processStarted && (state == ProgramSession.State.FAILED || state == ProgramSession.State.CLOSED)) {
            if (state == ProgramSession.State.CLOSED && failure == null) message.setVisible(false);
            complete(state == ProgramSession.State.FAILED ? "启动失败" : "已停止");
        }
        finishWhenDrained();
    }

    private void finishWhenDrained() {
        if (outputDrained && exitCode != null) {
            complete("已退出（退出码 " + exitCode + "）" + (failure == null ? "" : " · " + failure));
        }
    }

    private void complete(String description) {
        if (finished || closed) return;
        // 不重置 enterDown：结束前提交给程序的 Enter 必须释放后才能用于关闭。
        finished = true;
        status.setText(description + " · 按回车关闭此项");
        stop.setDisable(true);
    }

    private void outputDrained() {
        onFx(() -> {
            outputDrained = true;
            finishWhenDrained();
        });
    }

    private void onFx(Runnable action) {
        Platform.runLater(() -> { if (!closed) action.run(); });
    }

    private boolean enter(boolean pressed, boolean released) {
        synchronized (enterLock) {
            boolean freshPress = pressed && !enterDown;
            if (pressed) {
                enterDown = true;
                if (freshPress) closeArmed = !closed && finished;
            }
            if (released) {
                enterDown = false;
                // 等释放再关闭，避免长按的 repeat 落到下一项并连续关闭相邻面板。
                if (!closed && closeArmed && closeRequested.compareAndSet(false, true)) {
                    onFx(() -> onCloseRequest.run());
                }
                closeArmed = false;
            }
            return closed || finished;
        }
    }

    private void filterFxKey(KeyEvent event) {
        if (isEnter(event)) {
            boolean ignore = enter(event.getEventType() == KeyEvent.KEY_PRESSED,
                    event.getEventType() == KeyEvent.KEY_RELEASED);
            // 消费整个 FX Enter 序列并自行转发，防止其迟到的 AWT 副本在退出后被当成新按键。
            event.consume();
            Widget current = widget;
            if (ignore || current == null) return;
            int id = event.getEventType() == KeyEvent.KEY_PRESSED ? java.awt.event.KeyEvent.KEY_PRESSED
                    : event.getEventType() == KeyEvent.KEY_RELEASED ? java.awt.event.KeyEvent.KEY_RELEASED
                    : java.awt.event.KeyEvent.KEY_TYPED;
            int modifiers = modifiers(event);
            SwingUtilities.invokeLater(() -> {
                if (closed || finished || current != widget) return;
                ((GuardedTerminalPanel) current.getTerminalPanel()).forwardEnter(id, modifiers);
            });
            return;
        }
        if (event.getEventType() != KeyEvent.KEY_PRESSED) return;
        if (event.getCode().isModifierKey()) {
            event.consume();
            return;
        }
        int code = event.getCode().getCode();
        int character;
        if (event.getCode() == KeyCode.ESCAPE) character = 27;
        else {
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
        event.consume();
        Widget current = widget;
        if (current == null) return;
        int modifiers = modifiers(event);
        SwingUtilities.invokeLater(() -> {
            if (closed || current != widget) return;
            var terminal = current.getTerminalPanel();
            terminal.processKeyEvent(new java.awt.event.KeyEvent(terminal,
                    java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, code, (char) character));
        });
    }

    private static boolean isEnter(KeyEvent event) {
        return event.getCode() == KeyCode.ENTER || (event.getEventType() == KeyEvent.KEY_TYPED
                && ("\r".equals(event.getCharacter()) || "\n".equals(event.getCharacter())));
    }

    private static int modifiers(KeyEvent event) {
        return (event.isControlDown() ? InputEvent.CTRL_DOWN_MASK : 0)
                | (event.isShiftDown() ? InputEvent.SHIFT_DOWN_MASK : 0)
                | (event.isAltDown() ? InputEvent.ALT_DOWN_MASK : 0)
                | (event.isMetaDown() ? InputEvent.META_DOWN_MASK : 0);
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

    private final class Widget extends UiTerminalWidget {
        @Override
        protected UiTerminalPanel createTerminalPanel(SettingsProvider settings,
                StyleState style, TerminalTextBuffer buffer) {
            return new GuardedTerminalPanel(settings, buffer, style);
        }
    }

    private final class GuardedTerminalPanel extends UiTerminalPanel {
        GuardedTerminalPanel(SettingsProvider settings, TerminalTextBuffer buffer, StyleState style) {
            super(settings, buffer, style);
        }

        @Override
        public void processKeyEvent(java.awt.event.KeyEvent event) {
            boolean isEnter = event.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER
                    || (event.getID() == java.awt.event.KeyEvent.KEY_TYPED
                    && (event.getKeyChar() == '\n' || event.getKeyChar() == '\r'));
            if (isEnter && enter(event.getID() == java.awt.event.KeyEvent.KEY_PRESSED,
                    event.getID() == java.awt.event.KeyEvent.KEY_RELEASED)) {
                event.consume();
                return;
            }
            if (closed) {
                event.consume();
                return;
            }
            super.processKeyEvent(event);
        }

        void forwardEnter(int id, int modifiers) {
            // FX 已更新按下/释放状态，转发时不能第二次判定 Enter 是否为新按键。
            super.processKeyEvent(new java.awt.event.KeyEvent(this, id, System.currentTimeMillis(), modifiers,
                    id == java.awt.event.KeyEvent.KEY_TYPED ? java.awt.event.KeyEvent.VK_UNDEFINED
                            : java.awt.event.KeyEvent.VK_ENTER, '\n'));
        }
    }

    /** 只有 JediTerm 读取输出；保持连接到 EOF，防止快退出程序的尾部输出被跳过。 */
    private final class Connector extends ProcessTtyConnector {
        private final ProgramSession owner;
        private volatile boolean drained;

        Connector(PtyProcess process, ProgramSession owner) {
            super(process, StandardCharsets.UTF_8, List.of(executable.toString()));
            this.owner = owner;
        }

        @Override public String getName() { return "输入输出 · " + executable.getFileName(); }
        @Override public boolean isConnected() { return !closed && !drained; }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            try {
                int count = super.read(buffer, offset, length);
                if (count < 0) drained();
                return count;
            } catch (IOException error) {
                if (!getProcess().isAlive()) {
                    drained();
                    return -1;
                }
                // 读端损坏后不能把仍等待输入的程序留在后台；只停止本项拥有的进程。
                onFx(() -> failure = "读取程序输出失败：" + error.getMessage());
                drained();
                owner.close();
                return -1;
            }
        }

        private void drained() {
            if (drained) return;
            drained = true;
            outputDrained();
        }

        @Override public void write(byte[] bytes) { if (!closed && !finished) owner.write(bytes); }
        @Override public void write(String text) { if (!closed && !finished) owner.write(text); }
        @Override public void resize(TermSize size) { owner.resize(size.getColumns(), size.getRows()); }
        @Override public void close() { owner.close(); }
    }
}
