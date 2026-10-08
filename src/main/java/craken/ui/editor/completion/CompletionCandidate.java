package craken.ui.editor.completion;

import java.util.Objects;

/** 一条补全候选项；text 是插入源码的文本，kind 描述它来自关键词、变量还是头文件。 */
public record CompletionCandidate(String text, CompletionKind kind) {
    public CompletionCandidate {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
        if (text.isBlank()) {
            throw new IllegalArgumentException("completion text must not be blank");
        }
    }
}
