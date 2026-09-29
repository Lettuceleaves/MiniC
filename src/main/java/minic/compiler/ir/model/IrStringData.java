package minic.compiler.ir.model;

import java.util.Objects;

/**
 * IR 只读字符串数据项。
 *
 * @param label 汇编中导出的稳定标签
 * @param value 解码后的字符串值
 */
public record IrStringData(String label, String value, byte[] bytes) {
    /**
     * 创建只读字符串数据项。
     *
     * @param label 汇编中导出的稳定标签
     * @param value 解码后的字符串值
     */
    public IrStringData {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(bytes, "bytes");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        bytes = java.util.Arrays.copyOf(bytes, bytes.length);
    }

    public IrStringData(String label, String value) {
        this(label, value, (value + '\0').getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override public byte[] bytes() { return java.util.Arrays.copyOf(bytes, bytes.length); }
}
