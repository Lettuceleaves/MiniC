package minic.compiler.ir;

import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrStringData;
import minic.compiler.ir.model.IrGlobalData;
import minic.compiler.semantic.model.StructLayout;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map;

/**
 * 一次 IR lowering 的最终结果，也是后续 Asm 阶段的输入。
 *
 * @param functions 已完成 lowering 的函数列表
 * @param stringData 只读字符串数据列表
 * @param externalFunctionNames 外部函数名称集合
 * @param structLayouts IR 中聚合地址所引用的命名结构体布局
 */
public record IrResult(
        List<IrFunction> functions,
        List<IrStringData> stringData,
        List<IrGlobalData> globalData,
        Set<String> externalFunctionNames,
        Set<String> externalObjectNames,
        Map<String, StructLayout> structLayouts
) {
    public IrResult {
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(stringData, "stringData");
        Objects.requireNonNull(globalData, "globalData");
        Objects.requireNonNull(externalFunctionNames, "externalFunctionNames");
        Objects.requireNonNull(externalObjectNames, "externalObjectNames");
        Objects.requireNonNull(structLayouts, "structLayouts");
        functions = List.copyOf(functions);
        stringData = List.copyOf(stringData);
        globalData = List.copyOf(globalData);
        // IR 结果是 ASM 阶段的输入边界：已有函数体的符号永远不能再作为外部函数输出。
        LinkedHashSet<String> normalizedExternals = new LinkedHashSet<>(externalFunctionNames);
        functions.stream().map(IrFunction::name).forEach(normalizedExternals::remove);
        externalFunctionNames = Set.copyOf(normalizedExternals);
        externalObjectNames = Set.copyOf(externalObjectNames);
        structLayouts = Map.copyOf(structLayouts);
    }

    public IrResult(List<IrFunction> functions, List<IrStringData> stringData, Set<String> externalFunctionNames) {
        this(functions, stringData, List.of(), externalFunctionNames, Set.of(), Map.of());
    }

    public IrResult(List<IrFunction> functions, List<IrStringData> stringData,
                    Set<String> externalFunctionNames, Map<String, StructLayout> structLayouts) {
        this(functions, stringData, List.of(), externalFunctionNames, Set.of(), structLayouts);
    }

    public IrResult(List<IrFunction> functions, List<IrStringData> stringData, List<IrGlobalData> globalData,
                    Set<String> externalFunctionNames, Map<String, StructLayout> structLayouts) {
        this(functions, stringData, globalData, externalFunctionNames, Set.of(), structLayouts);
    }

    public IrResult(List<IrFunction> functions, List<IrStringData> stringData) {
        this(functions, stringData, Set.of(), Map.of());
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
