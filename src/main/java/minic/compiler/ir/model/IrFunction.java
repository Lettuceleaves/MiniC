package minic.compiler.ir.model;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * IR 函数。
 *
 * @param name 函数名称
 * @param parameters 形参列表
 * @param blocks 基本块列表
 * @param range 函数对应的源码范围
 */
public record IrFunction(String name, List<IrParameter> parameters, List<IrBlock> blocks, SourceRange range) {
    /**
     * 创建 IR 函数，并防御性复制形参和基本块列表。
     *
     * @param name 函数名称
     * @param parameters 形参列表
     * @param blocks 基本块列表
     * @param range 函数对应的源码范围
     */
    public IrFunction {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        parameters = List.copyOf(parameters);
        blocks = List.copyOf(blocks);
    }
}
