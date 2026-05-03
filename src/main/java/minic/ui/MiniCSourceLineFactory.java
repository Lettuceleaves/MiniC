package minic.ui;

import minic.uiapi.UiSourceRangeDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 把源码文本和源码范围转换为可显示的行数据。
 */
public final class MiniCSourceLineFactory {
    /**
     * 创建源码行数据。
     *
     * @param source 源码文本
     * @param range 当前源码范围；没有时为 {@code null}
     * @return 源码行
     */
    public List<MiniCSourceLine> create(String source, UiSourceRangeDto range) {
        String safeSource = source == null ? "" : source;
        String[] lines = safeSource.split("\\R", -1);
        List<MiniCSourceLine> result = new ArrayList<>();
        int offset = 0;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int lineStart = offset;
            int lineEnd = lineStart + line.length();
            boolean focused = intersects(lineStart, lineEnd, range);
            result.add(new MiniCSourceLine(index + 1, line, focused));
            offset = lineEnd + newlineWidth(safeSource, lineEnd);
        }
        if (result.isEmpty()) {
            result.add(new MiniCSourceLine(1, "", false));
        }
        return List.copyOf(result);
    }

    private boolean intersects(int lineStart, int lineEnd, UiSourceRangeDto range) {
        if (range == null) {
            return false;
        }
        if (range.startOffset() == range.endOffset()) {
            return range.startOffset() >= lineStart && range.startOffset() <= lineEnd;
        }
        return range.startOffset() <= lineEnd && range.endOffset() >= lineStart;
    }

    private int newlineWidth(String source, int lineEnd) {
        if (lineEnd >= source.length()) {
            return 0;
        }
        if (source.charAt(lineEnd) == '\r' && lineEnd + 1 < source.length() && source.charAt(lineEnd + 1) == '\n') {
            return 2;
        }
        return 1;
    }
}
