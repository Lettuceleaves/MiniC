package minic.compiler.ir;

import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrStringData;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 一次 IR lowering 的最终结果，也是后续 Asm 阶段的输入。
 *
 * @param functions 已完成 lowering 的函数列表
 * @param stringData 只读字符串数据列表
 * @param externalFunctionNames 外部函数名称集合
 */
public record IrResult(
        List<IrFunction> functions,
        List<IrStringData> stringData,
        Set<String> externalFunctionNames
) {
    public IrResult {
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(stringData, "stringData");
        Objects.requireNonNull(externalFunctionNames, "externalFunctionNames");
        functions = List.copyOf(functions);
        stringData = List.copyOf(stringData);
        // IR 结果是 ASM 阶段的输入边界：已有函数体的符号永远不能再作为外部函数输出。
        LinkedHashSet<String> normalizedExternals = new LinkedHashSet<>(externalFunctionNames);
        functions.stream().map(IrFunction::name).forEach(normalizedExternals::remove);
        externalFunctionNames = Set.copyOf(normalizedExternals);
    }

    public IrResult(List<IrFunction> functions, List<IrStringData> stringData) {
        this(functions, stringData, Set.of());
    }

    public IrResult(List<IrFunction> functions) {
        this(functions, List.of());
    }

    public Optional<IrFunction> findFunction(String name) {
        Objects.requireNonNull(name, "name");
        return functions.stream()
                .filter(function -> function.name().equals(name))
                .findFirst();
    }
}
