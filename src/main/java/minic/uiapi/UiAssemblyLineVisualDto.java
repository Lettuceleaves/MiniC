package minic.uiapi;

import java.util.Objects;

/**
 * Codegen 阶段汇编行图形数据。
 *
 * @param lineNumber 稳定行号
 * @param text 汇编文本或当前摘要
 * @param kind 行类型
 * @param section section 元信息；未知时为空字符串
 * @param label label 元信息；未知时为空字符串
 * @param active 是否为当前行
 */
public record UiAssemblyLineVisualDto(
        int lineNumber,
        String text,
        String kind,
        String section,
        String label,
        boolean active
) {
    public UiAssemblyLineVisualDto {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(label, "label");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be 1-based");
        }
    }
}
