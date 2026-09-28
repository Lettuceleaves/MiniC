package minic.compiler.nativebuild.coff;

import java.util.Arrays;
import java.util.Objects;

/**
 * 内置 COFF writer 生成的对象文件。
 *
 * @param bytes 完整 COFF 文件字节
 */
public record CoffObjectFile(byte[] bytes) {
    public CoffObjectFile {
        Objects.requireNonNull(bytes, "bytes");
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
