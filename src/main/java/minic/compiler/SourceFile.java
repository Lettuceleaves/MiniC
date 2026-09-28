package minic.compiler;

import minic.source.SourceRange;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 一次编译使用的源码输入。
 *
 * <p>源码文本和路径属于 Pipeline 输入，不属于源码位置。该类型负责在 Java
 * 字符偏移和 IDE 使用的“1-based 行号 + 0-based UTF-8 字节下标”之间转换。</p>
 *
 * @param path 源码路径或显示名称
 * @param content 源码完整内容
 */
public record SourceFile(String path, String content) {
    public SourceFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
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
        for (int offset = lineStart; offset <= lineEnd; offset++) {
            int bytes = utf8Length(content, lineStart, offset);
            if (bytes == byteIndex) {
                return offset;
            }
            if (bytes > byteIndex) {
                break;
            }
        }
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
        int line = 1;
        int lineStart = 0;
        for (int index = 0; index < offset; index++) {
            if (content.charAt(index) == '\n') {
                line++;
                lineStart = index + 1;
            }
        }
        return new Position(line, utf8Length(content, lineStart, offset));
    }

    private static int utf8Length(String value, int start, int end) {
        return value.substring(start, end).getBytes(StandardCharsets.UTF_8).length;
    }

    private record Position(int line, int byteIndex) {
    }
}
