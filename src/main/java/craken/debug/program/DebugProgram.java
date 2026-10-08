package craken.debug;

import craken.compiler.SourceFile;
import craken.SourceRange;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.instruction.CallInstruction;
import craken.compiler.ir.instruction.ControlInstruction.IrCaptureInstruction;
import craken.compiler.ir.instruction.ControlInstruction.IrJumpInstruction;
import craken.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import craken.compiler.ir.instruction.ControlInstruction.IrTrapInstruction;
import craken.compiler.ir.instruction.ControlInstruction.TrapKind;
import craken.compiler.ir.instruction.MemoryInstruction.IrAddressOfLocalInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrElementAddressInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrFieldAddressInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrLoadLocalInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrLoadPointerInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrMemCopyInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrStoreLocalInstruction;
import craken.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import craken.compiler.ir.model.IrBlock;
import craken.compiler.ir.model.IrFunction;
import craken.compiler.ir.model.IrLocal;
import craken.compiler.ir.value.IrValue.IrGlobalAddress;
import craken.compiler.ir.value.IrValue.IrTemporary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 调试代码区：持有独立的 IR 副本，并负责插装和源码行索引。 */
public final class DebugProgram {
    private final IrResult ir;
    private final List<List<Location>> lines;
    private final DebugVariableRegistry variables;
    private final Set<SourceRange> captures;
    private final Map<SourceRange, IrLocal> locals;
    private final Map<SourceRange, String> globals;

    DebugProgram(SourceFile source, IrResult original) {
        this(source, original, Set.of());
    }

    /**
     * 构造调试副本；{@code capturedDefinitions} 是“开始”前选中的变量定义范围。
     * 选中变量的创建/读取/写入/离开作用域之前（创建之后）插入捕获指令，
     * 未选中的变量与未插桩副本完全一致。
     */
    DebugProgram(SourceFile source, IrResult original, Set<SourceRange> capturedDefinitions) {
        this.captures = Set.copyOf(capturedDefinitions);
        int lineCount = 1 + (int) source.content().chars().filter(c -> c == '\n').count();
        ArrayList<List<Location>> lineIndex = new ArrayList<>();
        for (int line = 0; line <= lineCount; line++) lineIndex.add(new ArrayList<>());

        LinkedHashMap<SourceRange, IrLocal> knownLocals = new LinkedHashMap<>();
        LinkedHashMap<SourceRange, String> knownGlobals = new LinkedHashMap<>();
        LinkedHashMap<String, SourceRange> globalDefinitions = new LinkedHashMap<>();
        for (var global : original.globalData()) {
            knownGlobals.put(global.range(), global.label());
            globalDefinitions.put(global.label(), global.range());
        }

        ArrayList<IrFunction> functions = new ArrayList<>();
        for (IrFunction function : original.functions()) {
            var removals = capturedLocals(function, knownLocals);
            var derived = derivedTemporaries(function);
            ArrayList<IrBlock> blocks = new ArrayList<>();
            for (IrBlock block : function.blocks()) {
                ArrayList<IrInstruction> instrumented = new ArrayList<>();
                int previousLine = -1;
                for (IrInstruction instruction : block.instructions()) {
                    if (instruction instanceof IrTrapInstruction) {
                        throw new IllegalArgumentException("IR has already been instrumented");
                    }
                    int line = instruction.range().startLine();
                    // 合成跳转沿用所属语句范围，本身不代表该源码行重新开始。
                    if (!(instruction instanceof IrJumpInstruction) && line != previousLine) {
                        instrumented.add(new IrTrapInstruction(TrapKind.LINE, instruction.range()));
                        previousLine = line;
                    }
                    if (instruction instanceof CallInstruction) {
                        instrumented.add(new IrTrapInstruction(TrapKind.CALL, instruction.range()));
                    }
                    instrumented.addAll(beforeCapture(instruction, globalDefinitions, derived));
                    // 离开作用域必须发生在返回之前，否则帧已弹出、指令不再执行。
                    if (instruction instanceof IrReturnInstruction)
                        for (SourceRange definition : removals)
                            instrumented.add(new IrCaptureInstruction(
                                    IrCaptureInstruction.Kind.REMOVE, definition, instruction.range()));
                    instrumented.add(instruction);
                    if (instruction instanceof IrDeclareLocalInstruction declaration) {
                        knownLocals.putIfAbsent(declaration.local().range(), declaration.local());
                        instrumented.addAll(capture(captures, declaration.local().range(), IrCaptureInstruction.Kind.CREATE, instruction.range()));
                    }
                }
                // 空循环只有回跳指令，也必须能在每轮开始时暂停。
                if (instrumented.size() == 1 && instrumented.getFirst() instanceof IrJumpInstruction jump) {
                    instrumented.addFirst(new IrTrapInstruction(TrapKind.LINE, jump.range()));
                }

                blocks.add(new IrBlock(block.label(), instrumented));
                index(original.displayName(function.name()), block.label(), instrumented, lineIndex, lineCount);
            }
            functions.add(new IrFunction(
                    function.name(),
                    function.returnType(),
                    function.parameters(),
                    function.variadic(),
                    blocks,
                    function.range()
            ));
        }

        ir = new IrResult(
                functions,
                original.stringData(),
                original.globalData(),
                original.externalFunctionNames(),
                original.externalObjectNames(),
                original.structLayouts(),
                original.currentAstNode(),
                original.currentSubject(),
                original.displayNames(),
                original.entryFunction()
        );
        lines = lineIndex.stream().map(List::copyOf).toList();
        variables = DebugVariableRegistry.scan(ir);
        locals = Collections.unmodifiableMap(knownLocals);
        globals = Collections.unmodifiableMap(knownGlobals);
    }

    /** 选中变量在访问前插入的捕获指令；形参与未选中变量不插桩。 */
    private List<IrCaptureInstruction> beforeCapture(IrInstruction instruction,
                                                     Map<String, SourceRange> globalDefinitions,
                                                     Map<String, SourceRange> derived) {
        if (captures.isEmpty()) return List.of();
        return switch (instruction) {
            case IrLoadLocalInstruction load -> capture(captures, load.local().range(), IrCaptureInstruction.Kind.READ, instruction.range());
            case IrAddressOfLocalInstruction address -> capture(captures, address.local().range(), IrCaptureInstruction.Kind.READ, instruction.range());
            case IrStoreLocalInstruction store -> capture(captures, store.local().range(), IrCaptureInstruction.Kind.WRITE, instruction.range());
            case IrLoadPointerInstruction load when load.address() instanceof IrGlobalAddress address ->
                    capture(captures, globalDefinitions.get(address.globalName()), IrCaptureInstruction.Kind.READ, instruction.range());
            case IrStorePointerInstruction store when store.address() instanceof IrGlobalAddress address ->
                    capture(captures, globalDefinitions.get(address.globalName()), IrCaptureInstruction.Kind.WRITE, instruction.range());
            case IrLoadPointerInstruction load when load.address() instanceof IrTemporary address && derived.containsKey(address.name()) ->
                    element(derived, address.name(), IrCaptureInstruction.Kind.READ, instruction.range());
            case IrStorePointerInstruction store when store.address() instanceof IrTemporary address && derived.containsKey(address.name()) ->
                    element(derived, address.name(), IrCaptureInstruction.Kind.WRITE, instruction.range());
            case IrMemCopyInstruction copy when copy.destination() instanceof IrTemporary destination && derived.containsKey(destination.name()) ->
                    element(derived, destination.name(), IrCaptureInstruction.Kind.WRITE, instruction.range());
            case IrMemCopyInstruction copy when copy.source() instanceof IrTemporary source && derived.containsKey(source.name()) ->
                    element(derived, source.name(), IrCaptureInstruction.Kind.READ, instruction.range());
            default -> List.of();
        };
    }

    /** 值为被选中变量地址、或由其偏移派生的临时量；用于把元素访问定位到具体槽位。 */
    private Map<String, SourceRange> derivedTemporaries(IrFunction function) {
        if (captures.isEmpty()) return Map.of();
        var derived = new LinkedHashMap<String, SourceRange>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IrBlock block : function.blocks()) for (IrInstruction instruction : block.instructions())
                changed |= derive(derived, instruction);
        }
        return derived;
    }

    private boolean derive(Map<String, SourceRange> derived, IrInstruction instruction) {
        return switch (instruction) {
            case IrAddressOfLocalInstruction address when captures.contains(address.local().range()) ->
                    add(derived, address.result().name(), address.local().range());
            case IrElementAddressInstruction element when element.baseAddress() instanceof IrTemporary base
                    && derived.containsKey(base.name()) -> add(derived, element.result().name(), derived.get(base.name()));
            case IrFieldAddressInstruction field when field.baseAddress() instanceof IrTemporary base
                    && derived.containsKey(base.name()) -> add(derived, field.result().name(), derived.get(base.name()));
            default -> false;
        };
    }

    private static boolean add(Map<String, SourceRange> derived, String name, SourceRange definition) {
        if (derived.containsKey(name)) return false;
        derived.put(name, definition);
        return true;
    }

    /** 元素访问：地址临时量在运行期求值，投影器按 offset 命中具体槽位。 */
    private static List<IrCaptureInstruction> element(Map<String, SourceRange> derived, String temporary,
                                                       IrCaptureInstruction.Kind kind, SourceRange range) {
        return List.of(new IrCaptureInstruction(kind, derived.get(temporary), range, temporary));
    }

    private static List<IrCaptureInstruction> capture(Set<SourceRange> captures, SourceRange definition,
                                                      IrCaptureInstruction.Kind kind, SourceRange range) {
        if (definition == null || !captures.contains(definition)) return List.of();
        return List.of(new IrCaptureInstruction(kind, definition, range));
    }

    /** 函数体里所有被选中的局部变量定义；返回指令前按声明顺序补发离开作用域事件。 */
    private Set<SourceRange> capturedLocals(IrFunction function, Map<SourceRange, IrLocal> knownLocals) {
        if (captures.isEmpty()) return Set.of();
        LinkedHashSet<SourceRange> removals = new LinkedHashSet<>();
        for (IrBlock block : function.blocks()) for (IrInstruction instruction : block.instructions())
            if (instruction instanceof IrDeclareLocalInstruction declaration) {
                knownLocals.putIfAbsent(declaration.local().range(), declaration.local());
                if (captures.contains(declaration.local().range())) removals.add(declaration.local().range());
            }
        return removals;
    }

    private static void index(
            String function,
            String block,
            List<IrInstruction> instructions,
            List<List<Location>> lines,
            int lineCount
    ) {
        for (int index = 0; index < instructions.size(); index++) {
            IrInstruction instruction = instructions.get(index);
            int firstLine = instruction.range().startLine();
            int lastLine = instruction.range().endLine();
            if (instruction.range().endByte() == 0 && lastLine > firstLine) lastLine--;
            for (int line = firstLine; line <= Math.min(lastLine, lineCount); line++) {
                lines.get(line).add(new Location(function, block, index, instruction));
            }
        }
    }

    public IrResult ir() { return ir; }

    /** 预处理阶段扫描出的变量标记；按定义范围与源码名双索引。 */
    public DebugVariableRegistry variables() { return variables; }

    /** 本次“开始”选中的捕获变量定义范围。 */
    public Set<SourceRange> captureDefinitions() { return captures; }

    /** 捕获定义对应的局部存储槽（形参与全局变量为空）。 */
    public Optional<IrLocal> localFor(SourceRange definition) { return Optional.ofNullable(locals.get(definition)); }

    /** 捕获定义对应的全局符号（局部变量为空）。 */
    public Optional<String> globalFor(SourceRange definition) { return Optional.ofNullable(globals.get(definition)); }

    /** 第 0 项留空；lines().get(n) 对应 IDE 中第 n 行，包括插入的 trap。 */
    public List<List<Location>> lines() { return lines; }

    public List<Location> line(int n) {
        return n > 0 && n < lines.size() ? lines.get(n) : List.of();
    }

    public record Location(String function, String block, int index, IrInstruction instruction) {
        public String id() { return block + "#" + index; }
    }
}
