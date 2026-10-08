package craken.ui.component.editor;

import craken.SourceRange;
import org.fife.ui.rtextarea.ChangeableHighlightPainter;

import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.Position;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** 管理不会改变文档内容的错误波浪线，以及由外部传入的 SourceRange 区域高亮。 */
final class UiEditorDecorations {
    private static final BasicStroke ERROR_STROKE = new BasicStroke(
            1.25f,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
    );

    private final UiCodeEditorTextArea textArea;
    private final ChangeableHighlightPainter rangePainter =
            new ChangeableHighlightPainter(new Color(0xf2cc60), true, 0.28f);
    private final List<Object> rangeTags = new ArrayList<>();
    private List<TrackedRange> errorRanges = List.of();
    private Color errorColor = Color.RED;
    private int highlightedLine = -1;

    UiEditorDecorations(UiCodeEditorTextArea textArea) {
        this.textArea = textArea;
        textArea.setOverlayRenderer(this::paintErrorUnderlines);
    }

    void applyStyle(Color errorUnderline, Color rangeHighlight) {
        errorColor = Objects.requireNonNull(errorUnderline);
        setPainterColor(rangePainter, Objects.requireNonNull(rangeHighlight));
        textArea.repaint();
    }

    void setErrorUnderlines(Collection<SourceRange> ranges) {
        List<OffsetRange> offsets = offsets(ranges);
        try {
            ArrayList<TrackedRange> tracked = new ArrayList<>(offsets.size());
            for (OffsetRange range : offsets) {
                tracked.add(new TrackedRange(
                        textArea.getDocument().createPosition(range.start()),
                        textArea.getDocument().createPosition(range.end())
                ));
            }
            errorRanges = List.copyOf(tracked);
            textArea.repaint();
        } catch (BadLocationException exception) {
            throw new IllegalStateException("validated editor range became invalid", exception);
        }
    }

    void setRangeHighlights(Collection<SourceRange> ranges) {
        replace(rangeTags, ranges, rangePainter);
    }

    /** 高亮整行内容；行号越界或行为空时清除现有区域高亮。 */
    void highlightLine(int oneBasedLine) {
        // 调试器每步都同步当前行：同一行必须是无操作，否则 remove+add 会在 SwingNode 里
        // 堆积局部重绘，编辑器偶尔出现整块未刷新（点击一下才恢复）。
        if (oneBasedLine == highlightedLine) return;
        if (oneBasedLine < 1 || oneBasedLine > textArea.getLineCount()) {
            clearRangeHighlights();
            return;
        }
        int line = oneBasedLine - 1;
        try {
            int start = textArea.getLineStartOffset(line);
            int end = textArea.getLineEndOffset(line);
            String content = textArea.getText(start, end - start);
            while (content.endsWith("\n") || content.endsWith("\r")) {
                content = content.substring(0, content.length() - 1);
            }
            if (content.isEmpty()) {
                clearRangeHighlights();
                return;
            }
            setRangeHighlights(List.of(new SourceRange(
                    oneBasedLine, 0, oneBasedLine,
                    content.getBytes(StandardCharsets.UTF_8).length
            )));
            highlightedLine = oneBasedLine;
            // 整块重绘：双缓冲关闭后，局部重绘在 SwingNode 上可能丢区域，整块重绘最稳。
            textArea.repaint();
        } catch (BadLocationException exception) {
            throw new IllegalStateException("validated editor line became invalid", exception);
        }
    }

    void clearErrorUnderlines() {
        errorRanges = List.of();
        textArea.repaint();
    }

    void clearRangeHighlights() {
        boolean hadHighlight = highlightedLine >= 0;
        highlightedLine = -1;
        remove(rangeTags);
        if (hadHighlight) textArea.repaint();
    }

    void clear() {
        clearErrorUnderlines();
        clearRangeHighlights();
    }

    private void replace(
            List<Object> tags,
            Collection<SourceRange> sourceRanges,
            Highlighter.HighlightPainter painter
    ) {
        List<OffsetRange> offsets = offsets(sourceRanges);

        remove(tags);
        Highlighter highlighter = textArea.getHighlighter();
        try {
            for (OffsetRange range : offsets) {
                tags.add(highlighter.addHighlight(range.start(), range.end(), painter));
            }
        } catch (BadLocationException exception) {
            remove(tags);
            throw new IllegalStateException("validated editor range became invalid", exception);
        }
    }

    private List<OffsetRange> offsets(Collection<SourceRange> sourceRanges) {
        Objects.requireNonNull(sourceRanges, "sourceRanges");
        return List.copyOf(sourceRanges).stream()
                .map(range -> offsetRange(Objects.requireNonNull(range, "sourceRange")))
                .filter(Objects::nonNull)
                .toList();
    }

    private void paintErrorUnderlines(Graphics2D graphics) {
        if (errorRanges.isEmpty()) {
            return;
        }
        graphics.setColor(errorColor);
        graphics.setStroke(ERROR_STROKE);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        for (TrackedRange range : errorRanges) {
            paintErrorUnderline(graphics, range.start().getOffset(), range.end().getOffset());
        }
    }

    private void paintErrorUnderline(Graphics2D graphics, int start, int end) {
        if (start >= end) {
            return;
        }
        try {
            int firstLine = textArea.getLineOfOffset(start);
            int lastLine = textArea.getLineOfOffset(end - 1);
            for (int line = firstLine; line <= lastLine; line++) {
                int contentStart = textArea.getLineStartOffset(line);
                int contentEnd = contentEndOffset(line, contentStart);
                int segmentStart = Math.max(start, contentStart);
                int segmentEnd = Math.min(end, contentEnd);
                if (segmentStart < segmentEnd) {
                    paintWave(graphics, segmentStart, segmentEnd);
                }
            }
        } catch (BadLocationException ignored) {
            // 文档更新与绘制重叠时，下一帧会使用 Position 的新位置重绘。
        }
    }

    private int contentEndOffset(int line, int lineStart) throws BadLocationException {
        int end = textArea.getLineEndOffset(line);
        while (end > lineStart) {
            String lastCharacter = textArea.getText(end - 1, 1);
            if (!lastCharacter.equals("\n") && !lastCharacter.equals("\r")) {
                break;
            }
            end--;
        }
        return end;
    }

    private void paintWave(Graphics2D graphics, int start, int end)
            throws BadLocationException {
        Rectangle2D startBounds = textArea.modelToView2D(start);
        Rectangle2D endBounds = textArea.modelToView2D(end);
        if (startBounds == null || endBounds == null) {
            return;
        }
        int left = (int) Math.round(startBounds.getX());
        int right = Math.max(left + 3, (int) Math.round(endBounds.getX()));
        int y = (int) Math.round(startBounds.getMaxY()) - 2;
        int direction = -2;
        for (int x = left; x < right; x += 2) {
            int nextX = Math.min(x + 2, right);
            graphics.drawLine(x, y, nextX, y + direction);
            y += direction;
            direction = -direction;
        }
    }

    private OffsetRange offsetRange(SourceRange range) {
        try {
            int start = offsetAt(range.startLine(), range.startByte());
            int end = offsetAt(range.endLine(), range.endByte());
            if (end < start) {
                throw new IllegalArgumentException("source range end precedes its start: " + range);
            }
            if (start == end) {
                if (end < textArea.getDocument().getLength()) {
                    end++;
                } else if (start > 0) {
                    start--;
                } else {
                    return null;
                }
            }
            return new OffsetRange(start, end);
        } catch (BadLocationException exception) {
            throw new IllegalArgumentException("source range is outside the editor document: " + range,
                    exception);
        }
    }

    private int offsetAt(int oneBasedLine, int byteIndex) throws BadLocationException {
        if (oneBasedLine < 1 || oneBasedLine > textArea.getLineCount()) {
            throw new IllegalArgumentException("source line is outside the editor: " + oneBasedLine);
        }
        int line = oneBasedLine - 1;
        int lineStart = textArea.getLineStartOffset(line);
        int lineEnd = textArea.getLineEndOffset(line);
        String content = textArea.getText(lineStart, lineEnd - lineStart);
        while (content.endsWith("\n") || content.endsWith("\r")) {
            content = content.substring(0, content.length() - 1);
        }

        int bytes = 0;
        for (int index = 0; index < content.length();) {
            if (bytes == byteIndex) {
                return lineStart + index;
            }
            int codePoint = content.codePointAt(index);
            int encodedLength = utf8Length(codePoint);
            if (bytes + encodedLength > byteIndex) {
                throw new IllegalArgumentException(
                        "source byte index splits a UTF-8 character at line " + oneBasedLine
                                + ": " + byteIndex);
            }
            bytes += encodedLength;
            index += Character.charCount(codePoint);
        }
        if (bytes == byteIndex) {
            return lineStart + content.length();
        }
        throw new IllegalArgumentException(
                "source byte index is outside line " + oneBasedLine + ": " + byteIndex);
    }

    private void remove(List<Object> tags) {
        Highlighter highlighter = textArea.getHighlighter();
        tags.forEach(highlighter::removeHighlight);
        tags.clear();
    }

    private static void setPainterColor(
            ChangeableHighlightPainter painter,
            Color color
    ) {
        painter.setPaint(new Color(color.getRed(), color.getGreen(), color.getBlue()));
        painter.setAlpha(color.getAlpha() / 255f);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7f) {
            return 1;
        }
        if (codePoint <= 0x7ff) {
            return 2;
        }
        if (codePoint <= 0xffff) {
            return 3;
        }
        return 4;
    }

    private record OffsetRange(int start, int end) {
    }

    private record TrackedRange(Position start, Position end) {
    }
}
