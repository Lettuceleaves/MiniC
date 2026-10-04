package craken.ui.pipeline;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import craken.compiler.SourceFile;
import craken.ui.display.DisplayArea;
import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import craken.ui.frame.AppFrame;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** 编译会话只在后台推进；FX 线程消费快照并管理展示台。 */
public final class PipelineController implements AutoCloseable {
    private final EditorArea editors;
    private final AppFrame frame;
    private final DisplayArea display;
    private final Path outputRoot;
    private final PipelinePanel panel = new PipelinePanel();
    private final ChangeListener<EditorFile> selectionChanged = (observable, old, current) -> updateAvailability();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "craken-compile-pipeline");
        thread.setDaemon(true);
        return thread;
    });
    private Request active;
    private boolean busy;
    private boolean closed;

    public PipelineController(EditorArea editors, AppFrame frame, DisplayArea display, Path projectRoot) {
        requireFxThread();
        this.editors = Objects.requireNonNull(editors);
        this.frame = Objects.requireNonNull(frame);
        this.display = Objects.requireNonNull(display);
        outputRoot = Objects.requireNonNull(projectRoot).resolve("build").resolve("craken-pipelines");
        editors.activeFileProperty().addListener(selectionChanged);
        frame.setOnPipeline(this::open);
        panel.setOnNextStep(() -> advance(false));
        panel.setOnNextStage(() -> advance(true));
        panel.setOnSelectStage(this::selectStage);
        updateAvailability();
    }

    /** 重复打开相同源码保留进度；更换文件或编辑内容后建立新会话。 */
    public void open() {
        requireFxThread();
        if (closed) return;
        EditorFile selected = editors.activeFile();
        if (selected == null) return;
        display.show(panel);
        frame.showPipelineDisplayArea();
        SourceFile source = new SourceFile(selected.path().toString(), selected.editor().text());
        if (active != null && active.source.equals(source)) return;
        Request previous = active;
        if (previous != null && previous.future != null) previous.future.cancel(true);
        if (previous != null) worker.submit(() -> { if (previous.session != null) previous.session.close(); });
        Request request = new Request(source);
        active = request;
        busy = true;
        panel.preparing(selected.path().getFileName().toString());
        request.future = worker.submit(() -> {
            try {
                if (Thread.currentThread().isInterrupted()) return;
                request.session = PipelineSession.create(source, outputRoot);
                publish(request, request.session.snapshot(), null);
            } catch (Exception | LinkageError failure) {
                publish(request, null, failure);
            }
        });
    }

    private void advance(boolean entireStage) {
        requireFxThread();
        Request request = active;
        if (closed || busy || request == null || request.session == null
                || !request.session.snapshot().canAdvance()) return;
        busy = true;
        panel.setBusy(true);
        request.future = worker.submit(() -> {
            try {
                if (entireStage) request.session.nextStage(() -> Thread.currentThread().isInterrupted());
                else request.session.nextStep(() -> Thread.currentThread().isInterrupted());
                publish(request, request.session.snapshot(), null);
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
            } catch (Exception | LinkageError failure) {
                publish(request, request.session.snapshot(), failure);
            }
        });
    }

    private void selectStage(int index) {
        requireFxThread();
        Request request = active;
        if (closed || busy || request == null || request.session == null) return;
        busy = true;
        request.future = worker.submit(() -> {
            request.session.selectStage(index);
            publish(request, request.session.snapshot(), null);
        });
    }

    private void publish(Request request, PipelineSession.Snapshot snapshot, Throwable failure) {
        Platform.runLater(() -> {
            if (closed || active != request) return;
            busy = false;
            if (snapshot != null) panel.show(snapshot);
            if (failure != null) {
                String message = failure.getMessage();
                panel.failed(message == null || message.isBlank() ? failure.getClass().getSimpleName() : message);
                // 准备会话失败时允许再次点击图标重试。
                if (request.session == null) active = null;
            }
        });
    }

    private void updateAvailability() {
        frame.setPipelineDisabled(closed || editors.activeFile() == null);
    }

    @Override
    public void close() {
        requireFxThread();
        if (closed) return;
        closed = true;
        if (active != null && active.future != null) active.future.cancel(true);
        Request previous = active;
        active = null;
        editors.activeFileProperty().removeListener(selectionChanged);
        frame.setOnPipeline(null);
        panel.setBusy(true);
        panel.close();
        if (previous != null) worker.submit(() -> { if (previous.session != null) previous.session.close(); });
        worker.shutdown();
        updateAvailability();
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("pipeline commands must run on the JavaFX thread");
    }

    private static final class Request {
        private final SourceFile source;
        private volatile PipelineSession session;
        private Future<?> future;

        private Request(SourceFile source) {
            this.source = source;
        }
    }
}
