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
     * 指向只读字符串数据的地址值。
     */
    STRING_POINTER
}
