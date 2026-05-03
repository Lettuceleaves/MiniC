package minic.ui;

import java.util.Objects;

/**
 * Assembly 文本视图中的一行。
 *
 * @param lineNumber 行号
 * @param text 汇编文本
 * @param section section 元信息
 * @param label label 元信息
 * @param active 是否当前行
 */
public record MiniCAssemblyTextLine(int lineNumber, String text, String section, String label, boolean active) {
    public MiniCAssemblyTextLine {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(label, "label");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be 1-based");
        }
    }
}
