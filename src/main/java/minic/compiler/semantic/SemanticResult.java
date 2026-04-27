package minic.compiler.semantic;

import minic.diagnostics.Diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * 语义分析结果。
 *
 * @param globalScope 全局函数作用域
 * @param diagnostics 语义诊断列表
 */
public record SemanticResult(Scope globalScope, List<Diagnostic> diagnostics) {
    /**
     * 创建语义分析结果，并防御性复制诊断列表。
     *
     * @param globalScope 全局函数作用域
     * @param diagnostics 语义诊断列表
     */
    public SemanticResult {
        Objects.requireNonNull(globalScope, "globalScope");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }
}
