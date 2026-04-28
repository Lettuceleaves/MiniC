package minic.compiler.ir.value;

import minic.compiler.ir.model.IrType;

import java.util.Objects;

/**
 * IR 形参引用。
 *
 * @param name 形参名称
 * @param type 形参类型
 */
public record IrParameterRef(String name, IrType type) implements IrValue {
    /**
     * 创建 IR 形参引用。
     *
     * @param name 形参名称
     * @param type 形参类型
     */
    public IrParameterRef {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
