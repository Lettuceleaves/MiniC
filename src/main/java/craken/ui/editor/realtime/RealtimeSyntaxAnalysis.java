package craken.ui.editor.realtime;

import java.util.List;
import java.util.Objects;

/** One real-time analysis result for a frozen source snapshot. */
public record RealtimeSyntaxAnalysis(
        String sourceName,
        String sourceText,
        List<RealtimeDiagnostic> diagnostics,
        long version
) {
    public RealtimeSyntaxAnalysis {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(sourceText, "sourceText");
        diagnostics = List.copyOf(diagnostics);
    }
}
