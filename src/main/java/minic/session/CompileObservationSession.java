package minic.session;

import minic.compiler.ast.decl.Program;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.parser.ParseResult;
import minic.compiler.preprocess.PreprocessResult;
import minic.compiler.semantic.SemanticResult;
import minic.runtime.step.CodegenStageStepper;
import minic.runtime.step.CompileStage;
import minic.runtime.step.CurrentStepState;
import minic.runtime.step.ExecutionStageStepper;
import minic.runtime.step.GlobalStepData;
import minic.runtime.step.IrStageStepper;
import minic.runtime.step.LexerStageStepper;
import minic.runtime.step.ParserStageStepper;
import minic.runtime.step.PlaybackMode;
import minic.runtime.step.PreprocessStageStepper;
import minic.runtime.step.SemanticStageStepper;
import minic.runtime.step.SourceStageStepper;
import minic.runtime.step.StageStepData;
import minic.runtime.step.StageStepper;
import minic.runtime.step.StepCapabilities;
import minic.runtime.step.StepResult;
import minic.runtime.step.ToolchainStageStepper;
import minic.diagnostics.Diagnostic;
import minic.source.SourceFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 编译观测会话，负责串联各编译阶段 stepper。
 */
public final class CompileObservationSession {
    private static final List<CompileStage> STAGE_ORDER = List.of(
            CompileStage.SOURCE,
            CompileStage.PREPROCESS,
            CompileStage.LEXER,
            CompileStage.PARSER,
            CompileStage.SEMANTIC,
            CompileStage.IR,
            CompileStage.CODEGEN,
            CompileStage.TOOLCHAIN,
            CompileStage.EXECUTION
    );

    private final SourceFile sourceFile;
    private final Map<CompileStage, StageStepper> steppers = new EnumMap<>(CompileStage.class);
    private int currentStageIndex;
    private long globalStepCount;
    private PlaybackMode playbackMode = PlaybackMode.PAUSED;
    private boolean finalStageResultPending;

    private LexResult lexResult;
    private PreprocessResult preprocessResult;
    private ParseResult parseResult;
    private SemanticResult semanticResult;
    private IrModule irModule;
    private AssemblySource assemblySource;

    private CompileObservationSession(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        steppers.put(CompileStage.SOURCE, new SourceStageStepper(sourceFile));
        steppers.put(CompileStage.PREPROCESS, new PreprocessStageStepper(sourceFile));
    }

    /**
     * 从源码文件创建编译观测会话。
     *
     * @param sourceFile 源码文件
     * @return 编译观测会话
     */
    public static CompileObservationSession fromSource(SourceFile sourceFile) {
        return new CompileObservationSession(sourceFile);
    }

    /**
     * 从源码名称和文本创建编译观测会话。
     *
     * @param sourceName 源码名称
     * @param source 源码文本
     * @return 编译观测会话
     */
    public static CompileObservationSession fromSource(String sourceName, String source) {
        return fromSource(new SourceFile(sourceName, source));
    }

    /**
     * 返回阶段顺序。
     *
     * @return 阶段顺序
     */
    public List<CompileStage> stageOrder() {
        return STAGE_ORDER;
    }

    /**
     * 返回当前阶段。
     *
     * @return 当前阶段
     */
    public CompileStage currentStage() {
        return STAGE_ORDER.get(currentStageIndex);
    }

    /**
     * 返回当前阶段 stepper。
     *
     * @return 当前阶段 stepper
     */
    public StageStepper currentStepper() {
        return stepperFor(currentStage());
    }

    /**
     * 返回全局已推进步数。
     *
     * @return 全局步骤计数
     */
    public long globalStepCount() {
        return globalStepCount;
    }

    /**
     * 返回当前播放模式。
     *
     * @return 播放模式
     */
    public PlaybackMode playbackMode() {
        return playbackMode;
    }

    /**
     * 进入自动播放状态。
     *
     * @return 单步控制结果
     */
    public StepResult play() {
        playbackMode = PlaybackMode.PLAYING;
        return StepResult.advanced(currentStage(), "自动播放", "自动播放已开启。");
    }

    /**
     * 进入两倍速自动播放状态。
     *
     * @return 单步控制结果
     */
    public StepResult playFast() {
        playbackMode = PlaybackMode.FAST_PLAYING;
        return StepResult.advanced(currentStage(), "两倍速播放", "两倍速播放已开启。");
    }

    /**
     * 暂停自动播放。
     *
     * @return 单步控制结果
     */
    public StepResult pause() {
        playbackMode = PlaybackMode.PAUSED;
        return StepResult.advanced(currentStage(), "暂停", "编译观测已暂停。");
    }

    /**
     * 手动驱动一次播放帧。该方法不等待真实时间，供 UI 定时器或测试调用。
     *
     * @return 单步结果
     */
    public StepResult tick() {
        if (playbackMode == PlaybackMode.PAUSED) {
            return StepResult.cannotAdvance(currentStage(), "播放已暂停", "暂停状态不会自动推进。");
        }
        StepResult result = next();
        if (!currentState().canNext()) {
            playbackMode = PlaybackMode.PAUSED;
        }
        return result;
    }

    /**
     * 反向退回一步的未来扩展点。本阶段仅预留接口，不执行状态回退。
     *
     * @return unsupported 单步结果
     */
    public StepResult previous() {
        return StepResult.unsupported(currentStage(), "上一步暂不支持", "调度层只预留反向步进能力，当前版本不执行状态回退。");
    }

    /**
     * 自动倒放的未来扩展点。本阶段仅预留接口，不改变播放状态。
     *
     * @return unsupported 单步结果
     */
    public StepResult reversePlay() {
        return StepResult.unsupported(currentStage(), "自动倒放暂不支持", "调度层只预留自动倒放能力，当前版本不执行反向播放。");
    }

    /**
     * 全局推进一步。当前阶段完成后，本次调用只切换到下一个阶段。
     *
     * @return 单步结果
     */
    public StepResult next() {
        StageStepper stepper = currentStepper();
        if (stepper.canNext()) {
            StepResult result = stepper.next();
            globalStepCount++;
            if (!stepper.canNext()) {
                cacheCurrentStageOutput();
                if (atLastStage()) {
                    finalStageResultPending = true;
                }
            }
            return result;
        }
        if (currentStage() == CompileStage.EXECUTION && stepper instanceof ExecutionStageStepper executionStepper
                && !executionStepper.inputConfirmed()) {
            return StepResult.cannotAdvance(currentStage(), "等待运行输入", "请先确认标准输入，或勾选无输入。");
        }
        if (atLastStage()) {
            if (finalStageResultPending) {
                finalStageResultPending = false;
            }
            return StepResult.cannotAdvance(currentStage(), "编译观测已完成", "没有更多编译步骤。");
        }
        if (hasBlockingDiagnostics()) {
            return StepResult.failed(
                    currentStage(),
                    "编译阶段失败",
                    "当前阶段存在诊断，已停止后续编译阶段。",
                    currentStageDiagnostics()
            );
        }
        prepareNextStage();
        advanceStageIndex();
        return StepResult.advanced(currentStage(), "进入阶段", "已准备 " + currentStage().id() + " 阶段。");
    }

    /**
     * 跳转到下一编译阶段。该方法会完成当前阶段剩余步骤，并在可用时进入下一阶段。
     *
     * @return 跳转结果
     */
    public StepResult nextStage() {
        CompileStage startStage = currentStage();
        if (atLastStage() && !currentStepper().canNext()) {
            return StepResult.cannotAdvance(currentStage(), "已经是最后阶段", "当前已无后续编译阶段。");
        }
        StepResult last = null;
        int guard = 0;
        while (currentStage() == startStage && currentStepper().canNext() && guard++ < 10000) {
            last = next();
            if (last.outcome() == minic.runtime.step.StepOutcome.FAILED) {
                return last;
            }
        }
        if (currentStage() != startStage) {
            return StepResult.advanced(currentStage(), "跳转到下一环节", "已进入 " + currentStage().id() + " 阶段。");
        }
        StepResult enterNextStage = next();
        if (enterNextStage.outcome() == minic.runtime.step.StepOutcome.ADVANCED && currentStage() != startStage) {
            return StepResult.advanced(currentStage(), "跳转到下一环节", "已进入 " + currentStage().id() + " 阶段。");
        }
        return enterNextStage;
    }

    /**
     * 返回当前状态数据。
     *
     * @return 当前状态数据
     */
    public CurrentStepState currentState() {
        CurrentStepState state = currentStepper().snapshot();
        return new CurrentStepState(
                sourceFile.path(),
                currentStage(),
                globalStepCount,
                state.stageStepIndex(),
                playbackMode,
                frameInterval(),
                state.sourceRange(),
                state.title(),
                state.description(),
                state.diagnostics(),
                capabilities()
        );
    }

    /**
     * 返回当前阶段数据。
     *
     * @return 当前阶段数据
     */
    public StageStepData currentStageData() {
        return currentStepper().data();
    }

    /**
     * 返回全局数据。
     *
     * @return 全局数据
     */
    public GlobalStepData globalData() {
        return new GlobalStepData(
                sourceFile.content(),
                stageSummaries(),
                diagnostics(),
                summaryFor(CompileStage.PREPROCESS),
                summaryFor(CompileStage.LEXER),
                summaryFor(CompileStage.PARSER),
                summaryFor(CompileStage.SEMANTIC),
                summaryFor(CompileStage.IR),
                summaryFor(CompileStage.CODEGEN),
                summaryFor(CompileStage.TOOLCHAIN),
                executionInputSummary(),
                summaryFor(CompileStage.EXECUTION)
        );
    }

    /**
     * 返回已缓存的词法结果。
     *
     * @return 词法结果
     */
    public Optional<LexResult> lexResult() {
        return Optional.ofNullable(lexResult);
    }

    /**
     * 返回已缓存的预编译结果。
     *
     * @return 预编译结果
     */
    public Optional<PreprocessResult> preprocessResult() {
        return Optional.ofNullable(preprocessResult);
    }

    /**
     * 返回已缓存的解析结果。
     *
     * @return 解析结果
     */
    public Optional<ParseResult> parseResult() {
        return Optional.ofNullable(parseResult);
    }

    /**
     * 返回已缓存的语义结果。
     *
     * @return 语义结果
     */
    public Optional<SemanticResult> semanticResult() {
        return Optional.ofNullable(semanticResult);
    }

    /**
     * 返回已缓存的 IR 模块。
     *
     * @return IR 模块
     */
    public Optional<IrModule> irModule() {
        return Optional.ofNullable(irModule);
    }

    /**
     * 返回已缓存的汇编输出。
     *
     * @return 汇编输出
     */
    public Optional<AssemblySource> assemblySource() {
        return Optional.ofNullable(assemblySource);
    }

    /**
     * 确认运行阶段标准输入。
     *
     * @param standardInput 标准输入文本
     * @return 控制结果
     */
    public StepResult confirmExecutionInput(String standardInput) {
        StageStepper stepper = stepperFor(CompileStage.EXECUTION);
        if (!(stepper instanceof ExecutionStageStepper executionStepper)) {
            throw new IllegalStateException("execution stage is not prepared");
        }
        executionStepper.confirmInput(standardInput);
        return StepResult.advanced(CompileStage.EXECUTION, "运行输入已确认", "可执行文件已准备运行。");
    }

    StageStepper stepperFor(CompileStage stage) {
        StageStepper stepper = steppers.get(stage);
        if (stepper == null) {
            throw new IllegalStateException("stage is not prepared: " + stage);
        }
        return stepper;
    }

    void cacheLexResult(LexResult lexResult) {
        this.lexResult = Objects.requireNonNull(lexResult, "lexResult");
    }

    void cachePreprocessResult(PreprocessResult preprocessResult) {
        this.preprocessResult = Objects.requireNonNull(preprocessResult, "preprocessResult");
    }

    void cacheParseResult(ParseResult parseResult) {
        this.parseResult = Objects.requireNonNull(parseResult, "parseResult");
    }

    void cacheSemanticResult(SemanticResult semanticResult) {
        this.semanticResult = Objects.requireNonNull(semanticResult, "semanticResult");
    }

    void cacheIrModule(IrModule irModule) {
        this.irModule = Objects.requireNonNull(irModule, "irModule");
    }

    void cacheAssemblySource(AssemblySource assemblySource) {
        this.assemblySource = Objects.requireNonNull(assemblySource, "assemblySource");
    }

    void putStepper(CompileStage stage, StageStepper stepper) {
        steppers.put(Objects.requireNonNull(stage, "stage"), Objects.requireNonNull(stepper, "stepper"));
    }

    void advanceStageIndex() {
        if (currentStageIndex < STAGE_ORDER.size() - 1) {
            currentStageIndex++;
        }
    }

    boolean atLastStage() {
        return currentStageIndex == STAGE_ORDER.size() - 1;
    }

    void incrementGlobalStepCount() {
        globalStepCount++;
    }

    void setPlaybackMode(PlaybackMode playbackMode) {
        this.playbackMode = Objects.requireNonNull(playbackMode, "playbackMode");
    }

    private void cacheCurrentStageOutput() {
        switch (currentStage()) {
            case PREPROCESS -> cachePreprocessResult(((PreprocessStageStepper) currentStepper()).preprocessResult());
            case LEXER -> cacheLexResult(((LexerStageStepper) currentStepper()).lexerState().toLexResult());
            case PARSER -> cacheParseResult(((ParserStageStepper) currentStepper()).parserState().toParseResult());
            case SEMANTIC -> cacheSemanticResult(((SemanticStageStepper) currentStepper()).semanticState().toSemanticResult());
            case IR -> cacheIrModule(((IrStageStepper) currentStepper()).irState().toIrModule());
            case CODEGEN -> cacheAssemblySource(((CodegenStageStepper) currentStepper()).codegenState().toAssemblySource());
            case TOOLCHAIN -> {
                // Toolchain stepper owns its result; globalData reads it directly.
            }
            case EXECUTION -> {
                // Execution stepper owns its result; globalData reads it directly.
            }
            case SOURCE -> {
                // Source stepper 只持有原始源码快照。
            }
        }
    }

    private void prepareNextStage() {
        CompileStage nextStage = STAGE_ORDER.get(currentStageIndex + 1);
        switch (nextStage) {
            case PREPROCESS -> {
                // Preprocess stepper 在会话创建时已准备好，以便启动后先展示源码阶段。
            }
            case LEXER -> {
                PreprocessResult readyPreprocessResult = preprocessResult().orElseGet(() -> {
                    PreprocessResult result = ((PreprocessStageStepper) currentStepper()).preprocessResult();
                    cachePreprocessResult(result);
                    return result;
                });
                putStepper(CompileStage.LEXER, new LexerStageStepper(readyPreprocessResult.sourceFile()));
            }
            case PARSER -> {
                LexResult readyLexResult = lexResult().orElseGet(() -> {
                    LexResult result = ((LexerStageStepper) currentStepper()).lexerState().toLexResult();
                    cacheLexResult(result);
                    return result;
                });
                putStepper(CompileStage.PARSER, new ParserStageStepper(readyLexResult.tokens()));
            }
            case SEMANTIC -> {
                ParseResult readyParseResult = parseResult().orElseGet(() -> {
                    ParseResult result = ((ParserStageStepper) currentStepper()).parserState().toParseResult();
                    cacheParseResult(result);
                    return result;
                });
                putStepper(CompileStage.SEMANTIC, new SemanticStageStepper(readyParseResult.program()));
            }
            case IR -> {
                ParseResult readyParseResult = parseResult().orElseThrow(() ->
                        new IllegalStateException("parse result is required before IR stage"));
                SemanticResult readySemanticResult = semanticResult().orElseGet(() -> {
                    SemanticResult result = ((SemanticStageStepper) currentStepper()).semanticState().toSemanticResult();
                    cacheSemanticResult(result);
                    return result;
                });
                putStepper(CompileStage.IR, new IrStageStepper(readyParseResult.program(), readySemanticResult));
            }
            case CODEGEN -> {
                IrModule readyIrModule = irModule().orElseGet(() -> {
                    IrModule result = ((IrStageStepper) currentStepper()).irState().toIrModule();
                    cacheIrModule(result);
                    return result;
                });
                putStepper(CompileStage.CODEGEN, new CodegenStageStepper(readyIrModule));
            }
            case TOOLCHAIN -> {
                AssemblySource readyAssemblySource = assemblySource().orElseGet(() -> {
                    AssemblySource result = ((CodegenStageStepper) currentStepper()).codegenState().toAssemblySource();
                    cacheAssemblySource(result);
                    return result;
                });
                putStepper(CompileStage.TOOLCHAIN, new ToolchainStageStepper(sourceFile, readyAssemblySource));
            }
            case EXECUTION -> {
                ToolchainStageStepper toolchainStepper = (ToolchainStageStepper) stepperFor(CompileStage.TOOLCHAIN);
                putStepper(CompileStage.EXECUTION, new ExecutionStageStepper(
                        sourceFile,
                        toolchainStepper.result().executableArtifactOptional().orElseThrow(() ->
                                new IllegalStateException("executable artifact is required before execution stage"))
                ));
            }
            default -> throw new IllegalStateException("unsupported next stage: " + nextStage);
        }
    }

    private StepCapabilities capabilities() {
        boolean canNext = currentStepper().canNext() || !atLastStage() || finalStageResultPending;
        return new StepCapabilities(canNext, false, canNext, canNext, true, false);
    }

    private Duration frameInterval() {
        long base = minic.settings.MiniCSettings.frameIntervalMillis();
        return switch (playbackMode) {
            case PAUSED, PLAYING -> Duration.ofMillis(base);
            case FAST_PLAYING -> Duration.ofMillis(base / 2);
        };
    }

    private List<String> stageSummaries() {
        return STAGE_ORDER.stream()
                .map(stage -> stage.id() + (steppers.containsKey(stage) ? " prepared" : " pending"))
                .toList();
    }

    private List<String> summaryFor(CompileStage stage) {
        StageStepper stepper = steppers.get(stage);
        if (stepper == null) {
            return List.of();
        }
        return stepper.data().accumulatedOutput();
    }

    private List<String> executionInputSummary() {
        StageStepper stepper = steppers.get(CompileStage.EXECUTION);
        if (stepper == null) {
            return List.of();
        }
        return stepper.data().inputSummary();
    }

    private boolean hasBlockingDiagnostics() {
        return !currentStageDiagnostics().isEmpty();
    }

    private List<Diagnostic> currentStageDiagnostics() {
        return currentStepper().data().diagnostics();
    }

    private List<Diagnostic> diagnostics() {
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        steppers.values().stream()
                .flatMap(stepper -> stepper.data().diagnostics().stream())
                .forEach(diagnostics::add);
        return List.copyOf(diagnostics);
    }
}
