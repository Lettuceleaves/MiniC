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
