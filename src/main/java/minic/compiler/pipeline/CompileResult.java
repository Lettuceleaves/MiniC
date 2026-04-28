package minic.compiler.pipeline;

import minic.compiler.ast.decl.Program;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.parser.ParseResult;
import minic.compiler.semantic.SemanticResult;
import minic.compiler.toolchain.ToolchainResult;
import minic.diagnostics.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 一次核心编译流水线结果。
 *
 * @param lexResult 词法分析结果
 * @param parseResult 语法分析结果；词法失败导致未执行时为 {@code null}
 * @param semanticResult 语义分析结果；前序失败导致未执行时为 {@code null}
 * @param irModule IR 模块；前序失败导致未生成时为 {@code null}
 * @param assemblySource 汇编文本；前序失败导致未生成时为 {@code null}
 * @param toolchainResult 工具链结果
 */
public record CompileResult(
        LexResult lexResult,
        ParseResult parseResult,
        SemanticResult semanticResult,
        IrModule irModule,
        AssemblySource assemblySource,
        ToolchainResult toolchainResult
) {
    /**
     * 创建核心编译流水线结果。
     *
     * @param lexResult 词法分析结果
     * @param parseResult 语法分析结果；词法失败导致未执行时为 {@code null}
     * @param semanticResult 语义分析结果；前序失败导致未执行时为 {@code null}
     * @param irModule IR 模块；前序失败导致未生成时为 {@code null}
     * @param assemblySource 汇编文本；前序失败导致未生成时为 {@code null}
     * @param toolchainResult 工具链结果
     */
    public CompileResult {
        Objects.requireNonNull(lexResult, "lexResult");
        Objects.requireNonNull(toolchainResult, "toolchainResult");
    }

    /**
     * 返回程序 AST。
     *
     * @return 程序 AST；不存在时为空
     */
    public Optional<Program> programOptional() {
        return parseResultOptional().map(ParseResult::program);
    }

    /**
     * 返回语法分析结果。
     *
     * @return 语法分析结果；不存在时为空
     */
    public Optional<ParseResult> parseResultOptional() {
        return Optional.ofNullable(parseResult);
    }

    /**
     * 返回语义分析结果。
     *
     * @return 语义分析结果；不存在时为空
     */
    public Optional<SemanticResult> semanticResultOptional() {
        return Optional.ofNullable(semanticResult);
    }

    /**
     * 返回 IR 模块。
     *
     * @return IR 模块；不存在时为空
     */
    public Optional<IrModule> irModuleOptional() {
        return Optional.ofNullable(irModule);
    }

    /**
     * 返回汇编文本。
     *
     * @return 汇编文本；不存在时为空
     */
    public Optional<AssemblySource> assemblySourceOptional() {
        return Optional.ofNullable(assemblySource);
    }

    /**
     * 聚合所有已执行阶段的诊断。
     *
     * @return 诊断列表
     */
    public List<Diagnostic> diagnostics() {
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(lexResult.diagnostics());
        if (parseResult != null) {
            diagnostics.addAll(parseResult.diagnostics());
        }
        if (semanticResult != null) {
            diagnostics.addAll(semanticResult.diagnostics());
        }
        diagnostics.addAll(toolchainResult.diagnostics());
        return List.copyOf(diagnostics);
    }

    /**
     * 返回编译是否成功生成汇编且无诊断。
     *
     * @return 是否成功
     */
    public boolean succeeded() {
        return diagnostics().isEmpty() && assemblySource != null;
    }
}
