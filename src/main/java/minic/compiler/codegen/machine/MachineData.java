package minic.compiler.codegen.machine;

import java.util.Arrays;
import java.util.Objects;

/**
 * section 中的原始数据。
 *
 * @param bytes 数据字节
 * @param alignment 起始对齐
 */
public record MachineData(byte[] bytes, int alignment) implements MachineItem {
    public MachineData {
        Objects.requireNonNull(bytes, "bytes");
        if (alignment <= 0 || Integer.bitCount(alignment) != 1) {
            throw new IllegalArgumentException("alignment must be a positive power of two");
        }
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
