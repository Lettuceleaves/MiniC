package minic.compiler.asm;

import minic.compiler.CompilerApi;
import minic.compiler.Stage;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.ir.optimize.GlobalRegisterPlan;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrStringData;
import minic.compiler.ir.model.IrGlobalData;
import minic.SourceRange;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 可逐行执行的 Asm 阶段。
 */
public final class Assembler extends Stage {
    private final IrLowerer irStage;
    private final IrResult providedIr;
    private final IrOptimizationPipeline optimizationPipeline;
    private IrOptimizationPipeline.Result optimizationResult;
    private Input input;
    private final Work work = new Work();
    private Section section = Section.HEADER_PUBLIC;
    private int externalIndex;
    private int externalObjectIndex;
    private int stringDataIndex;
    private List<String> pendingStringDataLines = List.of();
    private int pendingStringDataLineIndex;
    private String pendingStringDataLabel = "";
    private int globalDataIndex;
    private List<String> pendingGlobalDataLines = List.of();
    private int pendingGlobalDataLineIndex;
    private String pendingGlobalDataLabel = "";
    private int entryLineIndex;
    private int functionIndex;
    private FunctionState functionState;
    private boolean completed;
    private SourceRange currentRange;
    private AsmResult result;

    /** 使用 IR 阶段最终结果创建 asm 阶段。 */
    public Assembler(IrResult irResult) {
        this(irResult, OptimizationLevel.BASELINE);
    }

    public Assembler(IrResult irResult, OptimizationLevel level) {
        this(irResult, IrOptimizationPipeline.forLevel(level));
    }

    public Assembler(IrResult irResult, IrOptimizationPipeline optimizationPipeline) {
        this.irStage = null;
        this.providedIr = Objects.requireNonNull(irResult, "irResult");
        this.optimizationPipeline = Objects.requireNonNull(optimizationPipeline, "optimizationPipeline");
    }

    /** 创建由 IR 阶段提供输入的 asm 阶段。 */
    public Assembler(IrLowerer irStage) {
        this(irStage, OptimizationLevel.BASELINE);
    }

    public Assembler(IrLowerer irStage, OptimizationLevel level) {
        this(irStage, IrOptimizationPipeline.forLevel(level));
    }

    public Assembler(IrLowerer irStage, IrOptimizationPipeline optimizationPipeline) {
        this.irStage = Objects.requireNonNull(irStage, "irStage");
        this.providedIr = null;
        this.optimizationPipeline = Objects.requireNonNull(optimizationPipeline, "optimizationPipeline");
    }

    public OptimizationLevel optimizationLevel() { return optimizationPipeline.level(); }

    /** The verified native input and passes actually applied; requires completed source IR. */
    public IrOptimizationPipeline.Result optimizationResult() {
        ensureInitialized();
        return optimizationResult;
    }

    /** 执行当前输入的完整 Asm 阶段。 */
    public AsmResult assemble() {
        new CompilerApi(List.of(this)).run();
        return result();
    }

    public Input input() {
        ensureInitialized();
        return input;
    }

    public Work work() {
        return work;
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    /** 执行并产出一行汇编。 */
    @Override
    public SourceRange step() {
        if (!canNext()) {
            throw new IllegalStateException("asm state is already completed");
        }
        ensureInitialized();
        currentRange = null;
        while (true) {
            switch (section) {
                case HEADER_PUBLIC -> {
                    section = Section.HEADER_EXIT_PROCESS;
                    return emit("header", CallingConvention.ENTRY_SYMBOL,
                            "PUBLIC " + CallingConvention.ENTRY_SYMBOL, null);
                }
                case HEADER_EXIT_PROCESS -> {
                    section = input.externalFunctionNames.isEmpty() && input.externalObjectNames.isEmpty()
                            ? Section.CONST_SECTION : Section.EXTERNS;
                    return emit("header", "ExitProcess", "EXTERN ExitProcess:PROC", null);
                }
                case EXTERNS -> {
                    if (externalIndex < input.irResult.externalFunctionNames().size()) {
                        String externalName = input.externalFunctionNames.get(externalIndex++);
                        return emit("header", externalName, "EXTERN " + externalName + ":PROC", null);
                    }
                    if (externalObjectIndex < input.externalObjectNames.size()) {
                        String externalName = input.externalObjectNames.get(externalObjectIndex++);
                        return emit("header", externalName, "EXTERN " + externalName + ":BYTE", null);
                    }
                    section = Section.CONST_SECTION;
                }
                case CONST_SECTION -> {
                    section = input.irResult.stringData().isEmpty()
                            ? (input.irResult.globalData().isEmpty() ? Section.CODE_SECTION : Section.DATA_SECTION)
                            : Section.STRING_DATA;
                    if (!input.irResult.stringData().isEmpty()) {
                        return emit("const", ".const", ".const", null);
                    }
                }
                case STRING_DATA -> {
                    if (pendingStringDataLineIndex < pendingStringDataLines.size()) {
                        return emit(
                                "const",
                                pendingStringDataLabel,
                                pendingStringDataLines.get(pendingStringDataLineIndex++),
                                null
                        );
                    }
                    if (stringDataIndex < input.irResult.stringData().size()) {
                        IrStringData stringData = input.irResult.stringData().get(stringDataIndex++);
                        pendingStringDataLines = formatStringDataLines(stringData);
                        pendingStringDataLineIndex = 0;
                        pendingStringDataLabel = stringData.label();
                        return emit(
                                "const",
                                pendingStringDataLabel,
                                pendingStringDataLines.get(pendingStringDataLineIndex++),
                                null
                        );
                    }
                    section = input.irResult.globalData().isEmpty() ? Section.CODE_SECTION : Section.DATA_SECTION;
                }
                case DATA_SECTION -> {
                    section = Section.GLOBAL_DATA;
                    return emit("data", ".data", ".data", null);
                }
                case GLOBAL_DATA -> {
                    if (pendingGlobalDataLineIndex < pendingGlobalDataLines.size()) {
                        return emit("data", pendingGlobalDataLabel,
                                pendingGlobalDataLines.get(pendingGlobalDataLineIndex++), null);
                    }
                    if (globalDataIndex < input.irResult.globalData().size()) {
                        IrGlobalData data = input.irResult.globalData().get(globalDataIndex++);
                        pendingGlobalDataLines = formatGlobalDataLines(data);
                        pendingGlobalDataLineIndex = 0;
                        pendingGlobalDataLabel = data.label();
                        return emit("data", pendingGlobalDataLabel,
                                pendingGlobalDataLines.get(pendingGlobalDataLineIndex++), null);
                    }
                    section = Section.CODE_SECTION;
                }
                case CODE_SECTION -> {
                    section = Section.ENTRY_POINT;
                    return emit("code", ".code", ".code", null);
                }
                case ENTRY_POINT -> {
                    List<String> entryLines = entryPointLines();
                    if (entryLineIndex < entryLines.size()) {
                        return emit("code", CallingConvention.ENTRY_SYMBOL,
                                entryLines.get(entryLineIndex++), null);
                    }
                    section = Section.FUNCTIONS;
                }
                case FUNCTIONS -> {
                    if (nextFunctionLine()) {
                        return currentRange;
                    }
                    section = Section.END;
                }
                case END -> {
                    section = Section.COMPLETE;
                    return emit("end", "end", "END", null);
                }
                case COMPLETE -> {
                    result = new AsmResult(
                            CallingConvention.ENTRY_SYMBOL,
                            work.assemblyText()
                    );
                    completed = true;
                    return finishStep(null, "", List.of(), () -> result);
                }
            }
        }
    }

    /** 返回 Asm 阶段最终结果。 */
    public AsmResult result() {
        if (result == null) {
            throw new IllegalStateException("asm result is not available before assembly completes");
        }
        return result;
    }

    private void ensureInitialized() {
        if (input != null) {
            return;
        }
        if (irStage != null && irStage.canNext()) {
            throw new IllegalStateException("IR stage has not completed");
        }
        if (irStage != null && !irStage.succeeded()) {
            throw new IllegalStateException("IR stage did not succeed");
        }
        optimizationResult = optimizationPipeline.apply(irStage == null ? providedIr : irStage.result());
        input = new Input(optimizationResult.ir());
    }

    private boolean nextFunctionLine() {
        while (functionIndex < input.irResult.functions().size()) {
            if (functionState == null) {
                functionState = new FunctionState(input.irResult.functions().get(functionIndex), input.irResult.externalFunctionNames(),
                        optimizationLevel() == OptimizationLevel.OPTIMIZED);
                work.currentFunctionName = functionState.function.name();
                work.currentFrameLayout = functionState.frame;
                work.currentSection = "function";
            }
            String line = functionState.nextLine();
            if (line != null) {
                emit("code", functionState.function.name(), line, functionState.currentRange());
                return true;
            }
            functionState = null;
            functionIndex++;
        }
        work.currentFunctionName = "";
        work.currentFrameLayout = null;
        work.currentSection = "end";
        return false;
    }

    private SourceRange emit(String section, String subject, String text, SourceRange sourceRange) {
        currentRange = sourceRange;
        work.assemblyLines.add(text);
        work.sourceRanges.add(sourceRange);
        work.currentSection = section;
        String operation = "EMIT_" + section.toUpperCase(java.util.Locale.ROOT) + ":" + subject;
        return finishStep(
                sourceRange,
                operation,
                List.of(),
                () -> new AsmResult(CallingConvention.ENTRY_SYMBOL, work.assemblyText())
        );
    }

    private List<String> entryPointLines() {
        return List.of(
                CallingConvention.ENTRY_SYMBOL + " PROC",
                "    sub rsp, 40",
                "    call " + CallingConvention.functionDefinitionSymbol(input.irResult.entryFunction()),
                "    mov ecx, eax",
                "    call ExitProcess",
                CallingConvention.ENTRY_SYMBOL + " ENDP"
        );
    }

    private static List<String> formatStringDataLines(IrStringData stringData) {
        ArrayList<Integer> bytes = new ArrayList<>();
        for (byte value : stringData.bytes()) bytes.add(Byte.toUnsignedInt(value));

        ArrayList<String> lines = new ArrayList<>();
        int index = 0;
        boolean firstLine = true;
        while (index < bytes.size()) {
            int end = Math.min(index + 16, bytes.size());
            String prefix = firstLine ? stringData.label() + " BYTE " : "    BYTE ";
            lines.add(prefix + bytes.subList(index, end).stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", ")));
            index = end;
            firstLine = false;
        }
        return List.copyOf(lines);
    }

    private List<String> formatGlobalDataLines(IrGlobalData data) {
        var lines=new ArrayList<String>();
        if(data.alignment()>1)lines.add("ALIGN "+data.alignment());
        byte[] bytes=data.bytes();int cursor=0,reference=0;boolean first=true;
        while(cursor<bytes.length) {
            var address=reference<data.addresses().size()?data.addresses().get(reference):null;
            String prefix=first?data.label()+" ":"    ";
            if(address!=null&&address.offset()==cursor) {
                String symbol=address.kind()==IrGlobalData.AddressKind.FUNCTION
                        ?CallingConvention.callSymbol(address.symbol(),input.irResult.externalFunctionNames().contains(address.symbol())):address.symbol();
                String addend=address.addend()>0?" + "+address.addend():address.addend()<0?" - "+java.math.BigInteger.valueOf(address.addend()).negate():"";
                lines.add(prefix+"QWORD OFFSET "+symbol+addend);cursor+=Long.BYTES;reference++;
            } else {
                int end=Math.min(cursor+16,address==null?bytes.length:address.offset());
                var values=new ArrayList<String>();while(cursor<end)values.add(Integer.toString(Byte.toUnsignedInt(bytes[cursor++])));
                lines.add(prefix+"BYTE "+String.join(", ",values));
            }
            first=false;
        }
        return List.copyOf(lines);
    }

    private static List<String> formatByteDataLines(String label, byte[] data) {
        ArrayList<Integer> bytes = new ArrayList<>(data.length);
        for (byte value : data) bytes.add(Byte.toUnsignedInt(value));
        ArrayList<String> lines = new ArrayList<>();
        for (int index = 0; index < bytes.size(); index += 16) {
            int end = Math.min(index + 16, bytes.size());
            String prefix = index == 0 ? label + " BYTE " : "    BYTE ";
            lines.add(prefix + bytes.subList(index, end).stream()
                    .map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")));
        }
        return List.copyOf(lines);
    }

    private static List<String> splitLines(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        ArrayList<String> lines = new ArrayList<>();
        for (String line : normalized.split("\n", -1)) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private enum Section {
        HEADER_PUBLIC,
        HEADER_EXIT_PROCESS,
        EXTERNS,
        CONST_SECTION,
        STRING_DATA,
        DATA_SECTION,
        GLOBAL_DATA,
        CODE_SECTION,
        ENTRY_POINT,
        FUNCTIONS,
        END,
        COMPLETE
    }

    private static final class FunctionState {
        private final IrFunction function;
        private final FrameLayout frame;
        private final String functionSymbol;
        private final String epilogueLabel;
        private final InstructionEmitter instructionEmitter;
        private final GlobalRegisterPlan registerPlan;
        private final ValueEmitter stackValues;
        private final ArrayDeque<PendingInstructionLine> pendingInstructionLines = new ArrayDeque<>();
        private FunctionSection section = FunctionSection.PROC;
        private int blockIndex;
        private int instructionIndex;
        private int trapIndex;
        private SourceRange currentRange;

        private FunctionState(IrFunction function, java.util.Set<String> externalFunctionNames, boolean optimizeValueLocations) {
            this.function = function;
            registerPlan = optimizeValueLocations ? GlobalRegisterPlan.allocate(function, true) : null;
            frame = FrameLayout.create(function, optimizeValueLocations).withCalleeSavedRegisters(
                    registerPlan == null ? List.of() : registerPlan.calleeSavedRegisters());
            functionSymbol = CallingConvention.functionDefinitionSymbol(function.name());
            epilogueLabel = functionSymbol + "$epilogue";
            TemporaryLocations locations = TemporaryLocations.allStack(frame);
            stackValues = new ValueEmitter(frame, externalFunctionNames);
            if (optimizeValueLocations) {
                var assignments = new java.util.LinkedHashMap<String, ValueLocation>();
                registerPlan.registers().forEach((name, register) -> assignments.put(name,
                        new ValueLocation.Register(registerPlan.temporaryTypes().get(name), register)));
                locations = TemporaryLocations.withOverrides(frame, assignments);
            }
            instructionEmitter = new InstructionEmitter(frame, externalFunctionNames, function, locations, optimizeValueLocations);
        }

        private String nextLine() {
            while (true) {
                if (!pendingInstructionLines.isEmpty()) {
                    PendingInstructionLine pendingLine = pendingInstructionLines.removeFirst();
                    currentRange = pendingLine.sourceRange();
                    return pendingLine.text();
                }
                switch (section) {
                    case PROC -> {
                        section = FunctionSection.PROLOG_PUSH;
                        return structure(functionSymbol + " PROC");
                    }
                    case PROLOG_PUSH -> {
                        section = FunctionSection.PROLOG_MOV;
                        return structure("    push rbp");
                    }
                    case PROLOG_MOV -> {
                        if (frame.realigned()) {
                            enqueueRealignedPrologue();
                            section = FunctionSection.CALLEE_SAVES;
                            continue;
                        }
                        section = frame.frameSize() > 0 ? FunctionSection.PROLOG_SUB : FunctionSection.CALLEE_SAVES;
                        return structure("    mov rbp, rsp");
                    }
                    case PROLOG_SUB -> {
                        section = FunctionSection.CALLEE_SAVES;
                        if (frame.frameSize() >= 4096) {
                            enqueueStackProbe();
                            continue;
                        }
                        return structure("    sub rsp, " + frame.frameSize());
                    }
                    case CALLEE_SAVES -> {
                        enqueueCalleeSavedTransfers(false);
                        section = FunctionSection.PARAMETER_STORES;
                    }
                    case PARAMETER_STORES -> {
                        enqueueParameterStores();
                        section = FunctionSection.BLOCKS;
                    }
                    case BLOCKS -> {
                        String blockLine = nextBlockLine();
                        if (blockLine != null) {
                            return blockLine;
                        }
                        section = FunctionSection.TRAPS;
                    }
                    case TRAPS -> {
                        if (trapIndex < 2) {
                            enqueueTrap(trapIndex++);
                        } else {
                            section = FunctionSection.EPILOGUE_LABEL;
                        }
                    }
                    case EPILOGUE_LABEL -> {
                        section = FunctionSection.CALLEE_RESTORES;
                        return structure(epilogueLabel + ":");
                    }
                    case CALLEE_RESTORES -> {
                        enqueueCalleeSavedTransfers(true);
                        section = FunctionSection.EPILOGUE_MOV;
                    }
                    case EPILOGUE_MOV -> {
                        section = FunctionSection.EPILOGUE_POP;
                        return structure(frame.realigned() ? "    mov rsp, " + frame.originalFrameSlot() : "    mov rsp, rbp");
                    }
                    case EPILOGUE_POP -> {
                        section = FunctionSection.EPILOGUE_RET;
                        return structure("    pop rbp");
                    }
                    case EPILOGUE_RET -> {
                        section = FunctionSection.ENDP;
                        return structure("    ret");
                    }
                    case ENDP -> {
                        section = FunctionSection.DONE;
                        return structure(functionSymbol + " ENDP");
                    }
                    case DONE -> {
                        return null;
                    }
                }
            }
        }

        private String nextBlockLine() {
            while (blockIndex < function.blocks().size()) {
                IrBlock block = function.blocks().get(blockIndex);
                if (instructionIndex == 0 && !"entry".equals(block.label())) {
                    instructionIndex = -1;
                    return structure(instructionEmitter.blockSymbol(functionSymbol, block.label()) + ":");
                }
                if (instructionIndex == -1) {
                    instructionIndex = 0;
                }
                if (instructionIndex < block.instructions().size()) {
                    IrInstruction instruction = block.instructions().get(instructionIndex++);
                    enqueueInstruction(instruction);
                    if (!pendingInstructionLines.isEmpty()) {
                        PendingInstructionLine pendingLine = pendingInstructionLines.removeFirst();
                        currentRange = pendingLine.sourceRange();
                        return pendingLine.text();
                    }
                    continue;
                }
                blockIndex++;
                instructionIndex = 0;
            }
            return null;
        }

        private String structure(String text) {
            currentRange = null;
            return text;
        }

        private SourceRange currentRange() {
            return currentRange;
        }

        private void enqueueRealignedPrologue() {
            String probe = functionSymbol + "$align_probe", done = functionSymbol + "$align_probe_done";
            // RAX/R10/R11 are volatile and are not Win64 argument registers. Preserve the original
            // frame for incoming stack arguments, va_start, and exact stack restoration.
            // Probe each intervening guard page before publishing the new stack pointer.
            // Win64 user addresses are nonnegative, so the signed comparison is equivalent here.
            List<String> lines = List.of("    mov rax, rsp", "    mov r11, rsp",
                    "    and r11, -" + frame.stackAlignment(), "    mov rbp, r11",
                    "    sub r11, " + frame.frameSize(), "    mov r10, rsp",
                    probe + ":", "    sub r10, 4096", "    cmp r11, r10", "    jge " + done,
                    "    mov BYTE PTR [r10], 0", "    jmp " + probe, done + ":",
                    "    mov BYTE PTR [r11], 0", "    mov rsp, r11",
                    "    mov " + frame.originalFrameSlot() + ", rax");
            lines.forEach(line -> pendingInstructionLines.add(new PendingInstructionLine(line, null)));
        }

        private void enqueueStackProbe() {
            // Ordinary frames can cross guard pages too. Only volatile scratch registers
            // are used, preserving register/stack arguments and the incoming frame pointer.
            // https://learn.microsoft.com/en-us/cpp/build/prolog-and-epilog
            String probe = functionSymbol + "$stack_probe", done = functionSymbol + "$stack_probe_done";
            List<String> lines = List.of("    mov r11, rsp", "    sub r11, " + frame.frameSize(),
                    "    mov r10, rsp", probe + ":", "    sub r10, 4096", "    cmp r11, r10",
                    "    jge " + done, "    mov BYTE PTR [r10], 0", "    jmp " + probe,
                    done + ":", "    mov BYTE PTR [r11], 0", "    mov rsp, r11");
            lines.forEach(line -> pendingInstructionLines.add(new PendingInstructionLine(line, null)));
        }

        private void enqueueCalleeSavedTransfers(boolean restore) {
            frame.calleeSavedOffsets().forEach((register, offset) -> {
                String slot = "QWORD PTR " + frame.stackAddress(offset);
                String operands = restore ? register + ", " + slot : slot + ", " + register;
                pendingInstructionLines.add(new PendingInstructionLine("    mov " + operands, null));
            });
        }

        private void enqueueParameterStores() {
            StringBuilder builder = new StringBuilder();
            instructionEmitter.emitParameterStores(builder, function);
            splitLines(builder.toString()).stream()
                    .map(line -> new PendingInstructionLine(line, function.range()))
                    .forEach(pendingInstructionLines::add);
        }

        private void enqueueInstruction(IrInstruction instruction) {
            StringBuilder builder = new StringBuilder();
            var survivors = registerPlan == null ? List.<minic.compiler.ir.value.IrValue.IrTemporary>of()
                    : registerPlan.spillsAt(function.blocks().get(blockIndex).label(), instructionIndex - 1);
            for (var value : survivors) stackValues.emitStoreTemporary(builder, value, registerPlan.registers().get(value.name()));
            instructionEmitter.emitInstruction(builder, functionSymbol, epilogueLabel, instruction);
            // The instruction includes placement of the NEW call result. Restore only old values
            // live across this call, never a same-name result's previous version.
            for (var value : survivors) stackValues.emitLoadValue(builder, value, registerPlan.registers().get(value.name()));
            splitLines(builder.toString()).stream()
                    .map(line -> new PendingInstructionLine(line, instruction.range()))
                    .forEach(pendingInstructionLines::add);
        }

        private void enqueueTrap(int index) {
            StringBuilder builder = new StringBuilder();
            if (index == 0) {
                instructionEmitter.emitFunctionTrap(builder, functionSymbol, "uninitialized", 101, epilogueLabel);
            } else {
                instructionEmitter.emitFunctionTrap(builder, functionSymbol, "divide_by_zero", 102, epilogueLabel);
            }
            splitLines(builder.toString()).stream()
                    .map(line -> new PendingInstructionLine(line, function.range()))
                    .forEach(pendingInstructionLines::add);
        }

        private record PendingInstructionLine(String text, SourceRange sourceRange) {
        }
    }

    private enum FunctionSection {
        PROC,
        PROLOG_PUSH,
        PROLOG_MOV,
        PROLOG_SUB,
        CALLEE_SAVES,
        PARAMETER_STORES,
        BLOCKS,
        TRAPS,
        EPILOGUE_LABEL,
        CALLEE_RESTORES,
        EPILOGUE_MOV,
        EPILOGUE_POP,
        EPILOGUE_RET,
        ENDP,
        DONE
    }

    /**
     * Asm 阶段输入数据。
     *
     * @param irResult IR 阶段最终结果
     */
    public record Input(IrResult irResult, List<String> externalFunctionNames, List<String> externalObjectNames) {
        /**
         * 创建输入数据。
         *
         * @param irResult IR 阶段最终结果
         * @param externalFunctionNames 外部函数名列表
         */
        public Input {
            Objects.requireNonNull(irResult, "irResult");
            Objects.requireNonNull(externalFunctionNames, "externalFunctionNames");
            Objects.requireNonNull(externalObjectNames, "externalObjectNames");
            externalFunctionNames = List.copyOf(externalFunctionNames);
            externalObjectNames = List.copyOf(externalObjectNames);
        }

        private Input(IrResult irResult) {
            this(irResult, irResult.externalFunctionNames().stream().toList(),
                    irResult.externalObjectNames().stream().toList());
        }
    }

    /**
     * Asm 阶段内部工作数据。
     */
    public static final class Work {
        private final ArrayList<String> assemblyLines = new ArrayList<>();
        private final ArrayList<SourceRange> sourceRanges = new ArrayList<>();
        private String currentSection = "header";
        private String currentFunctionName = "";
        private FrameLayout currentFrameLayout;

        /**
         * 返回已产出汇编行数。
         *
         * @return 汇编行数
         */
        public int completedLineCount() {
            return assemblyLines.size();
        }

        /**
         * 返回当前 section 摘要。
         *
         * @return 当前 section
         */
        public String currentSection() {
            return currentSection;
        }

        /**
         * 返回当前函数名称。
         *
         * @return 当前函数名称；不在函数内时为空字符串
         */
        public String currentFunctionName() {
            return currentFunctionName;
        }

        /**
         * 返回当前函数 frame layout。
         *
         * @return 当前 frame layout Optional
         */
        public Optional<FrameLayout> currentFrameLayout() {
            return Optional.ofNullable(currentFrameLayout);
        }

        /**
         * 返回已产出汇编行摘要。
         *
         * @return 汇编行摘要
         */
        public List<String> assemblyLines() {
            return List.copyOf(assemblyLines);
        }

        /**
         * 返回指定汇编行对应的源码范围。
         *
         * @param index 从零开始的汇编行索引
         * @return 源码范围；结构行没有直接源码映射时为空
         */
        public Optional<SourceRange> sourceRangeAt(int index) {
            return Optional.ofNullable(sourceRanges.get(index));
        }

        private String assemblyText() {
            return String.join(System.lineSeparator(), assemblyLines) + System.lineSeparator();
        }
    }

}
