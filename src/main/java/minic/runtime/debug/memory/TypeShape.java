package minic.runtime.debug.memory;

/**
 * Typed memory graph node shape.
 */
public enum TypeShape {
    SCALAR,
    POINTER,
    ARRAY,
    STRUCT,
    NULL,
    UNINITIALIZED,
    HEAP_BLOCK,
    UNKNOWN
}
