package craken.ui.run;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import craken.compiler.Diagnostic;
import craken.compiler.SourceFile;
import craken.compiler.link.ExecutableArtifact;
import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import craken.ui.editor.realtime.RealtimeDiagnostic;
import craken.ui.editor.realtime.RealtimeDiagnosticsController;
import craken.ui.frame.AppFrame;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.cases.CasePanel;
import craken.ui.interaction.cases.CaseTabs;
import craken.ui.interaction.inputoutput.InputOutputTab;
import craken.ui.interaction.inputoutput.InputOutputTabs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private final RealtimeDiagnosticsController diagnostics;
    private final Compiler compiler;
    private final InputOutputTabs tabs;
    private final CaseTabs cases;
    private final ExecutorService worker;
    private final ChangeListener<EditorFile> selectionChanged = (observable, old, current) -> updateAvailability();
    private Request active;
    private boolean closed;

    public RunController(EditorArea editors, AppFrame frame, InteractionArea interactions) {
        this(editors, frame, interactions, (RealtimeDiagnosticsController) null);
    }

    /** 编译失败时把诊断同步进文件绑定的 ERR 标签；{@code diagnostics} 可为 null。 */
    public RunController(EditorArea editors, AppFrame frame, InteractionArea interactions,
                         RealtimeDiagnosticsController diagnostics) {
        this(editors, frame, interactions, diagnostics, new RunCompilation()::compile);
    }

    RunController(EditorArea editors, AppFrame frame, InteractionArea interactions, Compiler compiler) {
        this(editors, frame, interactions, null, compiler);
    }

    RunController(EditorArea editors, AppFrame frame, InteractionArea interactions,
                  RealtimeDiagnosticsController diagnostics, Compiler compiler) {
        requireFxThread();
        this.editors = Objects.requireNonNull(editors);
        this.frame = Objects.requireNonNull(frame);
        this.interactions = Objects.requireNonNull(interactions);
        this.diagnostics = diagnostics;
        this.compiler = Objects.requireNonNull(compiler);
        // 与调试共用同一份 IO 项表：三种入口对同一个编辑器组件复用同一个 IO 项。
        tabs = interactions.inputOutputTabs(editors);
        cases = new CaseTabs(interactions);
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "craken-compile-run");
            thread.setDaemon(true);
            return thread;
        });
        editors.activeFileProperty().addListener(selectionChanged);
        frame.setOnRun(this::runActiveFile);
        interactions.setOnNewCase(this::newCaseForActiveFile);
        updateAvailability();
    }

    /** 为当前活动编辑器新建一个用例标签；没有活动编辑器或已经关闭时不做任何事。 */
    public void newCaseForActiveFile() {
        requireFxThread();
        if (closed) return;
        EditorFile file = editors.activeFile();
        if (file == null) return;
        cases.open(file.path());
    }

    public void runActiveFile() {
        requireFxThread();
        if (closed || active != null || editors.activeFile() == null) return;
        frame.collapseDisplayArea();
        EditorFile selected = editors.activeFile();
        // 必须在点击这次命令时固定文本与路径；后台不得重新查询 activeFile 或读磁盘源码。
        SourceFile source = new SourceFile(selected.path().toString(), selected.editor().text());
        // 同一个编辑器组件复用同一个 IO 项：标题、编号与列表位置不变，只替换本次运行的通道。
        InputOutputTabs.Entry entry = tabs.open(selected);
        // 用例输入同样在点击时快照；本轮运行会并行执行这些仍打开、绑定到同一源文件的用例。
        Request request = new Request(source, selected, beginCases(selected));
        request.panel = new RunPanel(selected.path(), () -> cancel(request),
                () -> interactions.closeItem(entry.item()));
        active = request;
        updateAvailability();
        try {
            entry.tab().show(new InputOutputTab.Channel(request.panel,
                    request.panel::start, request.panel::activate, request.panel::close));
            interactions.select(entry.item());
            Path outputRoot = interactions.projectRoot().resolve("build").resolve("craken-runs");
            request.future = worker.submit(() -> compile(request, source, outputRoot));
        } catch (RuntimeException failure) {
            finish(request, null, failure);
        }
    }

    /** 结束每个用例的上一轮进程、清空输出并让它们等待本次编译；返回本轮运行代号。 */
    private List<CaseRequest> beginCases(EditorFile file) {
        List<CaseRequest> requests = new ArrayList<>();
        for (CaseTabs.Entry entry : cases.entries(file.path())) {
            CasePanel panel = entry.item().content();
            int generation = panel.beginRun();
            requests.add(new CaseRequest(panel, generation, panel.inputText()));
        }
        return requests;
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
            request.cases.forEach(run -> run.panel().notRun(run.generation(), "未运行（编译失败）"));
        } else if (result == null) {
            request.panel.failed("编译失败", "编译器未返回结果，未启动程序。");
            request.cases.forEach(run -> run.panel().notRun(run.generation(), "未运行（编译失败）"));
        } else if (!result.succeeded()) {
            request.panel.failed(result.failedStage(), result.diagnostics());
            publishDiagnostics(request, result.diagnostics());
            request.cases.forEach(run -> run.panel().notRun(run.generation(), "未运行（编译失败）"));
        } else {
            try {
                request.panel.compiled(result.artifact());
                request.startCases(result.artifact());
            } catch (RuntimeException | LinkageError problem) {
                request.panel.failed("启动失败", Objects.toString(problem.getMessage(), problem.getClass().getSimpleName()));
                request.cases.forEach(run -> run.panel().notRun(run.generation(), "未运行（启动失败）"));
            }
        }
    }

    private void publishDiagnostics(Request request, List<Diagnostic> compileDiagnostics) {
        if (diagnostics == null || request.file == null || !editors.files().contains(request.file)) return;
        diagnostics.publish(request.file, compileDiagnostics.stream().map(RealtimeDiagnostic::from).toList());
    }

    private void cancel(Request request) {
        requireFxThread();
        if (active == request) {
            request.cancelled.set(true);
            if (request.future != null) request.future.cancel(true);
            active = null;
            request.panel.cancelled();
            request.cases.forEach(run -> run.panel().cancelRun(run.generation()));
            updateAvailability();
            return;
        }
        // 运行结束后关闭该次运行的 IO 项：结束仍属于这一轮的用例进程，已完成的结果保留。
        request.cases.forEach(run -> run.panel().cancelRun(run.generation()));
    }

    private void updateAvailability() {
        frame.setRunDisabled(closed || active != null || editors.activeFile() == null);
        interactions.setNewCaseDisabled(closed || editors.activeFile() == null);
    }

    @Override
    public void close() {
        requireFxThread();
        if (closed) return;
        closed = true;
        if (active != null) cancel(active);
        editors.activeFileProperty().removeListener(selectionChanged);
        cases.close();
        interactions.setOnNewCase(null);
        frame.setOnRun(null);
        worker.shutdownNow();
        updateAvailability();
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("run commands must run on the JavaFX thread");
    }

    private static final class Request {
        private final SourceFile source;
        private final EditorFile file;
        private final List<CaseRequest> cases;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private RunPanel panel;
        private Future<?> future;

        private Request(SourceFile source, EditorFile file, List<CaseRequest> cases) {
            this.source = source;
            this.file = file;
            this.cases = List.copyOf(cases);
        }

        /** 编译成功后启动本轮的所有用例：与前台 IO 并行，共用同一个可执行产物。 */
        private void startCases(ExecutableArtifact artifact) {
            Path workingDirectory = file.path().toAbsolutePath().getParent();
            for (CaseRequest run : cases) {
                run.panel().run(run.generation(), workingDirectory, artifact.path(), run.input());
            }
        }
    }

    /** 一次运行中的单个用例：面板、运行代号与点击时固定的输入快照。 */
    private record CaseRequest(CasePanel panel, int generation, String input) { }
}
