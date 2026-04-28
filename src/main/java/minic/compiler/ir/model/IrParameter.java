package minic.compiler.ir.model;

import minic.compiler.ir.value.IrParameterRef;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 函数形参。
 *
 * @param name 形参名称
 * @param type 形参类型
 * @param range 形参对应的源码范围
 */
public record IrParameter(String name, IrType type, SourceRange range) {
    /**
     * 创建 IR 函数形参。
     *
     * @param name 形参名称
     * @param type 形参类型
     * @param range 形参对应的源码范围
     */
    public IrParameter {
        Objects.requireNonNull(name, "name");
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
