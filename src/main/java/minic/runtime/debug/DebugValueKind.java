package minic.runtime.debug;

/**
 * Debug 值类别。
 */
public enum DebugValueKind {
    /**
     * int 标量。
     */
    INT,

    /**
     * long 标量。
     */
    LONG,

    /**
     * char 标量。
     */
    CHAR,

    /**
     * bool 标量。
     */
    BOOL,

    /**
     * 指针值。
     */
    POINTER,

    /**
     * 数组值。
     */
    ARRAY,

    /**
     * 结构体值。
     */
    STRUCT,

    /**
     * 空指针或空值。
     */
    NULL,

    /**
     * 未初始化值。
     */
    UNINITIALIZED
}
