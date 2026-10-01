package minic.compiler.semantic.cpp;

/** Source expression category, independent of the pointer ABI used to implement references. */
public enum CppValueCategory {
    LVALUE,
    XVALUE,
    PRVALUE
}
