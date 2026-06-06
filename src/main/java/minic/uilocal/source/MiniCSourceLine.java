package minic.uilocal;

import java.util.Objects;

/**
 * 源码视图中的一行。
 *
 * @param lineNumber 行号
 * @param text 行文本
 * @param focused 是否处于当前源码范围
 */
public record MiniCSourceLine(int lineNumber, String text, boolean focused) {
    public MiniCSourceLine {
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
        Objects.requireNonNull(text, "text");
    }
}
