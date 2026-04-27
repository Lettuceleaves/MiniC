package minic.source;

import java.util.Objects;

/**
 * 表示源码中的半开区间 {@code [startOffset, endOffset)}。
 *
 * @param sourceFile 区间所属源码文件
 * @param startOffset 起始 offset，包含
 * @param endOffset 结束 offset，不包含
 */
public record SourceRange(SourceFile sourceFile, int startOffset, int endOffset) {
    /**
     * 创建源码区间。
     *
     * @param sourceFile 区间所属源码文件
     * @param startOffset 起始 offset，包含
     * @param endOffset 结束 offset，不包含
     */
    public SourceRange {
        Objects.requireNonNull(sourceFile, "sourceFile");
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must be non-negative");
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must be greater than or equal to startOffset");
        }
        if (endOffset > sourceFile.content().length()) {
            throw new IllegalArgumentException("endOffset out of bounds: " + endOffset);
        }
    }

    /**
     * 返回区间起点位置。
     *
     * @return 起点源码位置
     */
    public SourcePosition startPosition() {
        return sourceFile.positionAt(startOffset);
    }

    /**
     * 返回区间终点位置。
     *
     * @return 终点源码位置
     */
    public SourcePosition endPosition() {
        return sourceFile.positionAt(endOffset);
    }

    /**
     * 返回该半开区间覆盖的源码文本。
     *
     * @return 区间文本
     */
    public String text() {
        return sourceFile.content().substring(startOffset, endOffset);
    }
}
