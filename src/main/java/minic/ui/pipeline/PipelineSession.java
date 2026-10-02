package minic.ui.pipeline;

import minic.compiler.CompilerApi;
import minic.compiler.Diagnostic;
import minic.compiler.SourceFile;
import minic.compiler.Stage;
import minic.compiler.asm.Assembler;
import minic.compiler.execute.ExecutableRunner;
import minic.compiler.ir.IrLowerer;
import minic.compiler.lexer.Lexer;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;

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
public final class PipelineSession {
    private static final List<String> STAGE_LABELS = List.of(
            "预处理", "词法分析", "语法分析", "语义分析", "生成 IR", "生成汇编", "生成目标文件", "链接");

    private final CompilerApi compiler;
    private final List<Stage> stages;
    private int selectedStageIndex;
    private String executionFailure = "";
    private volatile Snapshot snapshot;

    /** 独立产物目录保护已有编译产物；源码来自编辑缓冲区，不保存或重新读取源文件。 */
    public static PipelineSession create(SourceFile source, Path outputRoot) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outputRoot, "outputRoot");
        Path root = Files.createDirectories(outputRoot.toAbsolutePath().normalize());
        Path outputDirectory = Files.createTempDirectory(root, "pipeline-");
        return new PipelineSession(new CompilerApi(source, outputDirectory));
    }

    public PipelineSession(CompilerApi compiler) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        stages = compiler.stages().stream().takeWhile(stage -> !(stage instanceof ExecutableRunner)).toList();
        // 输入输出视图尚未启用，仅保留完成阶段的最终上下文，避免为每一步复制整个编译状态。
        for (Stage stage : stages) compiler.setResultRecording(stage, false);
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
            compiler.step();
        } catch (RuntimeException | LinkageError failure) {
            executionFailure = describeFailure(failure);
            throw failure;
        } finally {
            followCurrentStage();
        }
    }

    /** 完成当前阶段，保留下一阶段入口；每一步之前均可取消。 */
    public synchronized void nextStage(BooleanSupplier cancelled) throws InterruptedException {
        Objects.requireNonNull(cancelled, "cancelled");
        if (!snapshot.canAdvance()) return;
        try {
            compiler.nextStage(cancelled);
        } catch (RuntimeException | LinkageError failure) {
            executionFailure = describeFailure(failure);
            throw failure;
        } finally {
            followCurrentStage();
        }
    }

    /** 回看已完成或当前阶段不会修改编译器的推进位置。 */
    public synchronized boolean selectStage(int index) {
        if (index < 0 || index >= stages.size() || !snapshot.stages().get(index).selectable()) return false;
        selectedStageIndex = index;
        refresh();
        return true;
    }

    private void followCurrentStage() {
        selectedStageIndex = stages.isEmpty() ? -1 : Math.min(compiler.currentStageIndex(), stages.size() - 1);
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
                !succeeded && !failed && compiler.canNext(), succeeded, failed);
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
                           long stepCount, boolean canAdvance, boolean succeeded, boolean failed) {
        public Snapshot {
            stages = List.copyOf(stages);
        }
    }
}
