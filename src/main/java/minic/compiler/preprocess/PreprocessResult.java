package minic.compiler.preprocess;

import minic.diagnostics.Diagnostic;
import minic.source.SourceFile;

import java.util.List;
import java.util.Objects;

/**
 * 一次预编译阶段的结果。
 *
 * @param sourceFile 预编译后的源码
 * @param diagnostics 预编译诊断
 * @param includes include 摘要
 * @param macros 宏摘要
 */
public record PreprocessResult(
        SourceFile sourceFile,
        List<Diagnostic> diagnostics,
        List<IncludeSummary> includes,
        List<MacroSummary> macros
) {
    /**
     * 创建预编译结果。
     *
     * @param sourceFile 预编译后的源码
     * @param diagnostics 预编译诊断
     * @param includes include 摘要
     * @param macros 宏摘要
     */
    public PreprocessResult {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(includes, "includes");
        Objects.requireNonNull(macros, "macros");
        diagnostics = List.copyOf(diagnostics);
        includes = List.copyOf(includes);
        macros = List.copyOf(macros);
    }

    /**
     * 创建直通预编译结果。
     *
     * @param sourceFile 原始源码
     * @return 预编译结果
     */
    public static PreprocessResult passthrough(SourceFile sourceFile) {
        return new PreprocessResult(sourceFile, List.of(), List.of(), List.of());
    }
}
