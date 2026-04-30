package minic.compiler.ir.value;

import minic.compiler.ir.model.IrType;

import java.util.Objects;

/**
 * 指向 IR 只读字符串数据项的值。
 *
 * @param label 字符串数据标签
 */
public record IrStringLiteral(String label) implements IrValue {
    /**
     * 创建字符串字面量引用值。
     *
     * @param label 字符串数据标签
     */
    public IrStringLiteral {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }

    @Override
    public IrType type() {
        return IrType.STRING_POINTER;
    }
}
