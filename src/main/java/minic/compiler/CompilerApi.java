package minic.compiler;

import minic.compiler.asm.Assembler;
import minic.compiler.execute.ExecutableRunner;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.IrResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.SourceRange;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 编译流水线的唯一循环控制器。
 *
 * <p>CompilerApi 只持有 Stage 列表并调用当前阶段的 step。当前阶段耗尽后，成功则进入
 * 下一阶段，失败则结束流水线。阶段间自行传递输入；{@link #runThrough(Stage)}
 * 提供通用的阶段完成边界。</p>
 */
public final class CompilerApi {
    private final List<Stage> stages;
    private int currentStageIndex;
    private Stage currentStage;
    private boolean completed;
    private long stepCount;
    private SourceRange lastSourceRange;
    private Stage.Result lastStepResult;

    /** 使用标准阶段顺序创建一条完整编译流水线。 */
    public CompilerApi(SourceFile sourceFile) {
        this(sourceFile, LanguageMode.C);
    }

    public CompilerApi(SourceFile sourceFile, LanguageMode languageMode) {
        this(createPipeline(sourceFile, languageMode));
    }

    public CompilerApi(List<? extends Stage> stages) {
        Objects.requireNonNull(stages, "stages");
        this.stages = List.copyOf(stages);
        if (this.stages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("stages must not contain null");
        }
        this.stages.forEach(Stage::resetResultObservation);
        completed = this.stages.isEmpty();
        currentStage = completed ? null : this.stages.getFirst();
    }

    /** 执行到整个编译流水线结束。 */
    public void run() {
        while (canNext()) {
            step();
        }
    }

    /** 执行到 IR 阶段完成并返回结果；后续 ASM、本机构建阶段保持未执行。 */
    public IrResult runToIr() {
        IrLowerer ir = stages.stream()
                .filter(IrLowerer.class::isInstance)
                .map(IrLowerer.class::cast)
                .findFirst().orElseThrow(() -> new IllegalStateException("pipeline has no IR stage"));
        runThrough(ir);
        if (!ir.succeeded()) {
            throw new IllegalStateException("compilation stopped before IR: "
                    + currentStage.getClass().getSimpleName());
        }
        return ir.result();
    }

    /**
     * 执行到指定 Stage 完成；成功时停在下一 Stage 的入口。
     *
     * @param target 必须属于当前流水线的阶段实例
     */
    public void runThrough(Stage target) {
        Objects.requireNonNull(target, "target");
        int targetIndex = stages.indexOf(target);
        if (targetIndex < 0) {
            throw new IllegalArgumentException("target stage does not belong to this pipeline");
        }
        while (canNext() && currentStageIndex <= targetIndex) {
            step();
        }
    }

    /** 执行到当前 Stage 结束；成功时停在下一 Stage 的入口。 */
    public void runCurrentStage() {
        if (!canNext()) {
            return;
        }
        Stage stage = currentStage;
        while (canNext() && currentStage == stage) {
            step();
        }
    }

    /** 执行当前阶段的一步，并在该阶段耗尽时自动切换。 */
    public SourceRange step() {
        return advance().sourceRange();
    }

    /** 执行当前阶段的一步，并直接返回该步生成的 Result。 */
    public Stage.Result stepResult() {
        return advance();
    }

    private Stage.Result advance() {
        if (!canNext()) {
            throw new IllegalStateException("compiler api is already completed");
        }
        Stage executedStage = currentStage;
        SourceRange sourceRange = executedStage.step();
        lastStepResult = executedStage.consumeLatestStepResult();
        lastSourceRange = sourceRange;
        stepCount++;
        if (!executedStage.canNext()) {
            // 是否能进入下一阶段只由 Stage.succeeded() 决定。
            if (!executedStage.succeeded()) {
                completed = true;
                return lastStepResult;
            }
            currentStageIndex++;
            if (currentStageIndex >= stages.size()) {
                completed = true;
            } else {
                currentStage = stages.get(currentStageIndex);
            }
        }
        return lastStepResult;
    }

    public boolean canNext() {
        return !completed;
    }

    public boolean completed() {
        return completed;
    }

    public int currentStageIndex() {
        return currentStageIndex;
    }

    /** 返回整个流水线已经执行的单步数。 */
    public long stepCount() {
        return stepCount;
    }

    /** 返回最近一次单步对应的源码范围。 */
    public Optional<SourceRange> lastSourceRange() {
        return Optional.ofNullable(lastSourceRange);
    }

    /** 返回最近一次 step 生成的结果。 */
    public Optional<Stage.Result> lastStepResult() {
        return Optional.ofNullable(lastStepResult);
    }

    /** 按阶段实例打开或关闭逐步结果记录。 */
    public void setResultRecording(Stage stage, boolean enabled) {
        requireStage(stage).setResultRecordingEnabled(enabled);
    }

    /** 按阶段下标打开或关闭逐步结果记录。 */
    public void setResultRecording(int stageIndex, boolean enabled) {
        if (stageIndex < 0 || stageIndex >= stages.size()) {
            throw new IndexOutOfBoundsException("stageIndex: " + stageIndex);
        }
        stages.get(stageIndex).setResultRecordingEnabled(enabled);
    }

    /** 为流水线中指定类型的全部阶段打开或关闭逐步结果记录。 */
    public void setResultRecording(Class<? extends Stage> stageType, boolean enabled) {
        Objects.requireNonNull(stageType, "stageType");
        boolean matched = false;
        for (Stage stage : stages) {
            if (stageType.isInstance(stage)) {
                stage.setResultRecordingEnabled(enabled);
                matched = true;
            }
        }
        if (!matched) {
            throw new IllegalArgumentException("pipeline has no stage of type " + stageType.getName());
        }
    }

    /** 返回指定阶段保留的步骤结果。 */
    public List<Stage.Result> results(Stage stage) {
        return requireStage(stage).stepResults();
    }

    /**
     * 返回指定阶段的当前结果。该阶段打开逐步记录时，
     * 每次 step 后都会返回新的完整上下文；关闭时只有阶段最终结果。
     */
    public Optional<Stage.Result> result(Stage stage) {
        return requireStage(stage).stageResult();
    }

    /** 返回指定阶段集中收集的错误。 */
    public List<Diagnostic> errors(Stage stage) {
        return requireStage(stage).errors();
    }

    public Optional<Stage> currentStage() {
        return Optional.ofNullable(currentStage);
    }

    public List<Stage> stages() {
        return stages;
    }

    private Stage requireStage(Stage stage) {
        Objects.requireNonNull(stage, "stage");
        if (!stages.contains(stage)) {
            throw new IllegalArgumentException("stage does not belong to this pipeline");
        }
        return stage;
    }

    private static List<Stage> createPipeline(SourceFile sourceFile, LanguageMode languageMode) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(languageMode, "languageMode");
        Preprocessor preprocessor = new Preprocessor(sourceFile, Preprocessor.Options.defaults(languageMode));
        Lexer lexer = new Lexer(preprocessor, languageMode);
        Parser parser = new Parser(lexer, true);
        SemanticAnalyzer semantic = new SemanticAnalyzer(parser);
        IrLowerer ir = new IrLowerer(semantic);
        Assembler assembler = new Assembler(ir);
        Path outputDirectory = Path.of("build", "minic-output");
        String artifactName = artifactName(sourceFile.path());
        ObjBuilder obj = new ObjBuilder(sourceFile, assembler, outputDirectory, artifactName);
        Linker linker = new Linker(sourceFile, obj, outputDirectory, artifactName);
        ExecutableRunner execution = new ExecutableRunner(sourceFile, linker);
        return List.of(preprocessor, lexer, parser, semantic, ir, assembler, obj, linker, execution);
    }

    private static String artifactName(String sourceName) {
        String normalized = sourceName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isBlank() ? "minic-program" : safe;
    }
}
