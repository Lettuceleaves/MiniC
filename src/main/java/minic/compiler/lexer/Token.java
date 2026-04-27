package minic.compiler.lexer;

import minic.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * 表示词法分析得到的一个 token。
 *
 * @param kind token 类型
 * @param lexeme 源码中的原始文本
 * @param range token 对应的源码范围
 * @param literalValue 可选字面量值，非字面量 token 为 {@code null}
 */
public record Token(
        TokenKind kind,
        String lexeme,
        SourceRange range,
        Object literalValue
) {
    /**
     * 创建 token。
     *
     * @param kind token 类型
     * @param lexeme 源码中的原始文本
     * @param range token 对应的源码范围
     * @param literalValue 可选字面量值，非字面量 token 为 {@code null}
     */
    public Token {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(range, "range");
    }

    /**
     * 创建不携带字面量值的 token。
     *
     * @param kind token 类型
     * @param lexeme 源码中的原始文本
     * @param range token 对应的源码范围
     */
    public Token(TokenKind kind, String lexeme, SourceRange range) {
        this(kind, lexeme, range, null);
    }

    /**
     * 以 {@link Optional} 形式返回字面量值。
     *
     * @return 字面量值；不存在时为空
     */
    public Optional<Object> literalValueOptional() {
        return Optional.ofNullable(literalValue);
    }
}
