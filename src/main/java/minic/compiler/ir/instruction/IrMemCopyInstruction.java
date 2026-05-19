package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 结构体内存拷贝指令，将 sizeBytes 字节从 source 地址复制到 destination 地址。
 *
 * @param destination 目标地址
 * @param source 源地址
 * @param sizeBytes 拷贝字节数
 * @param range 指令对应的源码范围
 */
public record IrMemCopyInstruction(IrValue destination, IrValue source, int sizeBytes, SourceRange range)
        implements IrInstruction {
    /**
     * 创建内存拷贝指令。
     *
     * @param destination 目标地址
     * @param source 源地址
     * @param sizeBytes 拷贝字节数
     * @param range 指令对应的源码范围
     */
    public IrMemCopyInstruction {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(range, "range");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive: " + sizeBytes);
        }
    }
}
