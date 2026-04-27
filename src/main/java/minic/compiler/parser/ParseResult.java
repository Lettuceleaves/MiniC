package minic.compiler.parser;

import minic.compiler.ast.Program;
import minic.diagnostics.Diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * 表示一次语法分析的结果。
 *
 * @param program 解析得到的程序 AST
 * @param diagnostics 语法诊断列表
 */
public record ParseResult(Program program, List<Diagnostic> diagnostics) {
    /**
     * 创建语法分析结果，并防御性复制诊断列表。
     *
     * @param program 解析得到的程序 AST
     * @param diagnostics 语法诊断列表
     */
    public ParseResult {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }
}
