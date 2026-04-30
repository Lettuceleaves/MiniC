package minic.compiler.ir.model;

import java.util.Objects;

/**
 * IR 只读字符串数据项。
 *
 * @param label 汇编中导出的稳定标签
 * @param value 解码后的字符串值
 */
public record IrStringData(String label, String value) {
    /**
     * 创建只读字符串数据项。
     *
     * @param label 汇编中导出的稳定标签
     * @param value 解码后的字符串值
     */
    public IrStringData {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }
}
