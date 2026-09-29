package minic.compiler.obj.machine;

import java.util.Objects;

/**
 * x64 内存操作数。symbol、base 和 index 可以按 RIP-relative、base/index 或混合形式组合。
 *
 * @param sizeBits 访问宽度，0 表示由另一操作数推导
 * @param symbol 可选符号名
 * @param base 可选基址寄存器名
 * @param index 可选索引寄存器名
 * @param scale 索引倍率，只允许 1、2、4、8
 * @param displacement 常量位移
 */
public record MemoryOperand(
        int sizeBits,
        String symbol,
        String base,
        String index,
        int scale,
        int displacement
) implements MachineOperand {
    public MemoryOperand {
        if (sizeBits != 0 && sizeBits != 8 && sizeBits != 16 && sizeBits != 32 && sizeBits != 64) {
            throw new IllegalArgumentException("unsupported memory width: " + sizeBits);
        }
        if (scale != 1 && scale != 2 && scale != 4 && scale != 8) {
            throw new IllegalArgumentException("unsupported index scale: " + scale);
        }
        if (symbol != null && symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (base != null && base.isBlank()) {
            throw new IllegalArgumentException("base must not be blank");
        }
        if (index != null && index.isBlank()) {
            throw new IllegalArgumentException("index must not be blank");
        }
        if (symbol == null && base == null && index == null) {
            throw new IllegalArgumentException("memory operand requires a symbol, base, or index");
        }
    }

    public static MemoryOperand base(int sizeBits, String base, int displacement) {
        return new MemoryOperand(sizeBits, null, Objects.requireNonNull(base, "base"), null, 1, displacement);
    }

    public static MemoryOperand ripRelative(int sizeBits, String symbol) {
        return new MemoryOperand(sizeBits, Objects.requireNonNull(symbol, "symbol"), "rip", null, 1, 0);
    }
}
