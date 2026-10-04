package craken.debug;

import craken.SourceRange;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import craken.compiler.ir.model.IrBlock;
import craken.compiler.ir.model.IrFunction;
import craken.compiler.ir.model.IrGlobalData;
import craken.compiler.ir.model.IrLocal;
import craken.compiler.ir.model.IrParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 调试预处理阶段扫描整份 IR 得到的变量标记索引。
 *
 * <p>两个键各自服务一种查询：定义范围唯一标识一次声明（同名变量的多次声明拥有
 * 不同的范围），源码名用于从运行时快照的名称反查候选声明。函数内联产生的多个
 * 存储槽位可以共享同一源码定义范围，它们映射到同一个标记。</p>
 */
public final class DebugVariableRegistry {
    private final Map<SourceRange, DebugVariable> byDefinition;
    private final Map<String, List<DebugVariable>> byName;

    private DebugVariableRegistry(Map<SourceRange, DebugVariable> byDefinition,
                                  Map<String, List<DebugVariable>> byName) {
        this.byDefinition = byDefinition;
        this.byName = byName;
    }

    /** 扫描 IR：全局变量、每个函数的形参，以及所有基本块里的局部变量声明指令。 */
    public static DebugVariableRegistry scan(IrResult ir) {
        Objects.requireNonNull(ir, "ir");
        LinkedHashMap<SourceRange, DebugVariable> definitions = new LinkedHashMap<>();
        LinkedHashMap<String, List<DebugVariable>> names = new LinkedHashMap<>();

        for (IrGlobalData global : ir.globalData()) {
            String sourceName = ir.displayName(global.label());
            DebugVariable variable = new DebugVariable(sourceName, global.declaredType(),
                    DebugVariable.Kind.GLOBAL, "", global.range(), false);
            definitions.putIfAbsent(global.range(), variable);
            names.computeIfAbsent(sourceName, ignored -> new ArrayList<>()).add(variable);
        }
        for (IrFunction function : ir.functions()) {
            String functionName = ir.displayName(function.name());
            for (IrParameter parameter : function.parameters()) {
                String sourceName = ir.displayName(parameter.name());
                DebugVariable variable = new DebugVariable(sourceName, parameter.declaredType(),
                        DebugVariable.Kind.PARAMETER, functionName, parameter.range(), false);
                definitions.putIfAbsent(parameter.range(), variable);
                names.computeIfAbsent(sourceName, ignored -> new ArrayList<>()).add(variable);
            }
            for (IrBlock block : function.blocks()) {
                for (IrInstruction instruction : block.instructions()) {
                    if (!(instruction instanceof IrDeclareLocalInstruction declaration)) continue;
                    IrLocal local = declaration.local();
                    // 可变参数 ABI 的伪槽位不是源码对象。
                    if (local.incomingArgumentArea()) continue;
                    // 符号重命名器给局部变量起的内部名（crakenSymbol#），必须经
                    // displayNames 还原成源码名；局部声明的 #N 后缀属于内部序号。
                    String sourceName = ir.displayName(local.sourceName());
                    DebugVariable variable = new DebugVariable(sourceName, local.declaredType(),
                            DebugVariable.Kind.LOCAL, functionName, local.range(),
                            sourceName.startsWith("__"));
                    definitions.putIfAbsent(local.range(), variable);
                    names.computeIfAbsent(sourceName, ignored -> new ArrayList<>()).add(variable);
                }
            }
        }
        return new DebugVariableRegistry(Collections.unmodifiableMap(definitions),
                freezeNames(names));
    }

    private static Map<String, List<DebugVariable>> freezeNames(Map<String, List<DebugVariable>> names) {
        LinkedHashMap<String, List<DebugVariable>> frozen = new LinkedHashMap<>();
        names.forEach((name, variables) -> frozen.put(name, List.copyOf(variables)));
        return Collections.unmodifiableMap(frozen);
    }

    /** 按定义范围精确查找声明。 */
    public Optional<DebugVariable> byDefinition(SourceRange range) {
        return Optional.ofNullable(byDefinition.get(Objects.requireNonNull(range, "range")));
    }

    /** 同名变量在不同作用域/函数中的所有候选声明，保持扫描顺序。 */
    public List<DebugVariable> byName(String sourceName) {
        return byName.getOrDefault(Objects.requireNonNull(sourceName, "sourceName"), List.of());
    }

    public Map<SourceRange, DebugVariable> definitions() {
        return byDefinition;
    }

    public int definitionCount() {
        return byDefinition.size();
    }
}
