package craken.ui.editor.completion;

import java.util.List;
import java.util.Objects;

/** 一次补全计算结果：按展示顺序排列的候选项和光标左侧前缀。 */
public record CompletionResult(List<CompletionCandidate> candidates, CompletionPrefix prefix) {
    public CompletionResult {
        Objects.requireNonNull(prefix, "prefix");
        candidates = List.copyOf(candidates);
    }
}
