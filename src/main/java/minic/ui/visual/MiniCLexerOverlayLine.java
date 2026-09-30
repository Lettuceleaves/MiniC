package minic.ui;

import java.util.List;

/**
 * Lexer 遮罩视图中的一行。
 *
 * @param lineNumber 行号
 * @param segments 行内片段
 */
public record MiniCLexerOverlayLine(int lineNumber, List<MiniCLexerOverlaySegment> segments) {
    public MiniCLexerOverlayLine {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be 1-based");
        }
        segments = List.copyOf(segments);
    }
}
