package minic.compiler.codegen.windows;

import java.util.Objects;

/**
 * Windows x64 codegen 单步产出的汇编行。
 *
 * @param kind 行类型
 * @param subject 行所属主题
 * @param text 汇编行文本，不包含换行符
 */
public record WindowsX64AssemblyLine(WindowsX64AssemblyLineKind kind, String subject, String text) {
    /**
     * 创建汇编行。
     *
     * @param kind 行类型
     * @param subject 行所属主题
     * @param text 汇编行文本
     */
    public WindowsX64AssemblyLine {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(text, "text");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }
}
