package minic.ui.text;

import minic.compiler.lexer.Lexer;
import minic.compiler.lexer.Token;
import minic.source.SourceFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight highlighter for MiniC/C source snippets.
 */
public final class MiniCSourceTextHighlighter {
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
        List<Token> tokens = new Lexer(new SourceFile("guide-code.mc", source)).lex().tokens().stream()
                .filter(token -> !"EOF".equals(token.kind().name()))
                .filter(token -> token.range().endOffset() > token.range().startOffset())
                .sorted(Comparator.comparingInt(token -> token.range().startOffset()))
                .toList();
        if (tokens.isEmpty()) {
            return List.of(new MiniCStyledTextSegment(source, MiniCTextStyleRole.CODE_PLAIN));
        }
        ArrayList<MiniCStyledTextSegment> segments = new ArrayList<>();
        int cursor = 0;
        for (Token token : tokens) {
            int start = safeOffset(source, token.range().startOffset());
            int end = safeOffset(source, token.range().endOffset());
            if (end <= start || start < cursor) {
                continue;
            }
            if (start > cursor) {
                MiniCLineTokenHighlighter.add(segments, source.substring(cursor, start), MiniCTextStyleRole.CODE_PLAIN);
            }
            MiniCLineTokenHighlighter.add(segments, source.substring(start, end), styleMapper.roleFor(token.kind().name()));
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
