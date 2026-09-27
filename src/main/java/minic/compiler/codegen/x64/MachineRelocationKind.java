package minic.compiler.codegen.x64;

/**
 * 编码后仍需由对象写入器或链接器处理的地址修正类型。
 */
public enum MachineRelocationKind {
    REL32,
    RIP_REL32,
    ADDR64
}
