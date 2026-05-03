package minic.uiapi;

import minic.session.CompileObservationSession;
import minic.source.SourceFile;

import java.util.Objects;

/**
 * UI 层使用的 MiniC 编译观测控制门面。
 *
 * <p>该 API 不暴露内部 stepper 或编译层状态，也不依赖 JavaFX。</p>
 */
public final class MiniCObservationApi {
    private SourceFile sourceFile;
    private CompileObservationSession session;

    /**
     * 加载源码文本。
     *
     * @param sourceName 源码名称
     * @param source 源码文本
     */
    public void loadSource(String sourceName, String source) {
        loadSource(new SourceFile(sourceName, source));
    }

    /**
     * 加载源码文件。
     *
     * @param sourceFile 源码文件
     */
    public void loadSource(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        session = null;
    }

    /**
     * 开始编译观测会话。
     */
    public void startSession() {
        ensureSourceLoaded();
        session = CompileObservationSession.fromSource(sourceFile);
    }

    /**
     * 下一步。
     *
     * @return 单步结果
     */
    public UiControlResultDto next() {
        return UiControlResultDto.from(requireSession().next());
    }

    /**
     * 跳转到下一编译环节。
     *
     * @return 控制结果
     */
    public UiControlResultDto nextStage() {
        return UiControlResultDto.from(requireSession().nextStage());
    }

    /**
     * 开启自动播放。
     *
     * @return 控制结果
     */
    public UiControlResultDto play() {
        return UiControlResultDto.from(requireSession().play());
    }

    /**
     * 开启两倍速自动播放。
     *
     * @return 控制结果
     */
    public UiControlResultDto playFast() {
        return UiControlResultDto.from(requireSession().playFast());
    }

    /**
     * 手动驱动一个播放 tick。
     *
     * @return 单步结果
     */
    public UiControlResultDto tick() {
        return UiControlResultDto.from(requireSession().tick());
    }

    /**
     * 暂停播放。
     *
     * @return 控制结果
     */
    public UiControlResultDto pause() {
        return UiControlResultDto.from(requireSession().pause());
    }

    /**
     * 确认运行阶段标准输入。
     *
     * @param standardInput 标准输入文本
     * @return 控制结果
     */
    public UiControlResultDto confirmExecutionInput(String standardInput) {
        return UiControlResultDto.from(requireSession().confirmExecutionInput(standardInput));
    }

    /**
     * 上一步预留接口，当前返回 unsupported。
     *
     * @return unsupported 结果
     */
    public UiControlResultDto previous() {
        return UiControlResultDto.from(requireSession().previous());
    }

    /**
     * 自动倒放预留接口，当前返回 unsupported。
     *
     * @return unsupported 结果
     */
    public UiControlResultDto reversePlay() {
        return UiControlResultDto.from(requireSession().reversePlay());
    }

    /**
     * 查询当前状态数据。
     *
     * @return 当前状态数据
     */
    public UiCurrentStateDto currentState() {
        return UiCurrentStateDto.from(requireSession().currentState());
    }

    /**
     * 查询当前阶段数据。
     *
     * @return 当前阶段数据
     */
    public UiStageDataDto currentStageData() {
        return UiStageDataDto.from(requireSession().currentStageData());
    }

    /**
     * 查询当前阶段图形化数据。
     *
     * @return 当前阶段图形化数据
     */
    public UiStageVisualDto currentStageVisualData() {
        CompileObservationSession currentSession = requireSession();
        if (currentSession.currentStepper() instanceof minic.runtime.step.LexerStageStepper lexerStepper) {
            return UiStageVisualDto.fromLexerTokens(
                    currentSession.currentStageData(),
                    lexerStepper.lexerState().tokens(),
                    lexerStepper.lexerState().currentToken().orElse(null)
            );
        }
        if (currentSession.currentStepper() instanceof minic.runtime.step.ParserStageStepper parserStepper) {
            return UiStageVisualDto.fromAst(
                    currentSession.currentStageData(),
                    parserStepper.previewProgram(),
                    parserStepper.currentObservationNode().orElse(null),
                    parserStepper.revealedAstNodes()
            );
        }
        if (currentSession.currentStepper() instanceof minic.runtime.step.SemanticStageStepper semanticStepper) {
            return UiStageVisualDto.fromSemanticScope(
                    currentSession.currentStageData(),
                    semanticStepper.semanticState().work().globalScope(),
                    semanticStepper.semanticState().currentAction().orElse(null)
            );
        }
        if (currentSession.currentStepper() instanceof minic.runtime.step.CodegenStageStepper codegenStepper) {
            return UiStageVisualDto.fromAssemblyLines(
                    currentSession.currentStageData(),
                    codegenStepper.codegenState().work().assemblyLineData(),
                    codegenStepper.codegenState().work().currentSection()
            );
        }
        return UiStageVisualDto.from(currentSession.currentStageData(), UiCurrentStateDto.from(currentSession.currentState()));
    }

    /**
     * 查询 Lexer 阶段 token 可视化数据。未进入或未完成 Lexer 时返回当前 Lexer 状态。
     *
     * @return token 可视化数据
     */
    public UiStageVisualDto lexerVisualData() {
        CompileObservationSession currentSession = requireSession();
        minic.compiler.lexer.LexResult cachedLexResult = currentSession.lexResult().orElse(null);
        if (cachedLexResult != null) {
            return UiStageVisualDto.fromLexerTokens(
                    currentSession.currentStageData(),
                    cachedLexResult.tokens(),
                    null
            );
        }
        if (currentSession.currentStepper() instanceof minic.runtime.step.LexerStageStepper lexerStepper) {
            return UiStageVisualDto.fromLexerTokens(
                    currentSession.currentStageData(),
                    lexerStepper.lexerState().tokens(),
                    lexerStepper.lexerState().currentToken().orElse(null)
            );
        }
        return UiStageVisualDto.from(currentSession.currentStageData(), UiCurrentStateDto.from(currentSession.currentState()));
    }

    /**
     * 查询完整 AST 可视化数据。Parser 尚未准备时返回当前阶段 fallback。
     *
     * @return AST 可视化数据
     */
    public UiStageVisualDto astVisualData() {
        CompileObservationSession currentSession = requireSession();
        minic.compiler.parser.ParseResult cachedParseResult = currentSession.parseResult().orElse(null);
        if (cachedParseResult != null) {
            return UiStageVisualDto.fromAst(
                    currentSession.currentStageData(),
                    cachedParseResult.program(),
                    null
            );
        }
        if (currentSession.currentStepper() instanceof minic.runtime.step.ParserStageStepper parserStepper) {
            return UiStageVisualDto.fromAst(
                    currentSession.currentStageData(),
                    parserStepper.previewProgram(),
                    parserStepper.currentObservationNode().orElse(null),
                    parserStepper.revealedAstNodes()
            );
        }
        return UiStageVisualDto.from(currentSession.currentStageData(), UiCurrentStateDto.from(currentSession.currentState()));
    }

    /**
     * 查询当前可用的作用域树可视化数据。Semantic 尚未准备时返回当前阶段 fallback。
     *
     * @return 作用域可视化数据
     */
    public UiStageVisualDto semanticVisualData() {
        CompileObservationSession currentSession = requireSession();
        minic.compiler.semantic.SemanticResult cachedSemanticResult = currentSession.semanticResult().orElse(null);
        if (cachedSemanticResult != null) {
            return UiStageVisualDto.fromSemanticScope(
                    currentSession.currentStageData(),
                    cachedSemanticResult.globalScope(),
                    null
            );
        }
        if (currentSession.currentStepper() instanceof minic.runtime.step.SemanticStageStepper semanticStepper) {
            return UiStageVisualDto.fromSemanticScope(
                    currentSession.currentStageData(),
                    semanticStepper.semanticState().work().globalScope(),
                    semanticStepper.semanticState().currentAction().orElse(null)
            );
        }
        return UiStageVisualDto.from(currentSession.currentStageData(), UiCurrentStateDto.from(currentSession.currentState()));
    }

    /**
     * 查询全局数据。
     *
     * @return 全局数据
     */
    public UiGlobalDataDto globalData() {
        return UiGlobalDataDto.from(requireSession().globalData());
    }

    private void ensureSourceLoaded() {
        if (sourceFile == null) {
            throw new IllegalStateException("source must be loaded before starting a session");
        }
    }

    private CompileObservationSession requireSession() {
        if (session == null) {
            throw new IllegalStateException("session must be started before using compile controls");
        }
        return session;
    }
}
