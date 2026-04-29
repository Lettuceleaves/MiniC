package minic.compiler.ir.instruction;

/**
 * IR 二元操作符。
 */
public enum IrBinaryOperator {
    /**
     * 整数加法。
     */
    ADD,

    /**
     * 整数减法。
     */
    SUBTRACT,

    /**
     * 整数乘法。
     */
    MULTIPLY,

    /**
     * 整数除法。
     */
    DIVIDE,

    /**
     * 相等比较。
     */
    EQUAL,

    /**
     * 不等比较。
     */
    NOT_EQUAL,

    /**
     * 小于比较。
     */
    LESS_THAN,

    /**
     * 小于等于比较。
     */
    LESS_EQUAL,

    /**
     * 大于比较。
     */
    GREATER_THAN,

    /**
     * 大于等于比较。
     */
    GREATER_EQUAL
}
