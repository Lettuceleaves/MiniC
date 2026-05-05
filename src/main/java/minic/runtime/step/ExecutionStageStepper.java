package minic.runtime.step;

import minic.compiler.toolchain.ExecutableArtifact;
import minic.runtime.execution.ExecutableRunService;
import minic.runtime.execution.ExecutableRunner;
import minic.runtime.execution.ExecutionResult;
import minic.source.SourceFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 可执行文件运行阶段适配器。
 */
public final class ExecutionStageStepper implements StageStepper {
    private final SourceFile sourceFile;
    private final ExecutableArtifact artifact;
    private final ExecutableRunService runner;
    private String standardInput = "";
    private boolean inputConfirmed;
    private ExecutionResult result = ExecutionResult.notRun();
    private StepResult lastResult;
    private boolean completed;

    /**
     * 使用默认运行器创建运行阶段。
     *
     * @param sourceFile 源码文件
     * @param artifact 可执行产物
     */
    public ExecutionStageStepper(SourceFile sourceFile, ExecutableArtifact artifact) {
        this(sourceFile, artifact, new ExecutableRunner());
    }

    /**
     * 使用指定运行器创建运行阶段。
     *
     * @param sourceFile 源码文件
     * @param artifact 可执行产物
     * @param runner 可执行文件运行器
     */
    public ExecutionStageStepper(SourceFile sourceFile, ExecutableArtifact artifact, ExecutableRunService runner) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.artifact = Objects.requireNonNull(artifact, "artifact");
        this.runner = Objects.requireNonNull(runner, "runner");
        lastResult = StepResult.advanced(CompileStage.EXECUTION, "Execution 待开始", "等待运行可执行文件。");
    }

    @Override
    public CompileStage stage() {
        return CompileStage.EXECUTION;
    }

    @Override
    public boolean canNext() {
        return inputConfirmed && !completed;
    }

    @Override
    public StepResult next() {
        if (completed) {
            lastResult = StepResult.cannotAdvance(CompileStage.EXECUTION, "Execution 已完成", "没有更多运行步骤。");
            return lastResult;
        }
        if (!inputConfirmed) {
            lastResult = StepResult.cannotAdvance(CompileStage.EXECUTION, "等待运行输入", "请先确认标准输入，或勾选无输入。");
            return lastResult;
        }
        result = runner.run(sourceFile, artifact, standardInput);
        completed = true;
        if (!result.diagnostics().isEmpty()) {
            lastResult = StepResult.failed(
                    CompileStage.EXECUTION,
                    "Execution 诊断",
                    result.diagnostics().getLast().message(),
                    result.diagnostics()
            );
            return lastResult;
        }
        lastResult = StepResult.stageCompleted(CompileStage.EXECUTION, "Execution 完成", executionSummary().getLast());
        return lastResult;
    }

    @Override
    public CurrentStepState snapshot() {
        return new CurrentStepState(
                sourceFile.path(),
                CompileStage.EXECUTION,
                completed ? 1 : 0,
                completed ? 1 : 0,
                PlaybackMode.PAUSED,
                Duration.ofSeconds(1),
                null,
                lastResult.title(),
                lastResult.description(),
                lastResult.diagnostics(),
                new StepCapabilities(canNext(), false, inputConfirmed && !completed, inputConfirmed && !completed, true, false)
        );
    }

    @Override
    public StageStepData data() {
        return new StageStepData(
                CompileStage.EXECUTION,
                new StageProgress(completed ? 1 : 0, 1, completed),
                inputSummary(),
                completed ? executionSummary().getLast() : "",
                completed ? executionSummary() : List.of(),
                result.diagnostics()
        );
    }

    /**
     * 确认运行输入。
     *
     * @param standardInput 标准输入文本
     */
    public void confirmInput(String standardInput) {
        if (completed) {
            return;
        }
        this.standardInput = Objects.requireNonNull(standardInput, "standardInput");
        inputConfirmed = true;
        lastResult = StepResult.advanced(CompileStage.EXECUTION, "运行输入已确认", "可执行文件已准备运行。");
    }

    /**
     * 返回运行结果。
     *
     * @return 运行结果
     */
    public ExecutionResult result() {
        return result;
    }

    /**
     * 返回运行输入是否已确认。
     *
     * @return 已确认时为 {@code true}
     */
    public boolean inputConfirmed() {
        return inputConfirmed;
    }

    private List<String> inputSummary() {
        return List.of(
                "executable " + artifact.path(),
                inputConfirmed ? "stdin confirmed" : "stdin pending",
                standardInput.isBlank() ? "<empty>" : standardInput
        );
    }

    private List<String> executionSummary() {
        ArrayList<String> summary = new ArrayList<>();
        summary.add("executable " + artifact.path());
        result.exitCodeOptional().ifPresent(exitCode -> summary.add("exitCode " + exitCode));
        if (!result.stdout().isBlank()) {
            summary.add("stdout:");
            summary.addAll(result.stdout().lines().toList());
        }
        if (!result.stderr().isBlank()) {
            summary.add("stderr:");
            summary.addAll(result.stderr().lines().toList());
        }
        if (summary.size() == 1) {
            summary.add("no output");
        }
        return List.copyOf(summary);
    }
}
