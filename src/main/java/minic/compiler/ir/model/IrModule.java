package minic.compiler.ir.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * IR 模块。
 *
 * @param functions 模块中的函数列表
 * @param stringData 只读字符串数据列表
 */
public record IrModule(List<IrFunction> functions, List<IrStringData> stringData) {
    /**
     * 创建 IR 模块，并防御性复制列表。
     *
     * @param functions 模块中的函数列表
     * @param stringData 只读字符串数据列表
     */
    public IrModule {
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(stringData, "stringData");
        functions = List.copyOf(functions);
        stringData = List.copyOf(stringData);
    }

    /**
     * 创建不携带字符串数据的 IR 模块。
     *
     * @param functions 模块中的函数列表
     */
    public IrModule(List<IrFunction> functions) {
        this(functions, List.of());
    }

    /**
     * 按名称查找函数。
     *
     * @param name 函数名称
     * @return 匹配函数；不存在时为空
     */
    public Optional<IrFunction> findFunction(String name) {
        Objects.requireNonNull(name, "name");
        return functions.stream()
                .filter(function -> function.name().equals(name))
                .findFirst();
    }
}
