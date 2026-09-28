package minic.uiapi;

import minic.compiler.ir.IrResult;
import minic.session.CompileObservationSession;
import minic.compiler.SourceFile;

import java.util.List;
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
    public synchronized void loadSource(String sourceName, String source) {
        loadSource(new SourceFile(sourceName, source));
    }

    /**
     * 加载源码文件。
     *
     * @param sourceFile 源码文件
     */
    public synchronized void loadSource(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        session = null;
    }

    /**
     * 开始编译观测会话。
     */
    public synchronized void startSession() {
        ensureSourceLoaded();
        session = CompileObservationSession.fromSource(sourceFile);
    }

    /**
     * 下一步。
     *
     * @return 单步结果
     */
    public synchronized UiControlResultDto next() {
        return UiControlResultDto.from(sourceFile, requireSession().next());
    }

    /**
     * 跳转到下一编译环节。
     *
     * @return 控制结果
     */
    public synchronized UiControlResultDto nextStage() {
        return UiControlResultDto.from(sourceFile, requireSession().nextStage());
    }

    /**
     * 一步推进到执行阶段入口。
     *
     * @return 最后一次控制结果
     */
    public synchronized UiControlResultDto runToExecution() {
        return UiControlResultDto.from(sourceFile, requireSession().runToCompileEnd());
    }

    /**
     * 开启自动播放。
     *
     * @return 控制结果
     */
    public synchronized UiControlResultDto play() {
        return UiControlResultDto.from(sourceFile, requireSession().play());
    }

    /**
     * 开启两倍速自动播放。
     *
     * @return 控制结果
     */
    public synchronized UiControlResultDto playFast() {
        return UiControlResultDto.from(sourceFile, requireSession().playFast());
    }

    /**
     * 手动驱动一个播放 tick。
     *
     * @return 单步结果
     */
    public synchronized UiControlResultDto tick() {
        return UiControlResultDto.from(sourceFile, requireSession().tick());
    }

    /**
     * 暂停播放。
     *
     * @return 控制结果
     */
    public synchronized UiControlResultDto pause() {
        return UiControlResultDto.from(sourceFile, requireSession().pause());
    }

    /**
     * 确认运行阶段标准输入。
     *
     * @param standardInput 标准输入文本
     * @return 控制结果
     */
    public synchronized UiControlResultDto confirmExecutionInput(String standardInput) {
        return UiControlResultDto.from(sourceFile, requireSession().confirmExecutionInput(standardInput));
    }

    /**
     * 上一步预留接口，当前返回 unsupported。
     *
     * @return unsupported 结果
     */
    public synchronized UiControlResultDto previous() {
        return UiControlResultDto.from(sourceFile, requireSession().previous());
    }

    /**
     * 自动倒放预留接口，当前返回 unsupported。
     *
     * @return unsupported 结果
     */
    public synchronized UiControlResultDto reversePlay() {
        return UiControlResultDto.from(sourceFile, requireSession().reversePlay());
    }

    /**
     * 查询当前状态数据。
     *
     * @return 当前状态数据
     */
    public synchronized UiCurrentStateDto currentState() {
        return UiCurrentStateDto.from(sourceFile, requireSession().currentState());
    }

    /**
     * 查询当前阶段数据。
     *
     * @return 当前阶段数据
     */
    public synchronized UiStageDataDto currentStageData() {
        return UiStageDataDto.from(sourceFile, requireSession().currentStageData());
    }

    /**
     * 查询当前阶段图形化数据。
     *
     * @return 当前阶段图形化数据
     */
    public synchronized UiStageVisualDto currentStageVisualData() {
        CompileObservationSession currentSession = requireSession();
        return switch (currentSession.currentStage().id()) {
            case "lexer" -> lexerVisualData();
            case "parser" -> astVisualData();
            case "semantic" -> semanticVisualData();
            case "ir" -> irVisualData();
            case "asm" -> asmVisualData();
            default -> UiStageVisualDto.from(currentSession.currentStageData(), UiCurrentStateDto.from(sourceFile, currentSession.currentState()));
        };
    }

    /**
     * 查询 Lexer 阶段 token 可视化数据。未进入或未完成 Lexer 时返回当前 Lexer 状态。
     *
     * @return token 可视化数据
     */
    public synchronized UiStageVisualDto lexerVisualData() {
        CompileObservationSession currentSession = requireSession();
        minic.compiler.lexer.LexerResult cachedLexerResult = currentSession.lexResult().orElse(null);
        if (cachedLexerResult != null) {
            return UiStageVisualDto.fromLexerTokens(
                    currentSession.currentStageData(),
                    sourceFile,
                    cachedLexerResult.tokens(),
                    null
            );
        }
        if ("lexer".equals(currentSession.currentStage().id())) {
            return UiStageVisualDto.fromLexerTokens(
                    currentSession.currentStageData(),
                    sourceFile,
                    currentSession.lexer().tokens(),
                    currentSession.lexer().currentToken().orElse(null)
            );
        }
        return UiStageVisualDto.from(currentSession.currentStageData(), UiCurrentStateDto.from(sourceFile, currentSession.currentState()));
    }

    /**
     * 查询完整 AST 可视化数据。Parser 尚未准备时返回当前阶段 fallback。
     *
     * @return AST 可视化数据
     */
    public synchronized UiStageVisualDto astVisualData() {
        CompileObservationSession currentSession = requireSession();
        minic.compiler.parser.ParserResult cachedParserResult = currentSession.parseResult().orElse(null);
        if (cachedParserResult != null) {
            return UiStageVisualDto.fromAst(
                    sourceFile,
                    currentSession.currentStageData(),
                    cachedParserResult.program(),
                    null
            );
        }
        if ("parser".equals(currentSession.currentStage().id()) && !currentSession.parser().tokens().isEmpty()) {
            return UiStageVisualDto.fromAst(
                    sourceFile,
                    currentSession.currentStageData(),
                    currentSession.parser().currentResult().program(),
                    currentSession.parser().currentNode().orElse(null),
                    currentSession.parser().completedNodes().stream().map(node -> (Object) node).toList()
            );
        }
        return UiStageVisualDto.from(currentSession.currentStageData(), UiCurrentStateDto.from(sourceFile, currentSession.currentState()));
    }

    /**
     * 查询当前可用的作用域树可视化数据。Semantic 尚未准备时返回当前阶段 fallback。
     *
     * @return 作用域可视化数据
     */
    public synchronized UiStageVisualDto semanticVisualData() {
        CompileObservationSession currentSession = requireSession();
        minic.compiler.semantic.SemanticResult cachedSemanticResult = currentSession.semanticResult().orElse(null);
        if (cachedSemanticResult != null) {
            minic.compiler.parser.ParserResult cachedParserResult = currentSession.parseResult().orElseThrow(() ->
                    new IllegalStateException("parse result is required for completed semantic visual data"));
            return UiStageVisualDto.fromSemanticAstAndScope(
                    sourceFile,
                    currentSession.currentStageData(),
                    cachedParserResult.program(),
                    cachedSemanticResult.globalScope(),
                    null
            );
        }
        if ("semantic".equals(currentSession.currentStage().id()) && currentSession.semanticAnalyzer().stepCount() > 0) {
            return UiStageVisualDto.fromSemanticAstAndScope(
                    sourceFile,
                    currentSession.currentStageData(),
                    currentSession.semanticAnalyzer().program(),
                    currentSession.semanticAnalyzer().globalScope(),
                    currentSession.semanticAnalyzer().currentAction().orElse(null)
            );
        }
        return UiStageVisualDto.from(currentSession.currentStageData(), UiCurrentStateDto.from(sourceFile, currentSession.currentState()));
    }

    /**
     * 查询当前可用的 IR 可视化数据。IR 尚未准备时返回当前阶段 fallback。
     *
     * @return IR 可视化数据
     */
    public synchronized UiStageVisualDto irVisualData() {
        CompileObservationSession currentSession = requireSession();
        minic.compiler.parser.ParserResult cachedParserResult = currentSession.parseResult().orElse(null);
        minic.compiler.semantic.SemanticResult cachedSemanticResult = currentSession.semanticResult().orElse(null);
        if (cachedParserResult != null && cachedSemanticResult != null) {
            IrResult irResult = currentSession.irResult().orElse(null);
            Object activeAstNode = null;
            if ("ir".equals(currentSession.currentStage().id())) {
                irResult = currentSession.irResult().orElseGet(() -> currentSession.irLowerer().currentResult());
                activeAstNode = currentSession.irLowerer().currentAstNode().orElse(null);
            }
            return UiStageVisualDto.fromIrAstAndScope(
                    sourceFile,
                    currentSession.currentStageData(),
                    cachedParserResult.program(),
                    cachedSemanticResult.globalScope(),
                    activeAstNode,
                    irResult
            );
        }
        return UiStageVisualDto.from(currentSession.currentStageData(), UiCurrentStateDto.from(sourceFile, currentSession.currentState()));
    }

    /**
     * 查询当前可用的 Asm 汇编可视化数据。Asm 尚未准备时返回当前阶段 fallback。
     *
     * @return 汇编可视化数据
     */
    public synchronized UiStageVisualDto asmVisualData() {
        CompileObservationSession currentSession = requireSession();
        minic.compiler.asm.AsmResult cachedAsmResult = currentSession.asmResult().orElse(null);
        if (cachedAsmResult != null) {
            return UiStageVisualDto.fromAsmResult(
                    sourceFile,
                    currentSession.currentStageData(),
                    cachedAsmResult,
                    currentSession.irResult().orElse(null)
            );
        }
        if ("asm".equals(currentSession.currentStage().id())) {
            return UiStageVisualDto.fromAsm(
                    sourceFile,
                    currentSession.currentStageData(),
                    currentSession.irResult().orElseThrow(),
                    currentSession.assembler().work()
            );
        }
        return UiStageVisualDto.from(currentSession.currentStageData(), UiCurrentStateDto.from(sourceFile, currentSession.currentState()));
    }

    /**
     * 查询全局数据。
     *
     * @return 全局数据
     */
    public synchronized UiGlobalDataDto globalData() {
        return UiGlobalDataDto.from(sourceFile, requireSession().globalData());
    }

    /**
     * 查询 pipeline 阶段卡片展示语义。
     *
     * @return 阶段卡片列表
     */
    public synchronized List<UiStageViewDto> stageViews() {
        if (session == null) {
            return UiStageViewDto.initialViews();
        }
        return UiStageViewDto.from(currentState(), currentStageData(), globalData());
    }

    /**
     * 查询 Inspector 汇总展示语义。
     *
     * @return Inspector 模型
     */
    public synchronized UiInspectorModelDto inspectorModel() {
        if (session == null) {
            return UiInspectorModelDto.initial();
        }
        return UiInspectorModelDto.from(currentState(), currentStageData(), globalData());
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
