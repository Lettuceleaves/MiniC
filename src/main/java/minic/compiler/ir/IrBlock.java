package minic.compiler.ir;

import java.util.List;
import java.util.Objects;

/**
 * IR 基本块。
 *
 * @param label 基本块标签
 * @param instructions 指令列表
 */
public record IrBlock(String label, List<IrInstruction> instructions) {
    /**
     * 创建 IR 基本块，并防御性复制指令列表。
     *
     * @param label 基本块标签
     * @param instructions 指令列表
     */
    public IrBlock {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(instructions, "instructions");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        instructions = List.copyOf(instructions);
    }
}
