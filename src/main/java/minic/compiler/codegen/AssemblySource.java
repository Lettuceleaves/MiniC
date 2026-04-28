package minic.compiler.codegen;

import minic.compiler.codegen.target.TargetPlatform;

import java.util.Objects;

/**
 * 汇编文本输出模型。
 *
 * @param targetPlatform 目标平台
 * @param entrySymbol 入口符号名称
 * @param text 汇编文本
 */
public record AssemblySource(TargetPlatform targetPlatform, String entrySymbol, String text) {
    /**
     * 创建汇编文本输出模型。
     *
     * @param targetPlatform 目标平台
     * @param entrySymbol 入口符号名称
     * @param text 汇编文本
     */
    public AssemblySource {
        Objects.requireNonNull(targetPlatform, "targetPlatform");
        Objects.requireNonNull(entrySymbol, "entrySymbol");
        Objects.requireNonNull(text, "text");
        if (entrySymbol.isBlank()) {
            throw new IllegalArgumentException("entrySymbol must not be blank");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }
}
