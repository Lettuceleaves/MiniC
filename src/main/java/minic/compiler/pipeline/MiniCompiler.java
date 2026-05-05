package minic.compiler.pipeline;

import minic.compiler.codegen.AssemblyEmitter;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.windows.WindowsX64AssemblyEmitter;
import minic.compiler.ir.lowering.IrLowerer;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.MiniCPreprocessor;
import minic.compiler.preprocess.PreprocessResult;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.preprocess.PreprocessOptions;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.compiler.toolchain.ToolchainResult;
import minic.runtime.execution.ExecutableRunner;
import minic.runtime.execution.ExecutionResult;
import minic.source.SourceFile;

import java.util.Objects;

/**
 * MiniC 核心编译流水线入口。
 */
public final class MiniCompiler {
    private final AssemblyEmitter assemblyEmitter;
    private final Preprocessor preprocessor;

    /**
     * 使用默认 Windows x86_64 汇编 emitter 创建编译器。
     */
    public MiniCompiler() {
        this(new WindowsX64AssemblyEmitter(), new MiniCPreprocessor());
    }

    /**
     * 使用指定汇编 emitter 创建编译器。
     *
     * @param assemblyEmitter 汇编 emitter
     */
    public MiniCompiler(AssemblyEmitter assemblyEmitter) {
        this(assemblyEmitter, new MiniCPreprocessor());
    }

    /**
     * 使用指定汇编 emitter 和预编译器创建编译器。
     *
     * @param assemblyEmitter 汇编 emitter
     * @param preprocessor 预编译器
     */
    public MiniCompiler(AssemblyEmitter assemblyEmitter, Preprocessor preprocessor) {
        this.assemblyEmitter = Objects.requireNonNull(assemblyEmitter, "assemblyEmitter");
        this.preprocessor = Objects.requireNonNull(preprocessor, "preprocessor");
    }

    /**
     * 编译源码到当前目标平台汇编文本。
     *
     * <p>A061 阶段只编排前端、语义分析、IR lowering 和汇编生成；工具链尚未执行，
     * 因此结果中的工具链产物路径为空。</p>
     *
     * @param sourceFile 源码文件
     * @return 编译结果
     */
    public CompileResult compile(SourceFile sourceFile) {
        return compile(sourceFile, CompileOptions.assemblyOnly());
    }

    /**
     * 使用指定选项编译源码。
     *
     * @param sourceFile 源码文件
     * @param options 编译选项
     * @return 编译结果
     */
    public CompileResult compile(SourceFile sourceFile, CompileOptions options) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(options, "options");
        PreprocessResult preprocessResult = preprocessor.preprocess(sourceFile, new PreprocessOptions(options.includeRoots()));
        if (!preprocessResult.diagnostics().isEmpty()) {
            return new CompileResult(preprocessResult, null, null, null, null, null, ToolchainResult.notRun());
        }

        LexResult lexResult = new Lexer(preprocessResult.sourceFile()).lex();
        if (!lexResult.diagnostics().isEmpty()) {
            return new CompileResult(preprocessResult, lexResult, null, null, null, null, ToolchainResult.notRun());
        }

        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        if (!parseResult.diagnostics().isEmpty()) {
            return new CompileResult(preprocessResult, lexResult, parseResult, null, null, null, ToolchainResult.notRun());
        }

        SemanticResult semanticResult = new SemanticAnalyzer().analyze(parseResult.program());
        if (!semanticResult.diagnostics().isEmpty()) {
            return new CompileResult(preprocessResult, lexResult, parseResult, semanticResult, null, null, ToolchainResult.notRun());
        }

        IrModule irModule = new IrLowerer().lower(parseResult.program(), semanticResult);
        AssemblySource assemblySource = assemblyEmitter.emit(irModule);
        ToolchainResult toolchainResult = options.runToolchain()
                ? options.toolchain().buildExecutable(
                        sourceFile,
                        assemblySource,
                        options.outputDirectory(),
                        options.artifactName()
                )
                : ToolchainResult.notRun();
        ExecutionResult executionResult = ExecutionResult.notRun();
        if (options.runExecutable() && toolchainResult.diagnostics().isEmpty()) {
            executionResult = toolchainResult.executableArtifactOptional()
                    .map(artifact -> new ExecutableRunner().run(sourceFile, artifact))
                    .orElse(ExecutionResult.notRun());
        }
        return new CompileResult(
                preprocessResult,
                lexResult,
                parseResult,
                semanticResult,
                irModule,
                assemblySource,
                toolchainResult,
                executionResult
        );
    }
}
