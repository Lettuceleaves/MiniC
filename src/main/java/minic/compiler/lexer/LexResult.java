package minic.compiler.lexer;

import minic.diagnostics.Diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * 表示一次词法分析的结果。
 *
 * @param tokens 产出的 token 列表
 * @param diagnostics 词法诊断列表
 */
public record LexResult(List<Token> tokens, List<Diagnostic> diagnostics) {
    /**
     * 创建词法分析结果，并防御性复制列表。
     *
     * @param tokens 产出的 token 列表
     * @param diagnostics 词法诊断列表
     */
    public LexResult {
        Objects.requireNonNull(tokens, "tokens");
        Objects.requireNonNull(diagnostics, "diagnostics");
        tokens = List.copyOf(tokens);
        diagnostics = List.copyOf(diagnostics);
    }
}
