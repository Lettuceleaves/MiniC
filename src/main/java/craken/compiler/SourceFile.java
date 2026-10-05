package craken.compiler;

import craken.SourceRange;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * 一次编译使用的源码输入。
 *
 * <p>源码文本和路径属于 Pipeline 输入，不属于源码位置。该类型负责在 Java
 * 字符偏移和 IDE 使用的“1-based 行号 + 0-based UTF-8 字节下标”之间转换。</p>
 *
 * <p>行首偏移在首次换算位置时建立并缓存，逐记号换算位置不再重复扫描整个文件。</p>
 */
public final class SourceFile {
    private final String path;
    private final String content;
    private int[] lineStarts;
    private int[] byteOffsets;

    /**
     * @param path 源码路径或显示名称
     * @param content 源码完整内容
     */
    public SourceFile(String path, String content) {
        this.path = Objects.requireNonNull(path, "path");
        this.content = Objects.requireNonNull(content, "content");
    }

    public String path() {
        return path;
    }

    public String content() {
        return content;
    }

    /** 将 Java 字符半开区间转换为 IDE 源码范围。 */
    public SourceRange range(int startOffset, int endOffset) {
        if (startOffset < 0 || endOffset < startOffset || endOffset > content.length()) {
            throw new IllegalArgumentException("source offsets out of bounds: " + startOffset + ".." + endOffset);
        }
        Position start = positionAt(startOffset);
        Position end = positionAt(endOffset);
        return new SourceRange(start.line, start.byteIndex, end.line, end.byteIndex);
    }

    /** 返回 IDE 位置对应的 Java 字符偏移。 */
    public int offsetAt(int line, int byteIndex) {
        if (line < 1) {
            throw new IllegalArgumentException("line must be 1-based");
        }
        if (byteIndex < 0) {
            throw new IllegalArgumentException("byteIndex must not be negative");
        }
        int currentLine = 1;
        int lineStart = 0;
        while (currentLine < line) {
            int newline = content.indexOf('\n', lineStart);
            if (newline < 0) {
                throw new IllegalArgumentException("line out of bounds: " + line);
            }
            lineStart = newline + 1;
            currentLine++;
        }
        int lineEnd = content.indexOf('\n', lineStart);
        if (lineEnd < 0) {
            lineEnd = content.length();
        }
        int[] bytes = byteOffsets();
        int base = bytes[lineStart];
        int low = lineStart, high = lineEnd;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (bytes[middle] - base < byteIndex) low = middle + 1;
            else high = middle;
        }
        if (bytes[low] - base == byteIndex) return low;
        throw new IllegalArgumentException("byteIndex is not a UTF-8 character boundary on line " + line);
    }

    /** 返回 IDE 范围对应的源码文本。 */
    public String text(SourceRange range) {
        Objects.requireNonNull(range, "range");
        return content.substring(
                offsetAt(range.startLine(), range.startByte()),
                offsetAt(range.endLine(), range.endByte())
        );
    }

    private Position positionAt(int offset) {
        int[] starts = lineStarts();
        int index = Arrays.binarySearch(starts, offset);
        // 未命中时 binarySearch 返回 -(插入点)-1；所在行是插入点的前一行。
        int lineIndex = index >= 0 ? index : -index - 2;
        int[] bytes = byteOffsets();
        return new Position(lineIndex + 1, bytes[offset] - bytes[starts[lineIndex]]);
    }

    private int[] lineStarts() {
        int[] starts = lineStarts;
        if (starts == null) {
            int count = 1;
            for (int index = 0; index < content.length(); index++) {
                if (content.charAt(index) == '\n') count++;
            }
            starts = new int[count];
            int line = 1;
            for (int index = 0; index < content.length(); index++) {
                if (content.charAt(index) == '\n') starts[line++] = index + 1;
            }
            lineStarts = starts;
        }
        return starts;
    }

    /** UTF-8 byte length of content[0..i] per character boundary; built once and reused for every range. */
    private int[] byteOffsets() {
        int[] bytes = byteOffsets;
        if (bytes == null) {
            bytes = new int[content.length() + 1];
            int total = 0;
            for (int index = 0; index < content.length(); index++) {
                bytes[index] = total;
                char character = content.charAt(index);
                if (Character.isHighSurrogate(character) && index + 1 < content.length()
                        && Character.isLowSurrogate(content.charAt(index + 1))) {
                    int pairStart = bytes[index];
                    index++;
                    // The second half of a surrogate pair is not a UTF-8 boundary itself.
                    bytes[index] = pairStart;
                    total += 4;
                } else if (character <= 0x7F) {
                    total += 1;
                } else if (character <= 0x7FF) {
                    total += 2;
                } else {
                    total += 3;
                }
            }
            bytes[content.length()] = total;
            byteOffsets = bytes;
        }
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SourceFile source && path.equals(source.path) && content.equals(source.content);
    }

    @Override
    public int hashCode() {
        return 31 * path.hashCode() + content.hashCode();
    }

    @Override
    public String toString() {
        return "SourceFile[path=" + path + ", content=" + content + "]";
    }

    private record Position(int line, int byteIndex) {
    }
}
