package minic.compiler.link.pe;

import java.util.Arrays;
import java.util.Objects;

/**
 * 内置链接器生成的 PE32+ 映像。
 *
 * @param bytes 完整可执行文件字节
 */
public record PeImage(byte[] bytes) {
    public PeImage {
        Objects.requireNonNull(bytes, "bytes");
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
