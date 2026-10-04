package craken.compiler.asm;

import craken.compiler.Stage;

import java.util.Objects;

/**
 * Asm 阶段最终结果，也是 Obj 阶段的输入。
 *
 * @param entrySymbol 程序入口符号
 * @param text 完整汇编文本
 */
public record AsmResult(String entrySymbol, String text) implements Stage.Context {
    public AsmResult {
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
