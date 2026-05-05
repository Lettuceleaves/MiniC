package minic.ui;

import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiStageVisualDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据 Lexer visual DTO 生成等宽源码遮罩模型。
 */
public final class MiniCLexerOverlayModelFactory {
    /**
     * 创建 lexer 遮罩行模型。
     *
     * @param source 源码文本
     * @param visual 当前阶段 visual DTO
     * @return 行模型
     */
    public List<MiniCLexerOverlayLine> create(String source, UiStageVisualDto visual) {
        UiLexerTokenVisualDto activeToken = visual.lexerTokens().stream()
                .filter(UiLexerTokenVisualDto::active)
                .findFirst()
                .orElse(null);
        ArrayList<MiniCLexerOverlayLine> rows = new ArrayList<>();
        int lineStart = 0;
        int lineNumber = 1;
        for (int offset = 0; offset <= source.length(); offset++) {
            if (offset == source.length() || source.charAt(offset) == '\n') {
                rows.add(new MiniCLexerOverlayLine(lineNumber, segments(source, lineStart, offset, activeToken)));
                lineNumber++;
                lineStart = offset + 1;
            }
        }
        if (rows.isEmpty()) {
            rows.add(new MiniCLexerOverlayLine(1, List.of(new MiniCLexerOverlaySegment("", false))));
        }
        return List.copyOf(rows);
    }

    private List<MiniCLexerOverlaySegment> segments(String source, int lineStart, int lineEnd, UiLexerTokenVisualDto activeToken) {
        int activeStart = activeToken == null ? -1 : activeToken.startOffset();
        int activeEnd = activeToken == null ? -1 : activeToken.endOffset();
        if (activeToken == null || activeEnd <= lineStart || activeStart >= lineEnd) {
            return List.of(new MiniCLexerOverlaySegment(source.substring(lineStart, lineEnd), false));
        }

        ArrayList<MiniCLexerOverlaySegment> segments = new ArrayList<>();
        int beforeEnd = Math.max(lineStart, Math.min(activeStart, lineEnd));
        int hotStart = Math.max(lineStart, activeStart);
        int hotEnd = Math.max(hotStart, Math.min(activeEnd, lineEnd));
        if (beforeEnd > lineStart) {
            segments.add(new MiniCLexerOverlaySegment(source.substring(lineStart, beforeEnd), false));
        }
        segments.add(new MiniCLexerOverlaySegment(source.substring(hotStart, hotEnd), true));
        if (lineEnd > hotEnd) {
            segments.add(new MiniCLexerOverlaySegment(source.substring(hotEnd, lineEnd), false));
        }
        return List.copyOf(segments);
    }
}
