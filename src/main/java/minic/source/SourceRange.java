package minic.source;

/**
 * IDE 中的源码半开区间。
 *
 * <p>行号从 1 开始；字节下标从 0 开始，并按 UTF-8 编码计算。结束位置不包含
 * 在范围内。该对象只描述位置，不持有源码文本或文件身份。</p>
 *
 * @param startLine 起始行号
 * @param startByte 起始行内 UTF-8 字节下标
 * @param endLine 结束行号
 * @param endByte 结束行内 UTF-8 字节下标
 */
public record SourceRange(int startLine, int startByte, int endLine, int endByte) {
    public SourceRange {
        if (startLine < 1 || endLine < 1) {
            throw new IllegalArgumentException("source lines must be 1-based");
        }
        if (startByte < 0 || endByte < 0) {
            throw new IllegalArgumentException("source byte indexes must not be negative");
        }
        if (endLine < startLine || (endLine == startLine && endByte < startByte)) {
            throw new IllegalArgumentException("range end must not precede range start");
        }
    }

    /** 返回从 first 起点到 last 终点的范围。 */
    public static SourceRange span(SourceRange first, SourceRange last) {
        if (first == null || last == null) {
            throw new NullPointerException("source ranges must not be null");
        }
        return new SourceRange(first.startLine, first.startByte, last.endLine, last.endByte);
    }

    /** 返回当前范围是否完整包含另一个范围。 */
    public boolean contains(SourceRange other) {
        return compare(startLine, startByte, other.startLine, other.startByte) <= 0
                && compare(endLine, endByte, other.endLine, other.endByte) >= 0;
    }

    /** 返回两个范围是否相交。 */
    public boolean overlaps(SourceRange other) {
        return compare(startLine, startByte, other.endLine, other.endByte) < 0
                && compare(other.startLine, other.startByte, endLine, endByte) < 0;
    }

    private static int compare(int leftLine, int leftByte, int rightLine, int rightByte) {
        int lineComparison = Integer.compare(leftLine, rightLine);
        return lineComparison != 0 ? lineComparison : Integer.compare(leftByte, rightByte);
    }
}
