package craken.compiler.ir.model;

/**
 * Craken IR 中的值类型。
 */
public enum IrType {
    /**
     * 1 字节布尔类型。
     */
    BOOL(1, false),

    /**
     * 1 字节有符号 char 类型。
     */
    CHAR(1, true),

    /** 1 字节显式有符号 char。 */
    SIGNED_CHAR(1, true),

    /** 1 字节无符号 char。 */
    UNSIGNED_CHAR(1, false),

    /** 2 字节有符号 short。 */
    SHORT(2, true),

    /** 2 字节无符号 short。 */
    UNSIGNED_SHORT(2, false),

    /**
     * 4 字节 int 类型。
     */
    INT(4, true),

    /** 4 字节 unsigned int。 */
    UNSIGNED_INT(4, false),

    /**
     * Windows LLP64 的 4 字节 long 类型。
     */
    LONG(4, true),

    /** Windows LLP64 的 4 字节 unsigned long。 */
    UNSIGNED_LONG(4, false),

    /** Windows LLP64 的 8 字节 long long。 */
    LONG_LONG(8, true),

    /** Windows LLP64 的 8 字节 unsigned long long。 */
    UNSIGNED_LONG_LONG(8, false),

    /**
     * 4 字节 float 类型。
     */
    FLOAT(4, true),

    /**
     * 8 字节 double 类型。
     */
    DOUBLE(8, true),

    /**
     * 指针或地址类型。
     */
    POINTER(8, false);

    private final int sizeBytes;
    private final boolean signed;

    IrType(int sizeBytes, boolean signed) {
        this.sizeBytes = sizeBytes;
        this.signed = signed;
    }

    public int sizeBytes() {
        return sizeBytes;
    }

    public boolean isIntegerScalar() {
        return switch (this) {
            case BOOL, CHAR, SIGNED_CHAR, UNSIGNED_CHAR, SHORT, UNSIGNED_SHORT,
                    INT, UNSIGNED_INT, LONG, UNSIGNED_LONG, LONG_LONG, UNSIGNED_LONG_LONG -> true;
            default -> false;
        };
    }

    public boolean isSignedInteger() {
        return isIntegerScalar() && signed;
    }

    public boolean isUnsignedInteger() {
        return isIntegerScalar() && !signed;
    }

    public boolean isWideInteger() {
        return isIntegerScalar() && sizeBytes == Long.BYTES;
    }

    public boolean isFloatingScalar() {
        return this == FLOAT || this == DOUBLE;
    }

    public boolean isScalar() {
        return isIntegerScalar() || isFloatingScalar();
    }
}
