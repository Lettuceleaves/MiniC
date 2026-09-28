package minic.compiler.nativebuild.machine;

/**
 * 整数立即数操作数。
 *
 * @param value 原始 64-bit 位模式
 */
public record ImmediateOperand(long value) implements MachineOperand {
}
