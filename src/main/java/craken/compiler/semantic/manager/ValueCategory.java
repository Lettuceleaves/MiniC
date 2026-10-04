package craken.compiler.semantic.manager;

/** Source expression category, independent of the pointer ABI used to implement references. */
public enum ValueCategory {
    LVALUE,
    XVALUE,
    PRVALUE
}
