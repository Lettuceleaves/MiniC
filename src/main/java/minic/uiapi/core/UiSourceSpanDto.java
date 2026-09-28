package minic.uiapi;

import minic.compiler.SourceFile;
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

    public static UiSourceSpanDto from(SourceFile sourceFile, SourceRange range) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(range, "range");
        return new UiSourceSpanDto(
                sourceFile.path(),
                sourceFile.offsetAt(range.startLine(), range.startByte()),
                sourceFile.offsetAt(range.endLine(), range.endByte()),
                range.startLine(),
                range.startByte() + 1,
                range.endLine(),
                range.endByte() + 1
        );
    }
}
