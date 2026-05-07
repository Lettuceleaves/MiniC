package minic.ui;

import java.util.Objects;

/**
 * Lexer 遮罩视图中的一段源码文本。
 *
 * @param text 源码文本片段
 * @param active 是否覆盖当前 token
 */
public record MiniCLexerOverlaySegment(String text, boolean active) {
    public MiniCLexerOverlaySegment {
        Objects.requireNonNull(text, "text");
    }
}
