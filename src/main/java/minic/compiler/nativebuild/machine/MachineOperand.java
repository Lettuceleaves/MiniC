package minic.compiler.nativebuild.machine;

/**
 * x64 机器指令操作数。
 */
public sealed interface MachineOperand permits RegisterOperand, ImmediateOperand, MemoryOperand, SymbolOperand {
}
