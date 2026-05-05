package minic.uiapi;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * UI 使用的源码半开区间，包含 offset 和 1-based 行列。
 *
 * @param sourceName 源码名称
 * @param startOffset 起始 offset
 * @param endOffset 结束 offset
 * @param startLine 起始行
 * @param startColumn 起始列
 * @param endLine 结束行
 * @param endColumn 结束列
 */
public record UiSourceSpanDto(
        String sourceName,
        int startOffset,
        int endOffset,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn
) {
    public UiSourceSpanDto {
        Objects.requireNonNull(sourceName, "sourceName");
    }

    public static UiSourceSpanDto from(SourceRange range) {
        return new UiSourceSpanDto(
                range.sourceFile().path(),
                range.startOffset(),
                range.endOffset(),
                range.startPosition().line(),
                range.startPosition().column(),
                range.endPosition().line(),
                range.endPosition().column()
        );
    }
}
