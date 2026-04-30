package minic.compiler.ir.model;

/**
 * MiniC IR 中的值类型。
 */
public enum IrType {
    /**
     * v0.1 唯一的整数类型。
     */
    INT,

    /**
     * 指针或地址类型。
     */
    POINTER,

    /**
     * 固定长度 int 数组。
     */
    INT_ARRAY
}
