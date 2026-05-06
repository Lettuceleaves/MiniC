package minic.uiapi;

import minic.compiler.ast.decl.Program;
import minic.compiler.ir.lowering.IrLowerer;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
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
    private SourceFile sourceFile;
    private DebugSession session;

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
     * 启动独立 Debug 模式。
     *
     * @return Debug 状态
     */
    public UiDebugStateDto startDebug() {
        ensureSourceLoaded();
        session = new IrDebugInterpreter().runMain(lower(sourceFile), sourceFile);
        session.control(DebugCommand.RESTART);
        return currentState();
    }

    /**
     * 设置源码行断点。
     *
     * @param line 一基源码行号
     * @return Debug 状态
     */
    public UiDebugStateDto setBreakpoint(int line) {
        requireSession().setBreakpoint(line);
        return currentState();
    }

    /**
     * 清除源码行断点。
     *
     * @param line 一基源码行号
     * @return Debug 状态
     */
    public UiDebugStateDto clearBreakpoint(int line) {
        requireSession().clearBreakpoint(line);
        return currentState();
    }

    /**
     * 运行到断点。
     *
     * @return Debug 状态
     */
    public UiDebugStateDto runToBreakpoint() {
        requireSession().control(DebugCommand.RUN_TO_BREAKPOINT);
        return currentState();
    }

    /**
     * 单退。
     *
     * @return Debug 状态
     */
    public UiDebugStateDto stepBack() {
        requireSession().control(DebugCommand.STEP_BACK);
        return currentState();
    }

    /**
     * 查询当前 Debug 状态。
     *
     * @return Debug 状态
     */
    public UiDebugStateDto currentState() {
        return UiDebugDtoMapper.state(requireSession());
    }

    /**
     * 查询元数据视图模型。
     *
     * @return 元数据视图模型
     */
    public UiDebugMetadataViewDto metadataView() {
        return new UiDebugMetadataViewBuilder().build(currentState());
    }

    /**
     * 查询 AST Debug 视图模型。
     *
     * @return AST Debug 视图模型
     */
    public UiDebugAstViewDto astDebugView() {
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
    public UiDebugIrViewDto irDebugView() {
        Lowered lowered = lowerWithProgram(requireSourceFile());
        return new UiDebugIrViewBuilder().build(lowered.module(), currentState());
    }

    /**
     * 查询 ASM Debug 视图模型。
     *
     * @return ASM Debug 视图模型
     */
    public UiDebugAsmViewDto asmDebugView() {
        Lowered lowered = lowerWithProgram(requireSourceFile());
        return new UiDebugAsmViewBuilder().build(lowered.module(), currentState());
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
        LexResult lexResult = new Lexer(sourceFile).lex();
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
        return new Lowered(program, new IrLowerer().lower(program, semanticResult));
    }

    private SourceFile requireSourceFile() {
        ensureSourceLoaded();
        return sourceFile;
    }

    private record Lowered(Program program, IrModule module) {
    }
}
