package minic.compiler.nativebuild.machine;

/**
 * 机器模块中的逻辑 section 类型。
 */
public enum MachineSectionKind {
    CODE,
    READ_ONLY_DATA,
    WRITABLE_DATA,
    UNWIND_DATA
}
