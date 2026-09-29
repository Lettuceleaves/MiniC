package minic.compiler.obj.machine;

/**
 * 机器 section 内按顺序排列的项目。
 */
public sealed interface MachineItem permits MachineInstruction, MachineLabel, MachineData {
}
