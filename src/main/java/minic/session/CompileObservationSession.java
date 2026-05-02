package minic.session;

import minic.compiler.ast.decl.Program;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.windows.WindowsX64CodegenStepState;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.parser.ParseResult;
import minic.compiler.semantic.SemanticResult;
import minic.runtime.step.CompileStage;
import minic.runtime.step.CurrentStepState;
import minic.runtime.step.GlobalStepData;
import minic.runtime.step.LexerStageStepper;
import minic.runtime.step.PlaybackMode;
import minic.runtime.step.StageProgress;
import minic.runtime.step.StageStepData;
import minic.runtime.step.StageStepper;
import minic.runtime.step.StepCapabilities;
import minic.source.SourceFile;

import java.time.Duration;
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
            CompileStage.LEXER,
            CompileStage.PARSER,
            CompileStage.SEMANTIC,
            CompileStage.IR,
            CompileStage.CODEGEN
    );

    private final SourceFile sourceFile;
    private final Map<CompileStage, StageStepper> steppers = new EnumMap<>(CompileStage.class);
    private int currentStageIndex;
    private long globalStepCount;
    private PlaybackMode playbackMode = PlaybackMode.PAUSED;

    private LexResult lexResult;
    private ParseResult parseResult;
    private SemanticResult semanticResult;
    private IrModule irModule;
    private AssemblySource assemblySource;

    private CompileObservationSession(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        steppers.put(CompileStage.LEXER, new LexerStageStepper(sourceFile));
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
                List.of(),
                summaryFor(CompileStage.LEXER),
                summaryFor(CompileStage.PARSER),
                summaryFor(CompileStage.SEMANTIC),
                summaryFor(CompileStage.IR),
                summaryFor(CompileStage.CODEGEN),
                List.of()
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

    private StepCapabilities capabilities() {
        boolean canNext = currentStepper().canNext() || !atLastStage();
        return new StepCapabilities(canNext, false, canNext, canNext, true, false);
    }

    private Duration frameInterval() {
        return switch (playbackMode) {
            case PAUSED, PLAYING -> Duration.ofSeconds(1);
            case FAST_PLAYING -> Duration.ofMillis(500);
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
}
