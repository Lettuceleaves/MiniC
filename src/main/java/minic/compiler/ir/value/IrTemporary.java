package minic.compiler.ir.value;

import minic.compiler.ir.model.IrType;

import java.util.Objects;

/**
 * IR 临时值。
 *
 * @param name 临时值名称
 * @param type 临时值类型
 */
public record IrTemporary(String name, IrType type) implements IrValue {
    /**
     * 创建 IR 临时值。
     *
     * @param name 临时值名称
     * @param type 临时值类型
     */
    public IrTemporary {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
