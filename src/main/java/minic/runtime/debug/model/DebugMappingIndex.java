package minic.runtime.debug;

import minic.compiler.ast.decl.Program;
import minic.compiler.codegen.windows.WindowsX64CodegenStepState;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;
import minic.source.SourceRange;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Source/AST/IR/ASM Debug 映射索引。
 */
public final class DebugMappingIndex {
    private final List<DebugMappingItem> astItems;
    private final List<DebugMappingItem> irItems;
    private final List<DebugMappingItem> asmItems;

    private DebugMappingIndex(
            List<DebugMappingItem> astItems,
            List<DebugMappingItem> irItems,
            List<DebugMappingItem> asmItems
    ) {
        this.astItems = List.copyOf(astItems);
        this.irItems = List.copyOf(irItems);
        this.asmItems = List.copyOf(asmItems);
    }

    /**
     * 从 AST 和 IR 构建映射索引。
     *
     * @param program AST 根节点
     * @param module IR 模块
     * @return 映射索引
     */
    public static DebugMappingIndex build(Program program, IrModule module) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(module, "module");
        ArrayList<DebugMappingItem> ast = new ArrayList<>();
        collectAst(program, "ast-root", ast);
        ArrayList<DebugMappingItem> ir = collectIr(module);
        ArrayList<DebugMappingItem> asm = collectAsm(module);
        return new DebugMappingIndex(ast, ir, asm);
    }

    /**
     * 查询包含指定源码范围的映射项。
     *
     * @param sourceRange 源码范围
     * @return 查询结果
     */
    public DebugMappingQueryResult findBySourceRange(SourceRange sourceRange) {
        Objects.requireNonNull(sourceRange, "sourceRange");
        return new DebugMappingQueryResult(
                sourceKey(sourceRange),
                matching(astItems, sourceRange),
                matching(irItems, sourceRange),
                matching(asmItems, sourceRange)
        );
    }

    /**
     * 查询指定源码行相关映射项。
     *
     * @param line 一基源码行号
     * @return 查询结果
     */
    public DebugMappingQueryResult findBySourceLine(int line) {
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
        return new DebugMappingQueryResult(
                "line:" + line,
                matchingLine(astItems, line),
                matchingLine(irItems, line),
                matchingLine(asmItems, line)
        );
    }

    /**
     * 返回 AST 项。
     *
     * @return AST 项
     */
    public List<DebugMappingItem> astItems() {
        return astItems;
    }

    /**
     * 返回 IR 项。
     *
     * @return IR 项
     */
    public List<DebugMappingItem> irItems() {
        return irItems;
    }

    /**
     * 返回 ASM 项。
     *
     * @return ASM 项
     */
    public List<DebugMappingItem> asmItems() {
        return asmItems;
    }

    private static void collectAst(Object node, String id, ArrayList<DebugMappingItem> items) {
        items.add(new DebugMappingItem(id, "AST", node.getClass().getSimpleName(), range(node), astDetail(node)));
        int childIndex = 0;
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            Object value = read(component, node);
            if (isAstNode(value)) {
                collectAst(value, id + "-" + component.getName(), items);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (isAstNode(item)) {
                        collectAst(item, id + "-" + component.getName() + "-" + childIndex++, items);
                    }
                }
            }
        }
    }

    private static ArrayList<DebugMappingItem> collectIr(IrModule module) {
        ArrayList<DebugMappingItem> items = new ArrayList<>();
        for (IrFunction function : module.functions()) {
            items.add(new DebugMappingItem(
                    "ir-fn-" + function.name(),
                    "IR_FUNCTION",
                    "function " + function.name(),
                    function.range(),
                    "IR 函数"
            ));
            for (IrBlock block : function.blocks()) {
                for (int i = 0; i < block.instructions().size(); i++) {
                    IrInstruction instruction = block.instructions().get(i);
                    items.add(new DebugMappingItem(
                            "ir-" + function.name() + "-" + block.label() + "-" + i,
                            "IR_INSTRUCTION",
                            instruction.getClass().getSimpleName(),
                            instruction.range(),
                            block.label() + "#" + i
                    ));
                }
            }
        }
        return items;
    }

    private static ArrayList<DebugMappingItem> collectAsm(IrModule module) {
        WindowsX64CodegenStepState codegen = new WindowsX64CodegenStepState(module);
        codegen.toAssemblySource();
        ArrayList<DebugMappingItem> items = new ArrayList<>();
        List<minic.compiler.codegen.windows.WindowsX64AssemblyLine> lines = codegen.work().assemblyLineData();
        for (int i = 0; i < lines.size(); i++) {
            minic.compiler.codegen.windows.WindowsX64AssemblyLine line = lines.get(i);
            items.add(new DebugMappingItem(
                    "asm-" + (i + 1),
                    "ASM_LINE",
                    line.text(),
                    line.sourceRange(),
                    "生成汇编映射展示，不代表真实 CPU 状态"
            ));
        }
        return items;
    }

    private static List<DebugMappingItem> matching(List<DebugMappingItem> items, SourceRange sourceRange) {
        return items.stream()
                .filter(item -> item.sourceRangeOptional()
                        .map(range -> overlaps(range, sourceRange))
                        .orElse(false))
                .toList();
    }

    private static List<DebugMappingItem> matchingLine(List<DebugMappingItem> items, int line) {
        return items.stream()
                .filter(item -> item.sourceRangeOptional()
                        .map(range -> range.startPosition().line() <= line && range.endPosition().line() >= line)
                        .orElse(false))
                .toList();
    }

    private static boolean overlaps(SourceRange left, SourceRange right) {
        return left.sourceFile().path().equals(right.sourceFile().path())
                && left.startOffset() < right.endOffset()
                && right.startOffset() < left.endOffset();
    }

    private static String sourceKey(SourceRange range) {
        return range.sourceFile().path() + ":" + range.startOffset() + "-" + range.endOffset();
    }

    private static SourceRange range(Object node) {
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            if (component.getName().equals("range")) {
                Object value = read(component, node);
                if (value instanceof SourceRange sourceRange) {
                    return sourceRange;
                }
            }
        }
        return null;
    }

    private static String astDetail(Object node) {
        for (String componentName : List.of("name", "operator", "value", "lexeme")) {
            Object value = componentValue(node, componentName);
            if (value != null) {
                return componentName + "=" + value;
            }
        }
        return "AST 节点";
    }

    private static Object componentValue(Object node, String componentName) {
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            if (component.getName().equals(componentName)) {
                Object value = read(component, node);
                if (!isAstNode(value) && !(value instanceof List<?>) && !(value instanceof SourceRange)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Object read(RecordComponent component, Object node) {
        try {
            return component.getAccessor().invoke(node);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("cannot read AST component: " + component.getName(), exception);
        }
    }

    private static boolean isAstNode(Object value) {
        return value != null && value.getClass().getPackageName().startsWith("minic.compiler.ast.");
    }
}
