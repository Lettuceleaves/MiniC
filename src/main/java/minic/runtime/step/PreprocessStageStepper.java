package minic.runtime.step;

import minic.compiler.preprocess.IncludeSummary;
import minic.compiler.preprocess.MacroSummary;
import minic.compiler.preprocess.MiniCPreprocessor;
import minic.compiler.preprocess.PreprocessResult;
import minic.diagnostics.Diagnostic;
import minic.source.SourceFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 预编译阶段 stepper，展示原始源码到预处理产物的转换。
 */
public final class PreprocessStageStepper implements StageStepper {
    private final SourceFile sourceFile;
    private PreprocessResult preprocessResult;
    private StepResult lastResult = StepResult.advanced(
            CompileStage.PREPROCESS,
            "预编译待开始",
            "准备展开 include、宏和条件编译。"
    );
    private boolean completed;

    /**
     * 创建预编译阶段适配器。
     *
     * @param sourceFile 原始源码
     */
    public PreprocessStageStepper(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
    }

    @Override
    public CompileStage stage() {
        return CompileStage.PREPROCESS;
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    @Override
    public StepResult next() {
        if (completed) {
            lastResult = StepResult.cannotAdvance(CompileStage.PREPROCESS, "预编译已完成", "没有更多预编译步骤。");
            return lastResult;
        }
        preprocessResult = new MiniCPreprocessor().preprocess(sourceFile);
        completed = true;
        if (!preprocessResult.diagnostics().isEmpty()) {
            Diagnostic diagnostic = preprocessResult.diagnostics().getFirst();
            lastResult = StepResult.failed(
                    CompileStage.PREPROCESS,
                    "预编译诊断",
                    diagnostic.message(),
                    preprocessResult.diagnostics()
            );
            return lastResult;
        }
        lastResult = StepResult.stageCompleted(
                CompileStage.PREPROCESS,
                "预编译完成",
                "已生成预处理后源码。"
        );
        return lastResult;
    }

    @Override
    public CurrentStepState snapshot() {
        return new CurrentStepState(
                sourceFile.path(),
                CompileStage.PREPROCESS,
                completed ? 1 : 0,
                completed ? 1 : 0,
                PlaybackMode.PAUSED,
                Duration.ofSeconds(1),
                null,
                lastResult.title(),
                lastResult.description(),
                lastResult.diagnostics(),
                new StepCapabilities(canNext(), false, canNext(), canNext(), true, false)
        );
    }

    @Override
    public StageStepData data() {
        return new StageStepData(
                CompileStage.PREPROCESS,
                new StageProgress(completed ? 1 : 0, 1, completed),
                List.of(
                        "source=" + sourceFile.path(),
                        "length=" + sourceFile.content().length()
                ),
                currentItem(),
                outputLines(),
                preprocessResult == null ? List.of() : preprocessResult.diagnostics()
        );
    }

    /**
     * 返回预编译结果。
     *
     * @return 预编译结果
     */
    public PreprocessResult preprocessResult() {
        if (preprocessResult == null) {
            throw new IllegalStateException("preprocess result is not ready");
        }
        return preprocessResult;
    }

    private String currentItem() {
        if (!completed) {
            return "等待预编译";
        }
        if (preprocessResult == null) {
            return "";
        }
        if (!preprocessResult.diagnostics().isEmpty()) {
            return preprocessResult.diagnostics().getFirst().code() + " " + preprocessResult.diagnostics().getFirst().message();
        }
        return "预处理后源码 " + preprocessResult.sourceFile().content().length() + " 字符";
    }

    private List<String> outputLines() {
        if (preprocessResult == null) {
            return List.of();
        }
        ArrayList<String> lines = new ArrayList<>();
        for (IncludeSummary include : preprocessResult.includes()) {
            lines.add("include " + include.requestedPath() + (include.expanded() ? " expanded" : " skipped"));
        }
        for (MacroSummary macro : preprocessResult.macros()) {
            lines.add("macro " + macro.name() + (macro.defined() ? "=" + macro.replacement() : " undefined"));
        }
        preprocessResult.sourceFile().content().lines()
                .map(line -> "out " + line)
                .forEach(lines::add);
        return List.copyOf(lines);
    }
}
