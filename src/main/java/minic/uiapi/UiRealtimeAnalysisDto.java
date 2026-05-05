package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * 实时编辑分析结果。
 *
 * @param sourceName 源码名称
 * @param sourceText 源码文本
 * @param diagnostics 实时 diagnostics
 * @param tokens lexer tokens
 * @param version 分析版本
 */
public record UiRealtimeAnalysisDto(
        String sourceName,
        String sourceText,
        List<UiDiagnosticDto> diagnostics,
        List<UiLexerTokenVisualDto> tokens,
        long version
) {
    public UiRealtimeAnalysisDto {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(tokens, "tokens");
        diagnostics = List.copyOf(diagnostics);
        tokens = List.copyOf(tokens);
    }
}
