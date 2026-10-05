package craken.ui.pipeline;

import craken.compiler.CompilerApi;
import craken.compiler.Diagnostic;
import craken.compiler.SourceFile;
import craken.compiler.Stage;
import craken.compiler.asm.Assembler;
import craken.compiler.execute.ExecutableRunner;
import craken.compiler.ir.IrLowerer;
import craken.compiler.lexer.Lexer;
import craken.compiler.link.Linker;
import craken.compiler.obj.ObjBuilder;
import craken.compiler.parser.Parser;
import craken.compiler.preprocess.Preprocessor;
import craken.compiler.semantic.SemanticAnalyzer;
import craken.visualization.adapter.pipeline.PipelineStepObservation;
import craken.visualization.adapter.pipeline.PipelineVisualizationFrame;
import craken.visualization.adapter.pipeline.PipelineVisualizationSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * 一份源码快照的编译展示会话。编译止于链接，不执行生成的程序。
 * 推进方法应在后台线程调用；不可变 snapshot 可直接交给 FX 线程。
 */
public final class PipelineSession implements AutoCloseable {
    private static final List<String> STAGE_LABELS = List.of(
            "预处理", "词法分析", "语法分析", "语义分析", "生成 IR", "生成汇编", "生成目标文件", "链接");

    private final CompilerApi compiler;
    private final List<Stage> stages;
    private final PipelineVisualizationSession visualization;
    private PipelineStepObservation pending;
    private String visualizationFailure = "";
    private int selectedStageIndex;
    private String executionFailure = "";
    private volatile Snapshot snapshot;
    private boolean closed;

    /** 独立产物目录保护已有编译产物；源码来自编辑缓冲区，不保存或重新读取源文件。 */
    public static PipelineSession create(SourceFile source, Path outputRoot) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outputRoot, "outputRoot");
        Path root = Files.createDirectories(outputRoot.toAbsolutePath().normalize());
        Path outputDirectory = Files.createTempDirectory(root, "pipeline-");
        return new PipelineSession(new CompilerApi(source, outputDirectory));
    }

    public PipelineSession(CompilerApi compiler) {
        this(compiler, new PipelineVisualizationSession());
    }

    public PipelineSession(CompilerApi compiler, PipelineVisualizationSession visualization) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.visualization = Objects.requireNonNull(visualization, "visualization");
        stages = compiler.stages().stream().takeWhile(stage -> !(stage instanceof ExecutableRunner)).toList();
        for (Stage stage : stages) {
            compiler.setResultRecording(stage, false);
            compiler.setCaptureLatestContext(stage, true);
        }
        SourceFile source = stages.stream().filter(Preprocessor.class::isInstance).map(Preprocessor.class::cast)
                .map(Preprocessor::sourceFile).findFirst().orElse(null);
        visualization.initialize(source);
        selectedStageIndex = stages.isEmpty() ? -1 : Math.min(compiler.currentStageIndex(), stages.size() - 1);
        refresh();
    }

    public static List<String> stageLabels() {
        return STAGE_LABELS;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    /** 只执行当前阶段的一个真实编译步骤，结束时自动进入下一阶段。 */
    public synchronized void nextStep(BooleanSupplier cancelled) throws InterruptedException {
        Objects.requireNonNull(cancelled, "cancelled");
        if (!snapshot.canAdvance()) return;
        if (cancelled.getAsBoolean()) throw new InterruptedException("compilation cancelled");
        try {
            if (pending != null) retryProjection();
            else advanceAndProject();
        } finally {
            followCurrentStage();
        }
    }

    /** 完成当前阶段，保留下一阶段入口；每一步之前均可取消。 */
    public synchronized void nextStage(BooleanSupplier cancelled) throws InterruptedException {
        Objects.requireNonNull(cancelled, "cancelled");
        if (!snapshot.canAdvance()) return;
        try {
            if (pending != null) {
                if (cancelled.getAsBoolean()) throw new InterruptedException("compilation cancelled");
                retryProjection();
                return;
            }
            int target = compiler.currentStageIndex();
            // Bulk stage runs merge intermediate frames, so growing contexts are not copied and
            // projection happens only at the terminal. A failed terminal projection keeps its
            // captured observation pending for retry without re-running compilation steps.
            setPerStepCapture(false);
            try {
            while (compiler.canNext() && compiler.currentStageIndex() == target && target < stages.size()) {
                if (cancelled.getAsBoolean()) throw new InterruptedException("compilation cancelled");
                    advanceAndProject(true);
                if (pending != null) break;
            }
            } finally {
                setPerStepCapture(true);
            }
        } finally {
            followCurrentStage();
        }
    }

    private void advanceAndProject() {
        advanceAndProject(false);
    }

    private void advanceAndProject(boolean terminalOnly) {
        Stage executed = compiler.currentStage().orElseThrow();
        int index = compiler.currentStageIndex();
        Stage.Result result;
        try {
            result = compiler.stepResult();
        } catch (RuntimeException | LinkageError failure) {
            executionFailure = describeFailure(failure);
            throw failure;
        }
        if (terminalOnly && !result.lastStep()) return;
        pending = PipelineStepObservation.capture(executed, index, result);
        retryProjection();
    }

    private void setPerStepCapture(boolean enabled) {
        for (Stage stage : stages) compiler.setCaptureLatestContext(stage, enabled);
    }

    private void retryProjection() {
        try {
            visualization.project(pending);
            pending = null;
            visualizationFailure = "";
        } catch (RuntimeException | LinkageError failure) {
            visualizationFailure = describeFailure(failure);
        }
    }

    /** 回看已完成或当前阶段不会修改编译器的推进位置。 */
    public synchronized boolean selectStage(int index) {
        if (closed || index < 0 || index >= stages.size() || !snapshot.stages().get(index).selectable()) return false;
        selectedStageIndex = index;
        refresh();
        return true;
    }

    private void followCurrentStage() {
        selectedStageIndex = stages.isEmpty() ? -1 : pending != null ? pending.stageIndex() : Math.min(compiler.currentStageIndex(), stages.size() - 1);
        refresh();
    }

    private static String describeFailure(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private void refresh() {
        int current = compiler.currentStageIndex();
        boolean succeeded = current >= stages.size();
        boolean failed = !succeeded && (!compiler.canNext() || !executionFailure.isEmpty());
        List<StageView> views = new ArrayList<>();
        for (int index = 0; index < stages.size(); index++) {
            Stage stage = stages.get(index);
            Status status = index < current ? Status.COMPLETED
                    : index == current ? (failed ? Status.FAILED : Status.CURRENT) : Status.PENDING;
            String error = stage.errors().stream().map(Diagnostic::describe).collect(Collectors.joining("\n\n"));
            if (index == current && !executionFailure.isEmpty()) {
                error = error.isEmpty() ? executionFailure : error + "\n\n" + executionFailure;
            }
            views.add(new StageView(index, label(stage), status, status != Status.PENDING, error));
        }
        snapshot = new Snapshot(views, selectedStageIndex, current, compiler.stepCount(),
                pending != null || !succeeded && !failed && compiler.canNext(), succeeded, failed,
                visualization.frameFor(selectedStageIndex), pending != null, visualizationFailure);
    }

    private static String label(Stage stage) {
        if (stage instanceof Preprocessor) return STAGE_LABELS.get(0);
        if (stage instanceof Lexer) return STAGE_LABELS.get(1);
        if (stage instanceof Parser) return STAGE_LABELS.get(2);
        if (stage instanceof SemanticAnalyzer) return STAGE_LABELS.get(3);
        if (stage instanceof IrLowerer) return STAGE_LABELS.get(4);
        if (stage instanceof Assembler) return STAGE_LABELS.get(5);
        if (stage instanceof ObjBuilder) return STAGE_LABELS.get(6);
        if (stage instanceof Linker) return STAGE_LABELS.get(7);
        return stage.getClass().getSimpleName();
    }

    public enum Status { PENDING, CURRENT, COMPLETED, FAILED }

    public record StageView(int index, String label, Status status, boolean selectable, String error) { }

    public record Snapshot(List<StageView> stages, int selectedStageIndex, int currentStageIndex,
                           long stepCount, boolean canAdvance, boolean succeeded, boolean failed,
                           PipelineVisualizationFrame visualization, boolean visualizationPending, String visualizationError) {
        public Snapshot(List<StageView> stages, int selectedStageIndex, int currentStageIndex,
                long stepCount, boolean canAdvance, boolean succeeded, boolean failed) {
            this(stages, selectedStageIndex, currentStageIndex, stepCount, canAdvance, succeeded, failed, null, false, "");
        }
        public Snapshot {
            stages = List.copyOf(stages);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        pending = null;
        try { visualization.close(); }
        finally {
            var previous = snapshot;
            snapshot = new Snapshot(previous.stages(), previous.selectedStageIndex(), previous.currentStageIndex(),
                    previous.stepCount(), false, previous.succeeded(), previous.failed(), previous.visualization(), false,
                    previous.visualizationError());
        }
    }
}
