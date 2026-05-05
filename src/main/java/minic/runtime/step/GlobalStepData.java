package minic.runtime.step;

import minic.diagnostics.Diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * 全局数据区。
 *
 * @param source 源码文本
 * @param stageSummaries 所有阶段摘要
 * @param diagnostics 全量 diagnostics
 * @param tokenSummary token 摘要
 * @param astSummary AST 摘要
 * @param semanticSummary semantic 摘要
 * @param irSummary IR 摘要
 * @param assemblySummary assembly 摘要
 * @param artifactSummary artifact 摘要
 * @param executionInputSummary 运行输入摘要
 * @param executionOutputSummary 运行输出摘要
 */
public record GlobalStepData(
        String source,
        List<String> stageSummaries,
        List<Diagnostic> diagnostics,
        List<String> tokenSummary,
        List<String> astSummary,
        List<String> semanticSummary,
        List<String> irSummary,
        List<String> assemblySummary,
        List<String> artifactSummary,
        List<String> executionInputSummary,
        List<String> executionOutputSummary
) {
    /**
     * 创建全局数据区，并防御性复制集合。
     *
     * @param source 源码文本
     * @param stageSummaries 所有阶段摘要
     * @param diagnostics 全量 diagnostics
     * @param tokenSummary token 摘要
     * @param astSummary AST 摘要
     * @param semanticSummary semantic 摘要
     * @param irSummary IR 摘要
     * @param assemblySummary assembly 摘要
     * @param artifactSummary artifact 摘要
     * @param executionInputSummary 运行输入摘要
     * @param executionOutputSummary 运行输出摘要
     */
    public GlobalStepData {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(stageSummaries, "stageSummaries");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(tokenSummary, "tokenSummary");
        Objects.requireNonNull(astSummary, "astSummary");
        Objects.requireNonNull(semanticSummary, "semanticSummary");
        Objects.requireNonNull(irSummary, "irSummary");
        Objects.requireNonNull(assemblySummary, "assemblySummary");
        Objects.requireNonNull(artifactSummary, "artifactSummary");
        Objects.requireNonNull(executionInputSummary, "executionInputSummary");
        Objects.requireNonNull(executionOutputSummary, "executionOutputSummary");
        stageSummaries = List.copyOf(stageSummaries);
        diagnostics = List.copyOf(diagnostics);
        tokenSummary = List.copyOf(tokenSummary);
        astSummary = List.copyOf(astSummary);
        semanticSummary = List.copyOf(semanticSummary);
        irSummary = List.copyOf(irSummary);
        assemblySummary = List.copyOf(assemblySummary);
        artifactSummary = List.copyOf(artifactSummary);
        executionInputSummary = List.copyOf(executionInputSummary);
        executionOutputSummary = List.copyOf(executionOutputSummary);
    }
}
