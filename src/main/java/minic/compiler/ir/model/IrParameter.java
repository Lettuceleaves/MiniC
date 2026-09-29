package minic.compiler.ir.model;

import minic.compiler.ir.value.IrValue.IrParameterRef;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 函数形参。
 *
 * @param name 形参名称
 * @param declaredType 完整源码形参类型
 * @param type ABI/IR 值类型
 * @param range 形参对应的源码范围
 */
public record IrParameter(String name, MiniType declaredType, IrType type, SourceRange range) {
    /**
     * 创建 IR 函数形参。
     *
     * @param name 形参名称
     * @param declaredType 完整源码形参类型
     * @param type ABI/IR 值类型
     * @param range 形参对应的源码范围
     */
    public IrParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(declaredType, "declaredType");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /**
     * 创建该形参对应的 IR 值引用。
     *
     * @return 形参引用值
     */
    public IrParameterRef ref() {
        return new IrParameterRef(name, type);
    }
}
