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
     * 十进制 long 整数字面量。
     */
    LONG_LITERAL,

    /**
     * 浮点数字面量。
     */
    FLOAT_LITERAL,

    /**
     * double 字面量。
     */
    DOUBLE_LITERAL,

    /**
     * 字符字面量。
     */
    CHAR_LITERAL,

    /**
     * 字符串字面量。
     */
    STRING_LITERAL,

    /**
     * 布尔字面量。
     */
    BOOL_LITERAL,

    /**
     * NULL 空指针常量。
     */
    NULL_LITERAL,

    /**
     * {@code bool} 关键字。
     */
    BOOL,

    /**
     * {@code char} 关键字。
     */
    CHAR,

    /**
     * {@code int} 关键字。
     */
    INT,

    /**
     * {@code long} 关键字。
     */
    LONG,

    /**
     * {@code float} 关键字。
     */
    FLOAT,

    /**
     * {@code double} 关键字。
     */
    DOUBLE,

    /**
     * {@code extern} 关键字。
     */
    EXTERN,

    /**
     * {@code struct} 关键字。
     */
    STRUCT,

    /**
     * {@code return} 关键字。
     */
    RETURN,

    /**
     * {@code if} 关键字。
     */
    IF,

    /**
     * {@code else} 关键字。
     */
    ELSE,

    /**
     * {@code while} 关键字。
     */
    WHILE,

    /**
     * {@code for} 关键字。
     */
    FOR,

    /**
     * {@code break} 关键字。
     */
    BREAK,

    /**
     * {@code continue} 关键字。
     */
    CONTINUE,

    /**
     * {@code +}。
     */
    PLUS,

    /**
     * {@code ++}。
     */
    PLUS_PLUS,

    /**
     * {@code +=}。
     */
    PLUS_EQUAL,

    /**
     * {@code -}。
     */
    MINUS,

    /**
     * {@code ->}。
     */
    ARROW,

    /**
     * {@code *}。
     */
    STAR,

    /**
     * {@code &}。
     */
    AMPERSAND,

    /**
     * {@code /}。
     */
    SLASH,

    /**
     * {@code =}。
     */
    EQUAL,

    /**
     * {@code ==}。
     */
    EQUAL_EQUAL,

    /**
     * {@code !=}。
     */
    BANG_EQUAL,

    /**
     * {@code <}。
     */
    LESS,

    /**
     * {@code <=}。
     */
    LESS_EQUAL,

    /**
     * {@code >}。
     */
    GREATER,

    /**
     * {@code >=}。
     */
    GREATER_EQUAL,

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
     * {@code [}。
     */
    LEFT_BRACKET,

    /**
     * {@code ]}。
     */
    RIGHT_BRACKET,

    /**
     * {@code ;}。
     */
    SEMICOLON,

    /**
     * {@code ,}。
     */
    COMMA,

    /**
     * {@code .}。
     */
    DOT
}
