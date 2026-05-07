package minic.uiapi;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * UI 源码范围摘要。
 *
 * @param sourceName 源码名称
 * @param startOffset 起始 offset
 * @param endOffset 结束 offset
 */
public record UiSourceRangeDto(String sourceName, int startOffset, int endOffset) {
    public UiSourceRangeDto {
        Objects.requireNonNull(sourceName, "sourceName");
    }

    static UiSourceRangeDto from(SourceRange range) {
        return new UiSourceRangeDto(
                range.sourceFile().path(),
                range.startOffset(),
                range.endOffset()
        );
    }
}
