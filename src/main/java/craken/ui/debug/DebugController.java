package craken.ui.debug;

import craken.compiler.SourceFile;
import craken.debug.DebugVariable;
import craken.ui.component.editor.UiCodeEditor;
import craken.ui.component.editor.UiCodeEditorBreakpoint;
import craken.ui.display.DisplayArea;
import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import craken.ui.frame.AppFrame;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.inputoutput.InputOutputTab;
import craken.ui.interaction.inputoutput.InputOutputTabs;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 调试工作台控制器：调试会话在单后台线程推进，FX 线程只消费不可变快照。
 *
 * <p>打开时进入半屏布局并准备 IR 变量检索；“开始”把捕获列表写进插桩计划后运行；
 * 步进、步出、断点导航与历史回看都走同一命令入口。每次“开始”复用同一个编辑器组件的
 * IO 项：标准输出与标准错误追加到该项，输入行排队给下一次读取。</p>
 */
public final class DebugController implements AutoCloseable {
    private final EditorArea editors;
    private final AppFrame frame;
    private final DisplayArea display;
    private final InteractionArea interactions;
    private final InputOutputTabs tabs;
    private final DebugPanel panel = new DebugPanel();
    private final ChangeListener<EditorFile> selectionChanged = (observable, old, current) -> updateAvailability();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "craken-debug");
        thread.setDaemon(true);
        return thread;
    });
    private Request active;
    private boolean busy;
    private boolean closed;

    public DebugController(EditorArea editors, AppFrame frame, DisplayArea display, InteractionArea interactions) {
        requireFxThread();
        this.editors = Objects.requireNonNull(editors);
        this.frame = Objects.requireNonNull(frame);
        this.display = Objects.requireNonNull(display);
        this.interactions = Objects.requireNonNull(interactions);
        // 与运行共用同一份 IO 项表：同一编辑器组件重复运行/调试都复用同一个 IO 项。
        tabs = interactions.inputOutputTabs(editors);
        editors.activeFileProperty().addListener(selectionChanged);
        frame.setOnDebug(this::open);
        panel.setOnStart(this::startSelected);
        panel.setOnRestart(this::restart);
        panel.setOnNext(() -> command(DebugWorkbenchSession::stepOver));
        panel.setOnPrevious(() -> command(DebugWorkbenchSession::previous));
        panel.setOnStepInto(() -> command(DebugWorkbenchSession::stepInto));
        panel.setOnStepOut(() -> command(DebugWorkbenchSession::stepOut));
        panel.setOnRunToEnd(() -> command(DebugWorkbenchSession::runToEnd));
        panel.setOnNextBreakpoint(() -> command(DebugWorkbenchSession::nextBreakpoint));
        panel.setOnPreviousBreakpoint(() -> command(DebugWorkbenchSession::previousBreakpoint));
        updateAvailability();
    }

    /** 打开半屏调试工作台；相同源码与断点保留已准备的检索会话。 */
    public void open() {
        requireFxThread();
        if (closed) return;
        EditorFile selected = editors.focusedFile();
        if (selected == null) return;
        display.show(panel);
        frame.showDebugDisplayArea();
        SourceFile source = sourceOf(selected);
        Set<Integer> breakpoints = breakpointsOf(selected);
        if (active != null && active.source.equals(source) && active.breakpoints.equals(breakpoints)) return;
        prepare(selected, source, breakpoints, selected.editor());
    }

    private void startSelected() {
        requireFxThread();
        Request request = active;
        if (closed || busy || request == null || request.session == null) return;
        List<DebugVariable> selection = panel.selectedVariables();
        beginInputOutput(request);
        busy = true;
        panel.setBusy(true);
        request.future = worker.submit(() -> {
            try {
                publish(request, request.session.start(selection), null);
            } catch (Exception | LinkageError failure) {
                publish(request, null, failure);
            }
        });
    }

    /** 重启：始终用当前编辑缓冲区与当前捕获列表重新编译并插桩。 */
    public void restart() {
        requireFxThread();
        if (closed) return;
        EditorFile selected = editors.focusedFile();
        if (selected == null) return;
        List<String> keys = panel.selectedVariables().stream()
                .map(variable -> variable.function() + ":" + variable.definition().startLine() + ":" + variable.sourceName())
                .toList();
        prepare(selected, sourceOf(selected), breakpointsOf(selected), selected.editor(), keys);
    }

    private void prepare(EditorFile file, SourceFile source, Set<Integer> breakpoints, UiCodeEditor editor) {
        prepare(file, source, breakpoints, editor, List.of());
    }

    /** 准备会话并回到捕获阶段；selectionKeys 是上一轮选择的（函数:行:名字）快照。 */
    private void prepare(EditorFile file, SourceFile source, Set<Integer> breakpoints, UiCodeEditor editor,
                         List<String> selectionKeys) {
        cancelActive();
        Request request = new Request(file, source, breakpoints, editor);
        active = request;
        busy = true;
        panel.showCapturePhase();
        panel.setBusy(true);
        request.future = worker.submit(() -> {
            try {
                var session = new DebugWorkbenchSession(source, breakpoints);
                Platform.runLater(() -> {
                    if (closed || active != request) { session.close(); return; }
                    request.session = session;
                    panel.setCaptureSearch(session::search);
                    panel.setCaptureTypeText(session::typeText);
                    if (!selectionKeys.isEmpty()) {
                        var resolved = new ArrayList<DebugVariable>();
                        for (String key : selectionKeys) {
                            String[] parts = key.split(":", 3);
                            if (parts.length != 3) continue;
                            session.search(parts[2]).stream()
                                    .filter(variable -> variable.function().equals(parts[0])
                                            && variable.definition().startLine() == Integer.parseInt(parts[1]))
                                    .findFirst().ifPresent(resolved::add);
                        }
                        panel.replaceSelection(resolved);
                    }
                    busy = false;
                    panel.setBusy(false);
                });
            } catch (Exception | LinkageError failure) {
                publish(request, null, failure);
            }
        });
    }

    private void command(Function<DebugWorkbenchSession, DebugWorkbenchSession.Snapshot> action) {
        requireFxThread();
        Request request = active;
        if (closed || busy || request == null || request.session == null) return;
        busy = true;
        panel.setBusy(true);
        request.future = worker.submit(() -> {
            try {
                publish(request, action.apply(request.session), null);
            } catch (Exception | LinkageError failure) {
                publish(request, request.session.snapshot(), failure);
            }
        });
    }

    /** 本次“开始”的 IO 项：复用编辑器组件已有的项，清空上一次的输出并重新开放输入。 */
    private void beginInputOutput(Request request) {
        if (!editors.files().contains(request.file)) return;
        InputOutputTabs.Entry entry = tabs.open(request.file);
        DebugIoPanel console = new DebugIoPanel(request.file.path());
        console.setOnInput(line -> appendStandardInput(request, line));
        console.begin();
        request.console = console;
        request.streamedStdout = "";
        request.streamedStderr = "";
        entry.tab().show(new InputOutputTab.Channel(console,
                console::start, console::activate, console::close));
        interactions.select(entry.item());
    }

    /** 输入行只在当前请求仍是活动会话时排队，关闭或替换后的迟到输入不会进入新会话。 */
    private void appendStandardInput(Request request, String line) {
        requireFxThread();
        DebugWorkbenchSession session = request.session;
        if (closed || active != request || session == null) return;
        session.appendStandardInput(line + "\n");
    }

    /** 把停止点新增的标准输出/标准错误追加到 IO 项；历史回看不重复、不回滚已打印内容。 */
    private void stream(Request request, DebugWorkbenchSession.Snapshot snapshot) {
        DebugIoPanel console = request.console;
        if (console == null || console.isClosed()) return;
        String stdout = Objects.requireNonNullElse(snapshot.stdout(), "");
        String stdoutDelta = extension(stdout, request.streamedStdout);
        if (stdoutDelta != null) {
            console.appendStdout(stdoutDelta);
            request.streamedStdout = stdout;
        }
        String stderr = Objects.requireNonNullElse(snapshot.stderr(), "");
        String stderrDelta = extension(stderr, request.streamedStderr);
        if (stderrDelta != null) {
            console.appendStderr(stderrDelta);
            request.streamedStderr = stderr;
        }
    }

    /** 新文本是已显示内容的后继时返回增量；回看历史导致的截短与重复都返回 null。 */
    private static String extension(String full, String streamed) {
        if (full.length() <= streamed.length()) return null;
        return full.startsWith(streamed) ? full.substring(streamed.length()) : full;
    }

    /** 结束态关闭 IO 项的输入入口；输出保留供回看与复制。 */
    private void finishInputOutput(Request request, DebugWorkbenchSession.Snapshot snapshot) {
        DebugIoPanel console = request.console;
        if (console == null || console.isClosed() || console.isFinished()) return;
        if ("COMPLETED".equals(snapshot.status())) {
            console.finish("程序已结束");
        } else if ("FAILED".equals(snapshot.status())) {
            console.finish(snapshot.diagnostic().isBlank() ? "程序异常终止" : "程序异常终止：" + snapshot.diagnostic());
        }
    }

    private void publish(Request request, DebugWorkbenchSession.Snapshot snapshot, Throwable failure) {
        Platform.runLater(() -> {
            if (closed || active != request) return;
            busy = false;
            if (snapshot != null) {
                panel.show(snapshot);
                stream(request, snapshot);
                finishInputOutput(request, snapshot);
            }
            if (failure != null) {
                String message = message(failure);
                panel.failed(message);
                if (request.console != null) request.console.failure(message);
            }
            updateLineHighlight(request, snapshot);
        });
    }

    /** 被调试编辑器同步当前行高亮；READY/COMPLETED 或无行号时清除。 */
    private void updateLineHighlight(Request request, DebugWorkbenchSession.Snapshot snapshot) {
        if (snapshot != null && snapshot.line() > 0 && !"COMPLETED".equals(snapshot.status())) {
            request.editor.highlightLine(snapshot.line());
        } else {
            request.editor.clearRangeHighlights();
        }
    }

    private void cancelActive() {
        Request previous = active;
        active = null;
        if (previous == null) return;
        if (previous.console != null) previous.console.finish("调试已停止");
        previous.editor.clearRangeHighlights();
        if (previous.future != null) previous.future.cancel(true);
        worker.submit(() -> { if (previous.session != null) previous.session.close(); });
    }

    private void updateAvailability() {
        frame.setDebugDisabled(closed || editors.focusedFile() == null);
    }

    @Override public void close() {
        requireFxThread();
        if (closed) return;
        closed = true;
        Request previous = active;
        active = null;
        if (previous != null) {
            previous.editor.clearRangeHighlights();
            if (previous.console != null) previous.console.finish("调试已关闭");
        }
        if (previous != null && previous.future != null) previous.future.cancel(true);
        editors.activeFileProperty().removeListener(selectionChanged);
        frame.setOnDebug(null);
        panel.setBusy(true);
        try { panel.close(); }
        finally {
            try { if (previous != null) worker.submit(() -> { if (previous.session != null) previous.session.close(); }); }
            finally { worker.shutdown(); }
            updateAvailability();
        }
    }

    private static SourceFile sourceOf(EditorFile file) {
        return new SourceFile(file.path().toString(), file.editor().text());
    }

    private static Set<Integer> breakpointsOf(EditorFile file) {
        return file.editor().breakpoints().stream()
                .map(UiCodeEditorBreakpoint::line)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("debug commands must run on the JavaFX thread");
    }

    private static final class Request {
        private final EditorFile file;
        private final SourceFile source;
        private final Set<Integer> breakpoints;
        private final UiCodeEditor editor;
        private volatile DebugWorkbenchSession session;
        private volatile DebugIoPanel console;
        private String streamedStdout = "";
        private String streamedStderr = "";
        private Future<?> future;

        private Request(EditorFile file, SourceFile source, Set<Integer> breakpoints, UiCodeEditor editor) {
            this.file = Objects.requireNonNull(file, "file");
            this.source = source;
            this.breakpoints = Set.copyOf(breakpoints);
            this.editor = Objects.requireNonNull(editor, "editor");
        }
    }
}

