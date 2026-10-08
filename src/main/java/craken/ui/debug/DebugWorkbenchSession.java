package craken.ui.debug;

import craken.SourceRange;
import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.model.IrFunction;
import craken.compiler.ir.model.IrType;
import craken.compiler.type.CrakenType;
import craken.debug.DebugApi;
import craken.debug.DebugRuntime;
import craken.debug.DebugVariable;
import craken.debug.DebugVariableRegistry;
import craken.debug.Debugger;
import craken.debug.visualization.DebugCaptureProjector;
import craken.debug.visualization.DebugMemoryReader;
import craken.debug.visualization.RuntimeEvent;
import craken.debug.visualization.RuntimeEventCollector;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.api.ViewLocation;
import craken.visualization.snapshot.VisualizationSnapshot;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 调试工作台会话（调试线程独占）。
 *
 * <p>“开始”之前只编译到 IR 并扫描变量定义，供捕获列表按源码名检索；“开始”时把选中的
 * 变量定义写进调试 IR 副本的捕获指令，由 Debugger 在执行点记录创建/读取/写入/离开作用域，
 * 停止点再交给 {@link DebugCaptureProjector} 投影到可视化容器。结构自动识别与堆区不在本阶段。</p>
 *
 * <p>检索与栈区都只展示用户在编辑器里写下的变量：任何 include（库头文件或用户自己的 .mh）
 * 展开出的函数、形参、局部变量与全局变量在调试 IR 里都塌缩到 {@code #include} 行行首，
 * 按源码范围形状整体跳过，避免头文件内容淹没用户数据。</p>
 */
final class DebugWorkbenchSession implements AutoCloseable {
    record MemoryRow(String name, String value) {}
    record Snapshot(boolean started, int contextIndex, String status, String trap, int line, String function,
                    String block, int stackDepth, boolean canNext, boolean canPrevious, boolean canStepOut,
                    boolean canNextBreakpoint, boolean canPreviousBreakpoint, String stdout, String stderr,
                    String diagnostic, List<MemoryRow> stack, VisualizationSnapshot visualization) {}
    private static final int HISTORY_LIMIT = 2_000;
    private static final int STEP_BUDGET = 2_000_000;

    private final SourceFile source;
    private final Set<Integer> breakpoints;
    private final IrResult ir;
    private final DebugVariableRegistry variables;
    private final Set<String> includedFunctions;
    private final TreeSet<Integer> visitedBreakpoints = new TreeSet<>();
    private final LinkedHashMap<Integer, DebugCaptureProjector.Checkpoint> checkpoints = new LinkedHashMap<>();
    private volatile DebugApi api;
    private DefaultVisualizationSession visualization;
    private DebugCaptureProjector projector;
    private RuntimeEventCollector events;
    private DebugMemoryReader lastMemory;
    private Snapshot snapshot;

    DebugWorkbenchSession(SourceFile source, Set<Integer> breakpoints) {
        this.source = Objects.requireNonNull(source, "source");
        this.breakpoints = Set.copyOf(Objects.requireNonNull(breakpoints, "breakpoints"));
        ir = new CompilerApi(source).runToIr();
        includedFunctions = includedFunctionNames();
        variables = DebugVariableRegistry.scan(ir);
        snapshot = new Snapshot(false, -1, "READY", "", 0, "", "", 0, false, false, false, false, false, "", "", "",
                List.of(), null);
    }

    Snapshot snapshot() { return snapshot; }

    /**
     * include 展开块在调试 IR 里塌缩为 {@code #include} 行行首的单字符源码范围：它不是编辑器里
     * 写下的内容。编辑器里的声明至少覆盖一个类型记号，不会长成这个形状。
     */
    private static boolean fromInclude(SourceRange range) {
        return range.startByte() == 0
                && range.endLine() == range.startLine()
                && range.endByte() <= 1;
    }

    /**
     * 运行时帧只带显示名，这里按显示名汇总：同名函数同时出现在编辑器与 include 展开里时保留不隐藏，
     * 宁可多显示也不吞掉用户数据。
     */
    private Set<String> includedFunctionNames() {
        LinkedHashMap<String, Boolean> includedByName = new LinkedHashMap<>();
        for (IrFunction function : ir.functions()) {
            includedByName.merge(ir.displayName(function.name()), fromInclude(function.range()),
                    (left, right) -> left && right);
        }
        return includedByName.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 按源码名检索可捕获的变量定义；形参、编译器内部槽位与 include 展开出的定义不参与本阶段捕获。 */
    List<DebugVariable> search(String term) {
        String name = Objects.requireNonNullElse(term, "").trim();
        if (name.isEmpty()) return List.of();
        return variables.byName(name).stream()
                .filter(variable -> !variable.synthetic())
                .filter(variable -> variable.kind() != DebugVariable.Kind.PARAMETER)
                .filter(variable -> !fromInclude(variable.definition()))
                .sorted(Comparator.comparing(DebugVariable::function)
                        .thenComparingInt(variable -> variable.definition().startLine()))
                .toList();
    }

    String typeText(DebugVariable variable) { return typeName(variable.declaredType()); }

    /** 调试暂停期间追加标准输入；未开始或已结束的会话忽略。 */
    void appendStandardInput(String text) {
        DebugApi current = api;
        if (current == null || text == null || text.isEmpty()) return;
        current.appendStandardInput(text);
    }

    Snapshot start(List<DebugVariable> selected) {
        Objects.requireNonNull(selected, "selected");
        closeRunState();
        Set<SourceRange> definitions = new LinkedHashSet<>();
        selected.forEach(variable -> definitions.add(variable.definition()));
        events = new RuntimeEventCollector();
        var debugger = Debugger.fromIr(source, ir, "", HISTORY_LIMIT, events,
                breakpoints, definitions);
        api = new DebugApi(debugger);
        visualization = new DefaultVisualizationSession();
        projector = new DebugCaptureProjector(visualization, ir);
        visitedBreakpoints.clear();
        checkpoints.clear();
        projector.declare(selected);
        return show(api.current());
    }

    Snapshot stepInto() { return api != null && api.canNext() ? show(api.next()) : snapshot; }

    Snapshot stepOver() {
        if (api == null) return snapshot;
        int depth = depth(api.current());
        Debugger.Context context = api.current();
        while (api.canNext() && !halted()) {
            context = api.next();
            if (depth(context) <= depth) break;
            observe(context);
        }
        return show(context);
    }

    Snapshot stepOut() {
        if (api == null) return snapshot;
        int depth = depth(api.current());
        Debugger.Context context = api.current();
        while (api.canNext() && !halted()) {
            context = api.next();
            if (depth(context) < depth) break;
            observe(context);
        }
        return show(context);
    }

    Snapshot runToEnd() {
        if (api == null) return snapshot;
        int remaining = STEP_BUDGET;
        var passed = new ArrayList<Debugger.Context>();
        while (api.canNext() && remaining-- > 0 && !halted()) passed.add(api.next());
        Debugger.Context context = passed.isEmpty() ? api.current() : passed.getLast();
        // 中间止点只并入事件、不建页也不保留检查点；终态由 show 完整投影，保持“运行到结束只发布一帧”。
        for (int index = 0; index + 1 < passed.size(); index++) observe(passed.get(index));
        return show(context);
    }

    Snapshot nextBreakpoint() {
        if (api == null) return snapshot;
        Debugger.Context context = api.current();
        while (api.canNext() && !halted()) {
            context = api.next();
            if (context.stop().breakpoint()) break;
            observe(context);
        }
        return show(context);
    }

    Snapshot previous() { return api != null && api.canPrevious() ? show(api.previous()) : snapshot; }

    Snapshot previousBreakpoint() {
        if (api == null) return snapshot;
        Debugger.Context context = api.current();
        while (api.canPrevious() && !halted()) {
            context = api.previous();
            if (context.stop().breakpoint()) break;
        }
        return show(context);
    }

    /** 中间上下文：只并入运行期事件与地址，不做结构展开与值刷新（跨断点命令可能走过上千步）。 */
    private void observe(Debugger.Context context) {
        if (projector != null) projector.observe(context.captures(), context.events().events());
    }

    /** 显示一个上下文：已有检查点只恢复，不重放捕获事件；新上下文投影后保存检查点。 */
    private Snapshot show(Debugger.Context context) {
        apply(context);
        if (context.stop().breakpoint()) visitedBreakpoints.add(context.index());
        var stop = context.stop();
        snapshot = new Snapshot(true, context.index(), stop.status().name(),
                stop.breakpoint() ? "BREAKPOINT" : stop.kind() == null ? "NONE" : stop.kind().name(),
                stop.range() == null ? 0 : stop.range().startLine(),
                Objects.toString(stop.function(), ""), Objects.toString(stop.block(), ""),
                depth(context), api.canNext(), api.canPrevious(), depth(context) > 1,
                api.canNext() && !breakpoints.isEmpty(),
                visitedBreakpoints.lower(context.index()) != null,
                context.runtime().stdout(), context.runtime().stderr(), Objects.toString(stop.error(), ""),
                stackRows(context.runtime()), projector.publishedSnapshot());
        return snapshot;
    }

    /** 折叠一个上下文的捕获事件；已有检查点时只恢复，不重放。 */
    private void apply(Debugger.Context context) {
        apply(context, true);
    }

    private void apply(Debugger.Context context, boolean checkpoint) {
        var existing = checkpoints.get(context.index());
        if (existing != null) {
            projector.restore(existing);
            return;
        }
        lastMemory = new DebugMemoryReader(context.runtime());
        projector.project(context.captures(), lastMemory, context.events().events());
        if (!checkpoint) return;
        checkpoints.put(context.index(), projector.checkpoint());
        while (checkpoints.size() > HISTORY_LIMIT) checkpoints.remove(checkpoints.keySet().iterator().next());
    }

    /** 只列出编辑器里写的帧与变量；include 展开出的帧整体不显示。 */
    private List<MemoryRow> stackRows(DebugRuntime.RuntimeState state) {
        var rows = new ArrayList<MemoryRow>();
        var frames = new ArrayList<>(state.stack());
        Collections.reverse(frames);
        for (var frame : frames) {
            if (includedFunctions.contains(frame.function())) continue;
            rows.add(new MemoryRow(frame.function() + " · 行 " + frame.line(), "帧"));
            frame.parameters().forEach(variable -> addRow(rows, variable));
            frame.locals().forEach(variable -> addRow(rows, variable));
        }
        return List.copyOf(rows);
    }

    private void addRow(List<MemoryRow> rows, DebugRuntime.StackVariable item) {
        MemoryRow row = row(item);
        if (row != null) rows.add(row);
    }

    /** 把运行时槽位映射回源码名；编译器内部临时槽位（__ 前缀、无源码名的 crakenSymbol）不展示。 */
    private MemoryRow row(DebugRuntime.StackVariable item) {
        String display = ir.displayName(item.name());
        if (item.name().startsWith("__") || display.equals(item.name()) && item.name().startsWith("crakenSymbol"))
            return null;
        String value;
        if (item.aggregate()) value = String.format("0x%x", item.address());
        else if (item.value() == null) value = "未初始化";
        else if (item.value().type() == IrType.POINTER) value = String.format("0x%x", item.value().integer());
        else value = item.value().toString();
        return new MemoryRow(display, value);
    }

    private static int depth(Debugger.Context context) { return context.runtime().stack().size(); }

    private static boolean halted() { return Thread.currentThread().isInterrupted(); }

    private String typeName(CrakenType type) {
        var unqualified = type.unqualified();
        if (unqualified instanceof CrakenType.PointerType pointer) return typeName(pointer.pointee()) + "*";
        if (unqualified instanceof CrakenType.ArrayType array) return typeName(array.elementType()) + "[" + array.length() + "]";
        if (unqualified instanceof CrakenType.StructType struct) return structName(struct);
        return unqualified.toString();
    }

    /** 模板实例的显示名已经带命名空间，不再重复加 struct 前缀。 */
    private String structName(CrakenType.StructType struct) {
        String name = ir.displayName(struct.name());
        return name.contains("::") ? name : "struct " + name;
    }

    private void closeRunState() {
        if (projector != null) projector.close();
        if (events != null) events.close();
        if (visualization != null) visualization.close();
        projector = null;
        events = null;
        visualization = null;
        api = null;
        checkpoints.clear();
    }

    @Override public void close() { closeRunState(); }
}
