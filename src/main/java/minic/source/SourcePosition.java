package minic.source;

/**
 * 表示源码中的一个位置，line 和 column 均从 1 开始。
 *
 * @param offset 源码内容中的 0-based offset
 * @param line 1-based 行号
 * @param column 1-based 列号
 */
public record SourcePosition(int offset, int line, int column) {
    /**
     * 创建源码位置。
     *
     * @param offset 源码内容中的 0-based offset
     * @param line 1-based 行号
     * @param column 1-based 列号
     */
    public SourcePosition {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be 1-based");
        }
        if (column < 1) {
            throw new IllegalArgumentException("column must be 1-based");
        }
    }
}
