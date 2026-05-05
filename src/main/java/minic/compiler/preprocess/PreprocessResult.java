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
 * @param sourceMap 预编译产物 offset 到原始源码 offset 的映射；与 {@code sourceFile.content()} 等长
 */
public record PreprocessResult(
        SourceFile sourceFile,
        List<Diagnostic> diagnostics,
        List<IncludeSummary> includes,
        List<MacroSummary> macros,
        int[] sourceMap
) {
    /**
     * 创建预编译结果。
     *
     * @param sourceFile 预编译后的源码
     * @param diagnostics 预编译诊断
     * @param includes include 摘要
     * @param macros 宏摘要
     * @param sourceMap 预编译产物 offset 到原始源码 offset 的映射
     */
    public PreprocessResult {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(includes, "includes");
        Objects.requireNonNull(macros, "macros");
        Objects.requireNonNull(sourceMap, "sourceMap");
        if (sourceMap.length != sourceFile.content().length()) {
            throw new IllegalArgumentException("sourceMap length must match preprocessed source length");
        }
        diagnostics = List.copyOf(diagnostics);
        includes = List.copyOf(includes);
        macros = List.copyOf(macros);
        sourceMap = sourceMap.clone();
    }

    public PreprocessResult(
            SourceFile sourceFile,
            List<Diagnostic> diagnostics,
            List<IncludeSummary> includes,
            List<MacroSummary> macros
    ) {
        this(sourceFile, diagnostics, includes, macros, identityMap(sourceFile.content().length()));
    }

    @Override
    public int[] sourceMap() {
        return sourceMap.clone();
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

    private static int[] identityMap(int length) {
        int[] sourceMap = new int[length];
        for (int index = 0; index < sourceMap.length; index++) {
            sourceMap[index] = index;
        }
        return sourceMap;
    }
}
