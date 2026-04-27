package minic.compiler.lexer;

/**
 * MiniC v0.1 词法 token 类型。
 */
public enum TokenKind {
    /**
     * 输入结束标记。
     */
    EOF,

    /**
     * 标识符。
     */
    IDENTIFIER,

    /**
     * 十进制整数字面量。
     */
    INTEGER_LITERAL,

    /**
     * {@code int} 关键字。
     */
    INT,

    /**
     * {@code return} 关键字。
     */
    RETURN,

    /**
     * {@code +}。
     */
    PLUS,

    /**
     * {@code -}。
     */
    MINUS,

    /**
     * {@code *}。
     */
    STAR,

    /**
     * {@code /}。
     */
    SLASH,

    /**
     * {@code =}。
     */
    EQUAL,

    /**
     * {@code (}。
     */
    LEFT_PAREN,

    /**
     * {@code )}。
     */
    RIGHT_PAREN,

    /**
     * {@code \{}。
     */
    LEFT_BRACE,

    /**
     * {@code }}。
     */
    RIGHT_BRACE,

    /**
     * {@code ;}。
     */
    SEMICOLON,

    /**
     * {@code ,}。
     */
    COMMA
}
