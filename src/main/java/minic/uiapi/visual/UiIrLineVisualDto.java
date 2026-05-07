package minic.uiapi;

import java.util.Objects;

/**
 * IR 文本行图形数据。
 *
 * @param lineNumber 行号
 * @param text IR 行文本
 * @param range 对应源码范围；没有直接对应时为 {@code null}
 * @param active 是否为当前 codegen 正在消费的 IR 行
 */
public record UiIrLineVisualDto(int lineNumber, String text, UiSourceSpanDto range, boolean active) {
    public UiIrLineVisualDto {
        Objects.requireNonNull(text, "text");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be 1-based");
        }
    }
}
