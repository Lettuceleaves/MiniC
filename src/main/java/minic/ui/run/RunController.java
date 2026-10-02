package minic.ui.run;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import minic.compiler.SourceFile;
import minic.ui.editor.EditorArea;
import minic.ui.editor.EditorFile;
import minic.ui.frame.AppFrame;
import minic.ui.interaction.InteractionArea;
import minic.ui.interaction.InteractionItem;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 将运行命令接到选中标签的源码快照；编译不占用 FX/EDT，也不会自动保存文件。 */
public final class RunController implements AutoCloseable {
    @FunctionalInterface
    interface Compiler {
        RunCompilation.Outcome compile(SourceFile source, Path outputRoot,
                                       Consumer<RunCompilation.Progress> onProgress) throws Exception;
    }

    private final EditorArea editors;
    private final AppFrame frame;
    private final InteractionArea interactions;
    private final Compiler compiler;
    private final ExecutorService worker;
    private final ChangeListener<EditorFile> selectionChanged = (observable, old, current) -> updateAvailability();
    private Request active;
    private boolean closed;

    public RunController(EditorArea editors, AppFrame frame, InteractionArea interactions) {
        this(editors, frame, interactions, new RunCompilation()::compile);
    }

    RunController(EditorArea editors, AppFrame frame, InteractionArea interactions, Compiler compiler) {
        requireFxThread();
        this.editors = Objects.requireNonNull(editors);
        this.frame = Objects.requireNonNull(frame);
        this.interactions = Objects.requireNonNull(interactions);
        this.compiler = Objects.requireNonNull(compiler);
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "minic-compile-run");
            thread.setDaemon(true);
            return thread;
        });
        editors.activeFileProperty().addListener(selectionChanged);
        frame.setOnRun(this::runActiveFile);
        updateAvailability();
    }

    public void runActiveFile() {
        requireFxThread();
        if (closed || active != null || editors.activeFile() == null) return;
        frame.collapseDisplayArea();
        EditorFile selected = editors.activeFile();
        // 必须在点击这次命令时固定文本与路径；后台不得重新查询 activeFile 或读磁盘源码。
        SourceFile source = new SourceFile(selected.path().toString(), selected.editor().text());
        Request request = new Request();
        request.panel = new RunPanel(selected.path(), () -> cancel(request),
                () -> interactions.closeItem(request.item));
        active = request;
        updateAvailability();
        try {
            request.item = new InteractionItem<>(interactions.nextInputOutputTitle(),
                    request.panel, request.panel::start, request.panel::activate, request.panel::close);
            interactions.addItem(request.item);
            Path outputRoot = interactions.projectRoot().resolve("build").resolve("minic-runs");
            request.future = worker.submit(() -> compile(request, source, outputRoot));
        } catch (RuntimeException failure) {
            finish(request, null, failure);
        }
    }

    private void compile(Request request, SourceFile source, Path outputRoot) {
        RunCompilation.Outcome result = null;
        Throwable failure = null;
        try {
            if (request.cancelled.get()) return;
            result = compiler.compile(source, outputRoot, progress -> Platform.runLater(() -> {
                // 进度属于这次请求；取消、关闭或新建运行后，迟到回调不得改写当前视图。
                if (!closed && active == request && !request.cancelled.get() && !request.panel.isClosed()) {
                    request.panel.progress(progress);
                }
            }));
        } catch (Exception | LinkageError problem) {
            if (problem instanceof InterruptedException) Thread.currentThread().interrupt();
            failure = problem;
        }
        RunCompilation.Outcome outcome = result;
        Throwable error = failure;
        Platform.runLater(() -> finish(request, outcome, error));
    }

    private void finish(Request request, RunCompilation.Outcome result, Throwable failure) {
        if (active == request) active = null;
        updateAvailability();
        if (closed || request.cancelled.get() || request.panel.isClosed()) return;
        if (failure != null) {
            request.panel.failed("编译或启动失败", Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()));
        } else if (result == null) {
            request.panel.failed("编译失败", "编译器未返回结果，未启动程序。");
        } else if (!result.succeeded()) {
            request.panel.failed(result.failedStage(), result.diagnostics());
        } else {
            try {
                request.panel.compiled(result.artifact());
            } catch (RuntimeException | LinkageError problem) {
                request.panel.failed("启动失败", Objects.toString(problem.getMessage(), problem.getClass().getSimpleName()));
            }
        }
    }

    private void cancel(Request request) {
        requireFxThread();
        if (active != request) return;
        request.cancelled.set(true);
        if (request.future != null) request.future.cancel(true);
        active = null;
        request.panel.cancelled();
        updateAvailability();
    }

    private void updateAvailability() {
        frame.setRunDisabled(closed || active != null || editors.activeFile() == null);
    }

    @Override
    public void close() {
        requireFxThread();
        if (closed) return;
        closed = true;
        if (active != null) cancel(active);
        editors.activeFileProperty().removeListener(selectionChanged);
        frame.setOnRun(null);
        worker.shutdownNow();
        updateAvailability();
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("run commands must run on the JavaFX thread");
    }

    private static final class Request {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private RunPanel panel;
        private InteractionItem<RunPanel> item;
        private Future<?> future;
    }
}
