package minic.compiler.stage;

import minic.compiler.codegen.AssemblySource;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.parser.ParseResult;
import minic.compiler.semantic.SemanticResult;

/**
 * 现有一次性编译 API 结果的兼容输出数据。
 *
 * @param lexResult 词法结果
 * @param parseResult 语法结果
 * @param semanticResult 语义结果
 * @param irModule IR 模块
 * @param assemblySource 汇编文本
 */
public record ExistingCompilerOutputs(
        LexResult lexResult,
        ParseResult parseResult,
        SemanticResult semanticResult,
        IrModule irModule,
        AssemblySource assemblySource
) implements CompilerStageOutput {
}
