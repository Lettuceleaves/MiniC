package minic.compiler.codegen.windows;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * Windows x64 codegen 单步产出的汇编行。
 *
 * @param kind 行类型
 * @param subject 行所属主题
 * @param text 汇编行文本，不包含换行符
 * @param sourceRange 对应 IR 指令的源码范围；没有直接对应时为 {@code null}
 */
public record WindowsX64AssemblyLine(WindowsX64AssemblyLineKind kind, String subject, String text, SourceRange sourceRange) {
    /**
     * 创建汇编行。
     *
     * @param kind 行类型
     * @param subject 行所属主题
     * @param text 汇编行文本
     * @param sourceRange 对应 IR 指令的源码范围；没有直接对应时为 {@code null}
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

    public WindowsX64AssemblyLine(WindowsX64AssemblyLineKind kind, String subject, String text) {
        this(kind, subject, text, null);
    }
}
