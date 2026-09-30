package minic.compiler.lexer.token;

import minic.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * 表示词法分析得到的一个 token。
 *
 * @param type token 类型
 * @param lexeme 源码中的原始文本
 * @param range token 对应的源码范围
 * @param literalValue 可选字面量值，非字面量 token 为 {@code null}
 */
public record Token(
        TokenType type,
        String lexeme,
        SourceRange range,
        Object literalValue
) {
    /**
     * 创建 token。
     *
     * @param type token 类型
     * @param lexeme 源码中的原始文本
     * @param range token 对应的源码范围
     * @param literalValue 可选字面量值，非字面量 token 为 {@code null}
     */
    public Token {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(range, "range");
    }

    /**
     * 创建不携带字面量值的 token。
     *
     * @param type token 类型
     * @param lexeme 源码中的原始文本
     * @param range token 对应的源码范围
     */
    public Token(TokenType type, String lexeme, SourceRange range) {
        this(type, lexeme, range, null);
    }

    /**
     * 以 {@link Optional} 形式返回字面量值。
     *
     * @return 字面量值；不存在时为空
     */
    public Optional<Object> literalValueOptional() {
        return Optional.ofNullable(literalValue);
    }

    /** 非 {@code int} 十进制整数字面量的语义类别。 */
    public enum IntegerLiteralKind {
        LONG,
        UNSIGNED_INT,
        UNSIGNED_LONG,
        LONG_LONG,
        UNSIGNED_LONG_LONG
    }

    /**
     * 保存最多 64 位的整数字面量。无符号 64 位值以相同位模式保存在 {@code long} 中。
     */
    public record IntegerLiteralValue(long value, IntegerLiteralKind kind) {
        public IntegerLiteralValue {
            Objects.requireNonNull(kind, "kind");
        }
    }

    public enum LiteralEncoding { ORDINARY, UTF8, UTF16, UTF32 }

    /** Used for prefixed literals; ordinary literals retain their legacy String/Character value. */
    public record StringLiteralValue(String value, LiteralEncoding encoding) {
        public StringLiteralValue { Objects.requireNonNull(value); Objects.requireNonNull(encoding); }
    }

    public record CharacterLiteralValue(int value, LiteralEncoding encoding) {
        public CharacterLiteralValue { Objects.requireNonNull(encoding); }
    }
}
