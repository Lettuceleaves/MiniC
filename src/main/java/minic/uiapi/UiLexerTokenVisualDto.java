package minic.uiapi;

import java.util.Objects;

/**
 * Lexer 阶段 token 图形数据。
 *
 * @param kind token 类型
 * @param text token 原始文本
 * @param range 源码范围；尚未细化时可为 {@code null}
 * @param startOffset 起始 offset
 * @param endOffset 结束 offset
 * @param startLine 起始行
 * @param startColumn 起始列
 * @param endLine 结束行
 * @param endColumn 结束列
 * @param active 是否为当前 token
 */
public record UiLexerTokenVisualDto(
        String kind,
        String text,
        UiSourceSpanDto range,
        int startOffset,
        int endOffset,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn,
        boolean active
) {
    public UiLexerTokenVisualDto {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
    }

    public UiLexerTokenVisualDto(String kind, String text, UiSourceSpanDto range, boolean active) {
        this(
                kind,
                text,
                range,
                range == null ? -1 : range.startOffset(),
                range == null ? -1 : range.endOffset(),
                range == null ? -1 : range.startLine(),
                range == null ? -1 : range.startColumn(),
                range == null ? -1 : range.endLine(),
                range == null ? -1 : range.endColumn(),
                active
        );
    }
}
