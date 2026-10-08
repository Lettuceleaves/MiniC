package craken.ui.editor.completion;

import java.util.Objects;

/** 光标左侧待替换的前缀；startOffset/endOffset 是源码 Java 字符下标的半开区间。 */
public record CompletionPrefix(
        int startOffset,
        int endOffset,
        String text,
        CompletionContext context
) {
    public CompletionPrefix {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(context, "context");
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException(
                    "invalid prefix range: " + startOffset + ".." + endOffset);
        }
    }
}
