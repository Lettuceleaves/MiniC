package minic.compiler.ir.model;

/**
 * MiniC IR 中的值类型。
 */
public enum IrType {
    /**
     * 1 字节布尔类型。
     */
    BOOL(1),

    /**
     * 1 字节有符号 char 类型。
     */
    CHAR(1),

    /**
     * 4 字节 int 类型。
     */
    INT(4),

    /**
     * 8 字节 long 类型。
     */
    LONG(8),

    /**
     * 4 字节 float 类型。
     */
    FLOAT(4),

    /**
     * 8 字节 double 类型。
     */
    DOUBLE(8),

    /**
     * 指针或地址类型。
     */
    POINTER(8);

    private final int sizeBytes;

    IrType(int sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public int sizeBytes() {
        return sizeBytes;
    }

    public boolean isIntegerScalar() {
        return this == BOOL || this == CHAR || this == INT || this == LONG;
    }

    public boolean isFloatingScalar() {
        return this == FLOAT || this == DOUBLE;
    }

    public boolean isScalar() {
        return isIntegerScalar() || isFloatingScalar();
    }
}
