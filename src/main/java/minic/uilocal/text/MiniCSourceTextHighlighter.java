package minic.uilocal.text;

import minic.uiapi.MiniCRealtimeAnalysisApi;
import minic.uiapi.UiLexerTokenVisualDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight highlighter for MiniC/C source snippets.
 */
public final class MiniCSourceTextHighlighter {
    private final MiniCRealtimeAnalysisApi api = new MiniCRealtimeAnalysisApi();
    private final MiniCSyntaxTextStyleMapper styleMapper = new MiniCSyntaxTextStyleMapper();

    public List<MiniCStyledTextSegment> highlight(String source) {
        String text = source == null || source.isEmpty() ? " " : source;
        try {
            return highlightTokens(text);
        } catch (RuntimeException ignored) {
            return List.of(new MiniCStyledTextSegment(text, MiniCTextStyleRole.CODE_PLAIN));
        }
    }

    private List<MiniCStyledTextSegment> highlightTokens(String source) {
        List<UiLexerTokenVisualDto> tokens = api.tokenize("guide-code.mc", source).stream()
                .filter(token -> !"EOF".equals(token.kind()))
                .filter(token -> token.endOffset() > token.startOffset())
                .sorted(Comparator.comparingInt(UiLexerTokenVisualDto::startOffset))
                .toList();
        if (tokens.isEmpty()) {
            return List.of(new MiniCStyledTextSegment(source, MiniCTextStyleRole.CODE_PLAIN));
        }
        ArrayList<MiniCStyledTextSegment> segments = new ArrayList<>();
        int cursor = 0;
        for (int index = 0; index < tokens.size(); index++) {
            UiLexerTokenVisualDto token = tokens.get(index);
            int start = safeOffset(source, token.startOffset());
            int end = safeOffset(source, token.endOffset());
            if (end <= start || start < cursor) {
                continue;
            }
            if (start > cursor) {
                MiniCLineTokenHighlighter.add(segments, source.substring(cursor, start), MiniCTextStyleRole.CODE_PLAIN);
            }
            MiniCLineTokenHighlighter.add(segments, source.substring(start, end), styleMapper.roleForToken(tokens, index));
            cursor = end;
        }
        if (cursor < source.length()) {
            MiniCLineTokenHighlighter.add(segments, source.substring(cursor), MiniCTextStyleRole.CODE_PLAIN);
        }
        return List.copyOf(segments);
    }

    private static int safeOffset(String source, int offset) {
        return Math.max(0, Math.min(offset, source.length()));
    }
}
