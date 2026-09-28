package minic.compiler.asm;

import minic.compiler.Loop;
import minic.compiler.Stage;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrStringData;
import minic.source.SourceRange;

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
    private Input input;
    private final Work work = new Work();
    private Section section = Section.HEADER_PUBLIC;
    private int externalIndex;
    private int stringDataIndex;
    private List<String> pendingStringDataLines = List.of();
    private int pendingStringDataLineIndex;
    private String pendingStringDataLabel = "";
    private int entryLineIndex;
    private int functionIndex;
    private FunctionState functionState;
    private boolean completed;
    private String currentLine;
    private String currentSubject = "";
    private SourceRange currentRange;
    private AsmResult result;

    /** 使用 IR 阶段最终结果创建 asm 阶段。 */
    public Assembler(IrResult irResult) {
        irStage = null;
        input = new Input(irResult);
    }

    /** 创建由 IR 阶段提供输入的 asm 阶段。 */
    public Assembler(IrLowerer irStage) {
        this.irStage = Objects.requireNonNull(irStage, "irStage");
    }

    /** 执行当前输入的完整 Asm 阶段。 */
    public AsmResult assemble() {
        new Loop(List.of(this)).run();
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

    @Override
    public boolean succeeded() {
        return completed && result != null;
    }

    /** 执行并产出一行汇编。 */
    @Override
    public SourceRange step() {
        if (!canNext()) {
            throw new IllegalStateException("asm state is already completed");
        }
        ensureInitialized();
        currentLine = null;
        currentSubject = "";
        currentRange = null;
        while (true) {
            switch (section) {
                case HEADER_PUBLIC -> {
                    section = Section.HEADER_EXIT_PROCESS;
                    return emit("header", CallingConvention.ENTRY_SYMBOL,
                            "PUBLIC " + CallingConvention.ENTRY_SYMBOL, null);
                }
                case HEADER_EXIT_PROCESS -> {
                    section = input.irResult.externalFunctionNames().isEmpty() ? Section.CONST_SECTION : Section.EXTERNS;
                    return emit("header", "ExitProcess", "EXTERN ExitProcess:PROC", null);
                }
                case EXTERNS -> {
                    if (externalIndex < input.irResult.externalFunctionNames().size()) {
                        String externalName = input.externalFunctionNames.get(externalIndex++);
                        return emit("header", externalName, "EXTERN " + externalName + ":PROC", null);
                    }
                    section = Section.CONST_SECTION;
                }
                case CONST_SECTION -> {
                    section = input.irResult.stringData().isEmpty() ? Section.CODE_SECTION : Section.STRING_DATA;
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
                    return null;
                }
            }
        }
    }

    /**
     * 返回当前汇编行。
     *
     * @return 当前汇编行 Optional
     */
    public Optional<String> currentLine() {
        return Optional.ofNullable(currentLine);
    }

    /** 返回当前汇编行所属主题。 */
    public String currentSubject() {
        return currentSubject;
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
        if (irStage.canNext()) {
            throw new IllegalStateException("IR stage has not completed");
        }
        if (!irStage.succeeded()) {
            throw new IllegalStateException("IR stage did not succeed");
        }
        input = new Input(irStage.result());
    }

    private boolean nextFunctionLine() {
        while (functionIndex < input.irResult.functions().size()) {
            if (functionState == null) {
                functionState = new FunctionState(input.irResult.functions().get(functionIndex), input.irResult.externalFunctionNames());
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
        currentLine = text;
        currentSubject = subject;
        currentRange = sourceRange;
        work.assemblyLines.add(text);
        work.sourceRanges.add(sourceRange);
        work.currentSection = section;
        return sourceRange;
    }

    private static List<String> entryPointLines() {
        return List.of(
                CallingConvention.ENTRY_SYMBOL + " PROC",
                "    sub rsp, 40",
                "    call " + CallingConvention.USER_MAIN_SYMBOL,
                "    mov ecx, eax",
                "    call ExitProcess",
                CallingConvention.ENTRY_SYMBOL + " ENDP"
        );
    }

    private static List<String> formatStringDataLines(IrStringData stringData) {
        ArrayList<Integer> bytes = new ArrayList<>();
        for (int index = 0; index < stringData.value().length(); index++) {
            bytes.add((int) stringData.value().charAt(index));
        }
        bytes.add(0);

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
        private final ArrayDeque<PendingInstructionLine> pendingInstructionLines = new ArrayDeque<>();
        private FunctionSection section = FunctionSection.PROC;
        private int blockIndex;
        private int instructionIndex;
        private int trapIndex;
        private SourceRange currentRange;

        private FunctionState(IrFunction function, java.util.Set<String> externalFunctionNames) {
            this.function = function;
            frame = FrameLayout.create(function);
            functionSymbol = CallingConvention.functionDefinitionSymbol(function.name());
            epilogueLabel = functionSymbol + "$epilogue";
            instructionEmitter = new InstructionEmitter(frame, externalFunctionNames);
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
                        section = frame.frameSize() > 0 ? FunctionSection.PROLOG_SUB : FunctionSection.PARAMETER_STORES;
                        return structure("    mov rbp, rsp");
                    }
                    case PROLOG_SUB -> {
                        section = FunctionSection.PARAMETER_STORES;
                        return structure("    sub rsp, " + frame.frameSize());
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
                        section = FunctionSection.EPILOGUE_MOV;
                        return structure(epilogueLabel + ":");
                    }
                    case EPILOGUE_MOV -> {
                        section = FunctionSection.EPILOGUE_POP;
                        return structure("    mov rsp, rbp");
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

        private void enqueueParameterStores() {
            StringBuilder builder = new StringBuilder();
            instructionEmitter.emitParameterStores(builder, function);
            splitLines(builder.toString()).stream()
                    .map(line -> new PendingInstructionLine(line, function.range()))
                    .forEach(pendingInstructionLines::add);
        }

        private void enqueueInstruction(IrInstruction instruction) {
            StringBuilder builder = new StringBuilder();
            instructionEmitter.emitInstruction(builder, functionSymbol, epilogueLabel, instruction);
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

        private record PendingInstructionLine(String text, minic.source.SourceRange sourceRange) {
        }
    }

    private enum FunctionSection {
        PROC,
        PROLOG_PUSH,
        PROLOG_MOV,
        PROLOG_SUB,
        PARAMETER_STORES,
        BLOCKS,
        TRAPS,
        EPILOGUE_LABEL,
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
    public record Input(IrResult irResult, List<String> externalFunctionNames) {
        /**
         * 创建输入数据。
         *
         * @param irResult IR 阶段最终结果
         * @param externalFunctionNames 外部函数名列表
         */
        public Input {
            Objects.requireNonNull(irResult, "irResult");
            Objects.requireNonNull(externalFunctionNames, "externalFunctionNames");
            externalFunctionNames = List.copyOf(externalFunctionNames);
        }

        private Input(IrResult irResult) {
            this(irResult, irResult.externalFunctionNames().stream().toList());
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
