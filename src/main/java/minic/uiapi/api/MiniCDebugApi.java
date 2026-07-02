package minic.uiapi;

import minic.compiler.ast.decl.Program;
import minic.compiler.ir.lowering.IrLowerer;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.MiniCPreprocessor;
import minic.compiler.preprocess.PreprocessResult;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.runtime.debug.DebugCommand;
import minic.runtime.debug.DebugSession;
import minic.runtime.debug.IrDebugInterpreter;
import minic.source.SourceFile;

import java.util.Objects;

/**
 * UI 层使用的独立 Debug 模式门面。
 */
public final class MiniCDebugApi {
    private final Preprocessor preprocessor;
    private SourceFile sourceFile;
    private DebugSession session;
    private Lowered lowered;

    public MiniCDebugApi() {
        this(new MiniCPreprocessor());
    }

    public MiniCDebugApi(Preprocessor preprocessor) {
        this.preprocessor = Objects.requireNonNull(preprocessor, "preprocessor");
    }

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
        lowered = null;
    }

    /**
     * 启动独立 Debug 模式。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto startDebug() {
        ensureSourceLoaded();
        Lowered lowered = lowerWithProgram(sourceFile);
        session = new IrDebugInterpreter().runMain(lowered.module(), sourceFile, lowered.semanticResult());
        session.control(DebugCommand.RESTART);
        return currentState();
    }

    /**
     * 设置源码行断点。
     *
     * @param line 一基源码行号
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto setBreakpoint(int line) {
        requireSession().setBreakpoint(line);
        return currentState();
    }

    /**
     * 清除源码行断点。
     *
     * @param line 一基源码行号
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto clearBreakpoint(int line) {
        requireSession().clearBreakpoint(line);
        return currentState();
    }

    /**
     * 运行到断点。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto runToBreakpoint() {
        requireSession().control(DebugCommand.RUN_TO_BREAKPOINT);
        return currentState();
    }

    /**
     * 运行到结束或运行时错误，不因普通断点暂停。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto runToEnd() {
        requireSession().control(DebugCommand.RUN_TO_END);
        return currentState();
    }

    /**
     * 快进到结束、断点、错误或暂停请求。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto fastForward() {
        requireSession().control(DebugCommand.FAST_FORWARD);
        return currentState();
    }

    /**
     * 单步。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto stepOver() {
        requireSession().control(DebugCommand.STEP_OVER);
        return currentState();
    }

    /**
     * 步入。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto stepInto() {
        requireSession().control(DebugCommand.STEP_INTO);
        return currentState();
    }

    /**
     * 步返。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto stepOut() {
        requireSession().control(DebugCommand.STEP_OUT);
        return currentState();
    }

    /**
     * 暂停连续运行。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto pause() {
        requireSession().control(DebugCommand.PAUSE);
        return currentState();
    }

    /**
     * 重启 Debug 会话。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto restart() {
        requireSession().control(DebugCommand.RESTART);
        return currentState();
    }

    /**
     * 关闭 Debug 会话。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto close() {
        requireSession().control(DebugCommand.CLOSE);
        return currentState();
    }

    /**
     * 单退。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto stepBack() {
        requireSession().control(DebugCommand.STEP_BACK);
        return currentState();
    }

    /**
     * 回退到本调用层的上一句。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto stepBackOver() {
        requireSession().control(DebugCommand.STEP_BACK_OVER);
        return currentState();
    }

    /**
     * 步退到上一个断点。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto backToBreakpoint() {
        requireSession().control(DebugCommand.BACK_TO_BREAKPOINT);
        return currentState();
    }

    /**
     * 返回进入当前调用前的调用处。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto backToCallSite() {
        requireSession().control(DebugCommand.BACK_TO_CALL_SITE);
        return currentState();
    }

    /**
     * 查询当前 Debug 状态。
     *
     * @return Debug 状态
     */
    public synchronized UiDebugStateDto currentState() {
        return UiDebugDtoMapper.state(requireSession());
    }

    /**
     * 查询元数据视图模型。
     *
     * @return 元数据视图模型
     */
    public synchronized UiDebugMetadataViewDto metadataView() {
        return new UiDebugMetadataViewBuilder().build(currentState());
    }

    /**
     * 查询 AST Debug 视图模型。
     *
     * @return AST Debug 视图模型
     */
    public synchronized UiDebugAstViewDto astDebugView() {
        Lowered lowered = lowerWithProgram(requireSourceFile());
        return new UiDebugAstViewBuilder().build(
                lowered.program(),
                lowered.module(),
                requireSession().currentSnapshot().cursor().sourceRange()
        );
    }

    /**
     * 查询 IR Debug 视图模型。
     *
     * @return IR Debug 视图模型
     */
    public synchronized UiDebugIrViewDto irDebugView() {
        Lowered lowered = lowerWithProgram(requireSourceFile());
        return new UiDebugIrViewBuilder().build(lowered.module(), currentState());
    }

    /**
     * 查询 ASM Debug 视图模型。
     *
     * @return ASM Debug 视图模型
     */
    public synchronized UiDebugAsmViewDto asmDebugView() {
        Lowered lowered = lowerWithProgram(requireSourceFile());
        return new UiDebugAsmViewBuilder().build(lowered.module(), currentState());
    }

    /**
     * 查询数据结构 Debug 视图模型。
     *
     * @return 数据结构 Debug 视图模型
     */
    public synchronized UiDebugDataStructureViewDto dataStructureDebugView() {
        return new UiDebugDataStructureViewBuilder().build(
                requireSourceFile(),
                currentState(),
                requireSession().currentSnapshot().processSpace(),
                requireSession().visualEvents(),
                requireSession().dataFlowEvents()
        );
    }

    private void ensureSourceLoaded() {
        if (sourceFile == null) {
            throw new IllegalStateException("source must be loaded before starting debug");
        }
    }

    private DebugSession requireSession() {
        if (session == null) {
            throw new IllegalStateException("debug session must be started before using debug controls");
        }
        return session;
    }

    private IrModule lower(SourceFile sourceFile) {
        return lowerWithProgram(sourceFile).module();
    }

    private Lowered lowerWithProgram(SourceFile sourceFile) {
        if (lowered != null && lowered.sourceFile().equals(sourceFile)) {
            return lowered;
        }
        PreprocessResult preprocessResult = preprocessor.preprocess(sourceFile);
        if (!preprocessResult.diagnostics().isEmpty()) {
            throw new IllegalStateException("debug source has preprocess diagnostics");
        }
        SourceFile preprocessed = preprocessResult.sourceFile();
        LexResult lexResult = new Lexer(preprocessed).lex();
        if (!lexResult.diagnostics().isEmpty()) {
            throw new IllegalStateException("debug source has lexer diagnostics");
        }
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        if (!parseResult.diagnostics().isEmpty()) {
            throw new IllegalStateException("debug source has parser diagnostics");
        }
        Program program = parseResult.program();
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        if (!semanticResult.diagnostics().isEmpty()) {
            throw new IllegalStateException("debug source has semantic diagnostics");
        }
        lowered = new Lowered(sourceFile, program, semanticResult, new IrLowerer().lower(program, semanticResult));
        return lowered;
    }

    private SourceFile requireSourceFile() {
        ensureSourceLoaded();
        return sourceFile;
    }

    private record Lowered(SourceFile sourceFile, Program program, SemanticResult semanticResult, IrModule module) {
    }
}
