package craken.compiler.obj.x64;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * x64 编码器产出的 section 字节、符号偏移和未解析修正。
 *
 * @param name section 名称
 * @param bytes 编码字节
 * @param symbols section 内符号及偏移
 * @param relocations 未解析修正
 */
public record EncodedMachineSection(
        String name,
        byte[] bytes,
        Map<String, Integer> symbols,
        List<MachineRelocation> relocations
) {
    public EncodedMachineSection {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(relocations, "relocations");
        bytes = Arrays.copyOf(bytes, bytes.length);
        symbols = Map.copyOf(symbols);
        relocations = List.copyOf(relocations);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
