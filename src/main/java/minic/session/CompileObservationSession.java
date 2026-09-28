package minic.session;

import minic.compiler.Loop;
import minic.compiler.Stage;
import minic.compiler.asm.AsmResult;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.IrResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.lexer.LexerResult;
import minic.compiler.lexer.token.Token;
import minic.compiler.nativebuild.NativeBuildResult;
import minic.compiler.nativebuild.NativeBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.parser.ParserResult;
import minic.compiler.preprocess.PreprocessResult;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.diagnostics.Diagnostic;
import minic.execution.ExecutableRunner;
import minic.execution.ExecutionResult;
import minic.session.Observation.Capabilities;
import minic.session.Observation.ControlResult;
import minic.session.Observation.CurrentState;
import minic.session.Observation.GlobalData;
import minic.session.Observation.Outcome;
import minic.session.Observation.PlaybackMode;
import minic.session.Observation.Progress;
import minic.session.Observation.StageData;
import minic.session.Observation.StageId;
import minic.compiler.SourceFile;
import minic.source.SourceRange;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 编译观测会话。核心编译推进完全委托给唯一的 {@link Loop}，本类只负责将
 * Stage 状态整理成 UI 可消费的数据，并在编译结束后管理可执行文件运行。
 */
public final class CompileObservationSession {
    private static final List<StageId> STAGE_ORDER = List.of(
            StageId.SOURCE,
            StageId.PREPROCESS,
            StageId.LEXER,
            StageId.PARSER,
            StageId.SEMANTIC,
            StageId.IR,
            StageId.ASM,
            StageId.NATIVE_BUILD,
            StageId.EXECUTION
    );

    private final SourceFile sourceFile;
    private final Preprocessor preprocessor;
    private final Lexer lexer;
    private final Parser parser;
    private final SemanticAnalyzer semanticAnalyzer;
    private final IrLowerer irLowerer;
    private final Assembler assembler;
    private final NativeBuilder nativeBuilder;
    private final Loop loop;
    private final ExecutableRunner executableRunner = new ExecutableRunner();

    private boolean sourceStage = true;
    private PlaybackMode playbackMode = PlaybackMode.PAUSED;
    private SourceRange currentRange;
    private ControlResult lastResult;
    private String standardInput = "";
    private boolean executionInputConfirmed;
    private boolean executionCompleted;
    private ExecutionResult executionResult = ExecutionResult.notRun();

    private CompileObservationSession(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        preprocessor = new Preprocessor(sourceFile, Preprocessor.Options.defaults());
        lexer = new Lexer(preprocessor);
        parser = new Parser(lexer, true);
        semanticAnalyzer = new SemanticAnalyzer(parser);
        irLowerer = new IrLowerer(semanticAnalyzer);
        assembler = new Assembler(irLowerer);
        nativeBuilder = new NativeBuilder(
                sourceFile,
                assembler,
                Path.of("build", "minic-output"),
                artifactName(sourceFile.path())
        );
        loop = new Loop(List.of(
                preprocessor,
                lexer,
                parser,
                semanticAnalyzer,
                irLowerer,
                assembler,
                nativeBuilder
        ));
        lastResult = result(Outcome.ADVANCED, StageId.SOURCE, "源码已加载", "源码已准备进入编译流水线。", List.of());
    }

    public static CompileObservationSession fromSource(SourceFile sourceFile) {
        return new CompileObservationSession(sourceFile);
    }

    public static CompileObservationSession fromSource(String sourceName, String source) {
        return fromSource(new SourceFile(sourceName, source));
    }

    public List<StageId> stageOrder() {
        return STAGE_ORDER;
    }

    public Loop loop() {
        return loop;
    }

    public StageId currentStage() {
        if (sourceStage) {
            return StageId.SOURCE;
        }
        if (loop.completed() && nativeBuilder.succeeded()) {
            return StageId.EXECUTION;
        }
        return loop.currentStage().map(CompileObservationSession::stageId).orElse(StageId.EXECUTION);
    }

    public long globalStepCount() {
        return loop.stepCount() + (executionCompleted ? 1 : 0);
    }

    public PlaybackMode playbackMode() {
        return playbackMode;
    }

    /** UI 单步：源码入口只切换一次，其余编译步骤直接调用 {@link Loop#step()}。 */
    public ControlResult next() {
        if (sourceStage) {
            sourceStage = false;
            return remember(result(Outcome.ADVANCED, StageId.PREPROCESS, "进入预编译", "已进入编译流水线。", List.of()));
        }
        if (currentStage() == StageId.EXECUTION) {
            return runExecution();
        }
        if (!loop.canNext()) {
            List<Diagnostic> diagnostics = currentStageDiagnostics();
            return remember(result(
                    diagnostics.isEmpty() ? Outcome.CANNOT_ADVANCE : Outcome.FAILED,
                    currentStage(),
                    diagnostics.isEmpty() ? "编译已停止" : "编译阶段失败",
                    diagnostics.isEmpty() ? "没有更多编译步骤。" : "当前阶段存在错误诊断。",
                    diagnostics
            ));
        }

        StageId stage = currentStage();
        Stage stageObject = loop.currentStage().orElseThrow();
        currentRange = loop.step();
        if (!stageObject.canNext()) {
            if (!stageObject.succeeded()) {
                return remember(result(Outcome.FAILED, stage, stageTitle(stage) + "失败", "错误阻止了后续编译。", diagnostics(stageObject)));
            }
            return remember(result(Outcome.STAGE_COMPLETED, stage, stageTitle(stage) + "完成", "阶段结果已经生成。", List.of()));
        }
        return remember(result(Outcome.ADVANCED, stage, "执行 " + stageTitle(stage) + " 单步", currentItem(stage), currentStageDiagnostics()));
    }

    /** UI 跑完当前阶段：直接调用 {@link Loop#runCurrentStage()}。 */
    public ControlResult nextStage() {
        if (sourceStage || currentStage() == StageId.EXECUTION) {
            return next();
        }
        if (!loop.canNext()) {
            return next();
        }
        StageId stage = currentStage();
        Stage stageObject = loop.currentStage().orElseThrow();
        loop.runCurrentStage();
        currentRange = loop.lastSourceRange().orElse(null);
        if (!stageObject.succeeded()) {
            return remember(result(Outcome.FAILED, stage, stageTitle(stage) + "失败", "错误阻止了后续编译。", diagnostics(stageObject)));
        }
        return remember(result(Outcome.STAGE_COMPLETED, stage, stageTitle(stage) + "完成", "已运行到当前阶段结束。", List.of()));
    }

    /** UI 一次运行到编译结束：直接调用 {@link Loop#run()}。 */
    public ControlResult runToCompileEnd() {
        sourceStage = false;
        if (!loop.canNext()) {
            return next();
        }
        loop.run();
        currentRange = loop.lastSourceRange().orElse(null);
        if (!nativeBuilder.succeeded()) {
            return remember(result(Outcome.FAILED, currentStage(), "编译失败", "流水线未生成可执行文件。", diagnostics()));
        }
        return remember(result(Outcome.STAGE_COMPLETED, StageId.NATIVE_BUILD, "编译完成", "可执行文件已经生成。", List.of()));
    }

    public ControlResult play() {
        playbackMode = PlaybackMode.PLAYING;
        return remember(result(Outcome.ADVANCED, currentStage(), "自动播放", "自动播放已开启。", List.of()));
    }

    public ControlResult playFast() {
        playbackMode = PlaybackMode.FAST_PLAYING;
        return remember(result(Outcome.ADVANCED, currentStage(), "两倍速播放", "两倍速播放已开启。", List.of()));
    }

    public ControlResult pause() {
        playbackMode = PlaybackMode.PAUSED;
        return remember(result(Outcome.ADVANCED, currentStage(), "暂停", "编译观测已暂停。", List.of()));
    }

    public ControlResult tick() {
        if (playbackMode == PlaybackMode.PAUSED) {
            return remember(result(Outcome.CANNOT_ADVANCE, currentStage(), "播放已暂停", "暂停状态不会自动推进。", List.of()));
        }
        ControlResult result = next();
        if (!currentState().canNext()) {
            playbackMode = PlaybackMode.PAUSED;
        }
        return result;
    }

    public ControlResult previous() {
        return remember(result(Outcome.UNSUPPORTED, currentStage(), "上一步暂不支持", "核心 Loop 仅负责正向编译。", List.of()));
    }

    public ControlResult reversePlay() {
        return remember(result(Outcome.UNSUPPORTED, currentStage(), "自动倒放暂不支持", "核心 Loop 仅负责正向编译。", List.of()));
    }

    public ControlResult confirmExecutionInput(String standardInput) {
        if (currentStage() != StageId.EXECUTION) {
            throw new IllegalStateException("execution stage is not ready");
        }
        this.standardInput = Objects.requireNonNull(standardInput, "standardInput");
        executionInputConfirmed = true;
        return remember(result(Outcome.ADVANCED, StageId.EXECUTION, "运行输入已确认", "可执行文件已准备运行。", List.of()));
    }

    public CurrentState currentState() {
        StageId stage = currentStage();
        boolean canNext = sourceStage || loop.canNext() || stage == StageId.EXECUTION && executionInputConfirmed && !executionCompleted;
        Capabilities capabilities = new Capabilities(canNext, false, canNext, canNext, true, false);
        return new CurrentState(
                sourceFile.path(),
                stage,
                globalStepCount(),
                stageStepCount(stage),
                playbackMode,
                frameInterval(),
                currentRange,
                lastResult.title(),
                lastResult.description(),
                currentStageDiagnostics(),
                capabilities
        );
    }

    public StageData currentStageData() {
        StageId stage = currentStage();
        return new StageData(
                stage,
                new Progress(stageStepCount(stage), totalSteps(stage), stageCompleted(stage)),
                inputSummary(stage),
                currentItem(stage),
                outputSummary(stage),
                currentStageDiagnostics()
        );
    }

    public GlobalData globalData() {
        return new GlobalData(
                sourceFile.content(),
                stageSummaries(),
                diagnostics(),
                preprocessSummary(),
                tokenSummary(),
                astSummary(),
                semanticSummary(),
                irSummary(),
                assembler.work().assemblyLines(),
                artifactSummary(),
                executionInputSummary(),
                executionOutputSummary()
        );
    }

    public Preprocessor preprocessor() {
        return preprocessor;
    }

    public Lexer lexer() {
        return lexer;
    }

    public Parser parser() {
        return parser;
    }

    public SemanticAnalyzer semanticAnalyzer() {
        return semanticAnalyzer;
    }

    public IrLowerer irLowerer() {
        return irLowerer;
    }

    public Assembler assembler() {
        return assembler;
    }

    public NativeBuilder nativeBuilder() {
        return nativeBuilder;
    }

    public Optional<PreprocessResult> preprocessResult() {
        return preprocessor.resultReady() ? Optional.of(preprocessor.preprocessResult()) : Optional.empty();
    }

    public Optional<LexerResult> lexResult() {
        return !lexer.canNext() && lexer.succeeded() ? Optional.of(lexer.toLexerResult()) : Optional.empty();
    }

    public Optional<ParserResult> parseResult() {
        return !parser.canNext() && parser.succeeded() ? Optional.of(parser.result()) : Optional.empty();
    }

    public Optional<SemanticResult> semanticResult() {
        return !semanticAnalyzer.canNext() && semanticAnalyzer.succeeded()
                ? Optional.of(semanticAnalyzer.semanticResult())
                : Optional.empty();
    }

    public Optional<IrResult> irResult() {
        return !irLowerer.canNext() && irLowerer.succeeded() ? Optional.of(irLowerer.result()) : Optional.empty();
    }

    public Optional<AsmResult> asmResult() {
        return !assembler.canNext() && assembler.succeeded() ? Optional.of(assembler.result()) : Optional.empty();
    }

    public Optional<NativeBuildResult> nativeBuildResult() {
        return !nativeBuilder.canNext() ? Optional.of(nativeBuilder.result()) : Optional.empty();
    }

    private ControlResult runExecution() {
        if (executionCompleted) {
            return remember(result(Outcome.CANNOT_ADVANCE, StageId.EXECUTION, "执行已完成", "没有更多运行步骤。", executionResult.diagnostics()));
        }
        if (!executionInputConfirmed) {
            return remember(result(Outcome.CANNOT_ADVANCE, StageId.EXECUTION, "等待运行输入", "请先确认标准输入。", List.of()));
        }
        NativeBuildResult buildResult = nativeBuilder.result();
        executionResult = executableRunner.run(
                sourceFile,
                buildResult.executableArtifactOptional().orElseThrow(),
                standardInput
        );
        executionCompleted = true;
        if (!executionResult.diagnostics().isEmpty()) {
            return remember(result(Outcome.FAILED, StageId.EXECUTION, "执行失败", "可执行文件运行失败。", executionResult.diagnostics()));
        }
        return remember(result(Outcome.STAGE_COMPLETED, StageId.EXECUTION, "执行完成", executionOutputSummary().getLast(), List.of()));
    }

    private long stageStepCount(StageId stage) {
        return switch (stage) {
            case SOURCE -> 0;
            case PREPROCESS -> preprocessor.stepCount();
            case LEXER -> lexer.stepCount();
            case PARSER -> parser.stepCount();
            case SEMANTIC -> semanticAnalyzer.stepCount();
            case IR -> irLowerer.completedStepCount();
            case ASM -> assembler.work().completedLineCount() + (assembler.canNext() ? 0 : 1);
            case NATIVE_BUILD -> nativeBuilder.stepCount();
            case EXECUTION -> executionCompleted ? 1 : 0;
        };
    }

    private long totalSteps(StageId stage) {
        return switch (stage) {
            case SOURCE -> 1;
            case PREPROCESS, ASM -> -1;
            case LEXER -> preprocessResult().map(result -> (long) result.sourceFile().content().length() + 1).orElse(-1L);
            case PARSER -> lexer.tokens().isEmpty() ? -1 : lexer.tokens().size();
            case SEMANTIC -> semanticAnalyzer.stepCount() == 0 ? -1 : semanticAnalyzer.actionCount() + 1L;
            case IR -> irLowerer.completedStepCount() == 0 ? -1 : irLowerer.plannedStepCount();
            case NATIVE_BUILD -> nativeBuilder.plannedStepCount();
            case EXECUTION -> 1;
        };
    }

    private boolean stageCompleted(StageId stage) {
        return switch (stage) {
            case SOURCE -> !sourceStage;
            case PREPROCESS -> !preprocessor.canNext();
            case LEXER -> !lexer.canNext();
            case PARSER -> !parser.canNext();
            case SEMANTIC -> !semanticAnalyzer.canNext();
            case IR -> !irLowerer.canNext();
            case ASM -> !assembler.canNext();
            case NATIVE_BUILD -> !nativeBuilder.canNext();
            case EXECUTION -> executionCompleted;
        };
    }

    private List<String> inputSummary(StageId stage) {
        return switch (stage) {
            case SOURCE, PREPROCESS -> List.of("source=" + sourceFile.path(), "length=" + sourceFile.content().length());
            case LEXER -> List.of("offset=" + lexer.currentOffset());
            case PARSER -> List.of("tokens=" + parser.tokens().size(), "index=" + parser.currentIndex());
            case SEMANTIC -> parseResult().map(result -> List.of("functions=" + result.program().functions().size(), "structs=" + result.program().structs().size())).orElse(List.of());
            case IR -> List.of("semanticActions=" + semanticAnalyzer.completedActionCount());
            case ASM -> List.of("irFunctions=" + irResult().map(result -> result.functions().size()).orElse(0));
            case NATIVE_BUILD -> List.of("assemblyLines=" + assembler.work().completedLineCount());
            case EXECUTION -> executionInputSummary();
        };
    }

    private String currentItem(StageId stage) {
        return switch (stage) {
            case SOURCE -> "源码已加载";
            case PREPROCESS -> preprocessor.currentOperation();
            case LEXER -> lexer.currentToken().map(CompileObservationSession::tokenSummary).orElse("");
            case PARSER -> parser.currentNode().map(node -> node.getClass().getSimpleName()).orElse("");
            case SEMANTIC -> semanticAnalyzer.currentAction().map(action -> action.kind() + " " + action.subject()).orElse("");
            case IR -> irLowerer.currentOperationName().isEmpty() ? "" : irLowerer.currentOperationName() + " " + irLowerer.currentSubject();
            case ASM -> assembler.currentLine().orElse("");
            case NATIVE_BUILD -> nativeBuilder.currentOperation();
            case EXECUTION -> executionCompleted ? executionOutputSummary().getLast() : executionInputConfirmed ? "等待执行" : "等待输入";
        };
    }

    private List<String> outputSummary(StageId stage) {
        return switch (stage) {
            case SOURCE -> sourceFile.content().lines().map(line -> "src " + line).toList();
            case PREPROCESS -> preprocessSummary();
            case LEXER -> tokenSummary();
            case PARSER -> astSummary();
            case SEMANTIC -> semanticSummary();
            case IR -> irSummary();
            case ASM -> assembler.work().assemblyLines();
            case NATIVE_BUILD -> artifactSummary();
            case EXECUTION -> executionOutputSummary();
        };
    }

    private List<String> preprocessSummary() {
        if (!preprocessor.resultReady()) {
            return List.of();
        }
        PreprocessResult result = preprocessor.preprocessResult();
        ArrayList<String> summary = new ArrayList<>();
        result.includes().forEach(include -> summary.add("include " + include.requestedPath()));
        result.macros().forEach(macro -> summary.add("macro " + macro.name()));
        result.sourceFile().content().lines().map(line -> "out " + line).forEach(summary::add);
        return List.copyOf(summary);
    }

    private List<String> tokenSummary() {
        return lexer.tokens().stream().map(CompileObservationSession::tokenSummary).toList();
    }

    private List<String> astSummary() {
        return parser.completedNodes().stream().map(node -> node.getClass().getSimpleName()).toList();
    }

    private List<String> semanticSummary() {
        ArrayList<String> summary = new ArrayList<>();
        semanticAnalyzer.currentAction().ifPresent(action -> summary.add(action.kind() + " " + action.subject()));
        if (!semanticAnalyzer.canNext() && semanticAnalyzer.succeeded()) {
            summary.add("expressionTypes=" + semanticAnalyzer.expressionTypeCount());
        }
        return List.copyOf(summary);
    }

    private List<String> irSummary() {
        if (irLowerer.completedStepCount() == 0) {
            return List.of();
        }
        ArrayList<String> summary = new ArrayList<>(irLowerer.work().loweringLog());
        summary.addAll(irLowerer.work().functionSummaries());
        return List.copyOf(summary);
    }

    private List<String> artifactSummary() {
        if (nativeBuilder.canNext()) {
            return List.of();
        }
        NativeBuildResult result = nativeBuilder.result();
        ArrayList<String> summary = new ArrayList<>();
        result.assemblyPathOptional().ifPresent(path -> summary.add("assembly " + path));
        result.objectPathOptional().ifPresent(path -> summary.add("object " + path));
        result.executableArtifactOptional().ifPresent(artifact -> summary.add("executable " + artifact.path()));
        return List.copyOf(summary);
    }

    private List<String> executionInputSummary() {
        if (currentStage() != StageId.EXECUTION && !loop.completed()) {
            return List.of();
        }
        return List.of(
                executionInputConfirmed ? "stdin confirmed" : "stdin pending",
                standardInput.isBlank() ? "<empty>" : standardInput
        );
    }

    private List<String> executionOutputSummary() {
        if (!executionCompleted) {
            return List.of();
        }
        ArrayList<String> summary = new ArrayList<>();
        executionResult.exitCodeOptional().ifPresent(code -> summary.add("exitCode " + code));
        executionResult.stdout().lines().filter(line -> !line.isBlank()).map(line -> "stdout " + line).forEach(summary::add);
        executionResult.stderr().lines().filter(line -> !line.isBlank()).map(line -> "stderr " + line).forEach(summary::add);
        if (summary.isEmpty()) {
            summary.add("no output");
        }
        return List.copyOf(summary);
    }

    private List<String> stageSummaries() {
        int currentIndex = STAGE_ORDER.indexOf(currentStage());
        ArrayList<String> summaries = new ArrayList<>();
        for (int index = 0; index < STAGE_ORDER.size(); index++) {
            String state = index < currentIndex ? "completed" : index == currentIndex ? "current" : "queued";
            summaries.add(STAGE_ORDER.get(index).id() + " " + state);
        }
        return List.copyOf(summaries);
    }

    private List<Diagnostic> diagnostics() {
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(preprocessor.diagnostics());
        diagnostics.addAll(lexer.diagnostics());
        diagnostics.addAll(parser.diagnostics());
        diagnostics.addAll(semanticAnalyzer.diagnostics());
        diagnostics.addAll(nativeBuilder.diagnostics());
        diagnostics.addAll(executionResult.diagnostics());
        return List.copyOf(diagnostics);
    }

    private List<Diagnostic> currentStageDiagnostics() {
        return switch (currentStage()) {
            case SOURCE, IR, ASM -> List.of();
            case PREPROCESS -> preprocessor.diagnostics();
            case LEXER -> lexer.diagnostics();
            case PARSER -> parser.diagnostics();
            case SEMANTIC -> semanticAnalyzer.diagnostics();
            case NATIVE_BUILD -> nativeBuilder.diagnostics();
            case EXECUTION -> executionResult.diagnostics();
        };
    }

    private static List<Diagnostic> diagnostics(Stage stage) {
        if (stage instanceof Preprocessor value) return value.diagnostics();
        if (stage instanceof Lexer value) return value.diagnostics();
        if (stage instanceof Parser value) return value.diagnostics();
        if (stage instanceof SemanticAnalyzer value) return value.diagnostics();
        if (stage instanceof NativeBuilder value) return value.diagnostics();
        return List.of();
    }

    private Duration frameInterval() {
        long base = minic.settings.MiniCSettings.frameIntervalMillis();
        return Duration.ofMillis(playbackMode == PlaybackMode.FAST_PLAYING ? Math.max(1, base / 2) : base);
    }

    private ControlResult remember(ControlResult result) {
        lastResult = result;
        return result;
    }

    private static ControlResult result(Outcome outcome, StageId stage, String title, String description, List<Diagnostic> diagnostics) {
        return new ControlResult(outcome, stage, title, description, diagnostics);
    }

    private static StageId stageId(Stage stage) {
        if (stage instanceof Preprocessor) return StageId.PREPROCESS;
        if (stage instanceof Lexer) return StageId.LEXER;
        if (stage instanceof Parser) return StageId.PARSER;
        if (stage instanceof SemanticAnalyzer) return StageId.SEMANTIC;
        if (stage instanceof IrLowerer) return StageId.IR;
        if (stage instanceof Assembler) return StageId.ASM;
        if (stage instanceof NativeBuilder) return StageId.NATIVE_BUILD;
        throw new IllegalArgumentException("unknown stage: " + stage.getClass().getName());
    }

    private static String stageTitle(StageId stage) {
        return switch (stage) {
            case SOURCE -> "源码";
            case PREPROCESS -> "预编译";
            case LEXER -> "词法分析";
            case PARSER -> "语法分析";
            case SEMANTIC -> "语义分析";
            case IR -> "IR lowering";
            case ASM -> "汇编生成";
            case NATIVE_BUILD -> "本机构建";
            case EXECUTION -> "执行";
        };
    }

    private static String tokenSummary(Token token) {
        return token.type() + " " + (token.lexeme().isEmpty() ? "<empty>" : token.lexeme());
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
