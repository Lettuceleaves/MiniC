package minic.compiler.codegen.windows;

import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.target.TargetPlatform;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.model.IrStringData;
import minic.compiler.stage.CompilerStageInput;
import minic.compiler.stage.CompilerStageOutput;
import minic.compiler.stage.CompilerStageResult;
import minic.compiler.stage.CompilerStageSnapshot;
import minic.compiler.stage.CompilerStageState;
import minic.compiler.stage.CompilerStageStatus;
import minic.compiler.stage.CompilerStageWork;
import minic.runtime.step.CompileStage;
import minic.runtime.step.StageProgress;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 可正向步进的 Windows x64 codegen 状态。
 */
public final class WindowsX64CodegenStepState implements CompilerStageState<
        WindowsX64CodegenStepState.Input,
        WindowsX64CodegenStepState.Work,
        WindowsX64CodegenStepState.Output> {
    private final Input input;
    private final Work work = new Work();
    private Section section = Section.HEADER_TARGET;
    private int externalIndex;
    private int stringDataIndex;
    private List<String> pendingStringDataLines = List.of();
    private int pendingStringDataLineIndex;
    private String pendingStringDataLabel = "";
    private int entryLineIndex;
    private int functionIndex;
    private FunctionState functionState;
    private boolean completed;
    private WindowsX64AssemblyLine currentLine;

    /**
     * 创建 Windows x64 codegen 步进状态。
     *
     * @param module IR 模块
     */
    public WindowsX64CodegenStepState(IrModule module) {
        input = new Input(module);
    }

    @Override
    public CompileStage stage() {
        return CompileStage.CODEGEN;
    }

    @Override
    public Input input() {
        return input;
    }

    @Override
    public Work work() {
        return work;
    }

    @Override
    public CompilerStageSnapshot snapshot() {
        CompilerStageStatus status = completed
                ? CompilerStageStatus.COMPLETED
                : work.completedLineCount() == 0 ? CompilerStageStatus.NOT_STARTED : CompilerStageStatus.RUNNING;
        return new CompilerStageSnapshot(
                CompileStage.CODEGEN,
                status,
                new StageProgress(work.completedLineCount(), -1, completed),
                currentLine == null ? "" : currentLine.kind() + " " + currentLine.subject() + " " + currentLine.text(),
                List.of()
        );
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    /**
     * 推进并产出一行汇编。
     *
     * @return 本步汇编行
     */
    public WindowsX64AssemblyLine next() {
        if (!canNext()) {
            throw new IllegalStateException("codegen state is already completed");
        }
        while (true) {
            switch (section) {
                case HEADER_TARGET -> {
                    section = Section.HEADER_PUBLIC;
                    return emit(WindowsX64AssemblyLineKind.HEADER, "target", "; target: " + TargetPlatform.WINDOWS_X86_64.id());
                }
                case HEADER_PUBLIC -> {
                    section = Section.HEADER_EXIT_PROCESS;
                    return emit(WindowsX64AssemblyLineKind.HEADER, WindowsX64CallingConvention.ENTRY_SYMBOL,
                            "PUBLIC " + WindowsX64CallingConvention.ENTRY_SYMBOL);
                }
                case HEADER_EXIT_PROCESS -> {
                    section = input.module.externalFunctionNames().isEmpty() ? Section.CONST_SECTION : Section.EXTERNS;
                    return emit(WindowsX64AssemblyLineKind.HEADER, "ExitProcess", "EXTERN ExitProcess:PROC");
                }
                case EXTERNS -> {
                    if (externalIndex < input.module.externalFunctionNames().size()) {
                        String externalName = input.externalFunctionNames.get(externalIndex++);
                        return emit(WindowsX64AssemblyLineKind.HEADER, externalName, "EXTERN " + externalName + ":PROC");
                    }
                    section = Section.CONST_SECTION;
                }
                case CONST_SECTION -> {
                    section = input.module.stringData().isEmpty() ? Section.CODE_SECTION : Section.STRING_DATA;
                    if (!input.module.stringData().isEmpty()) {
                        return emit(WindowsX64AssemblyLineKind.CONST_SECTION, ".const", ".const");
                    }
                }
                case STRING_DATA -> {
                    if (pendingStringDataLineIndex < pendingStringDataLines.size()) {
                        return emit(
                                WindowsX64AssemblyLineKind.STRING_DATA,
                                pendingStringDataLabel,
                                pendingStringDataLines.get(pendingStringDataLineIndex++)
                        );
                    }
                    if (stringDataIndex < input.module.stringData().size()) {
                        IrStringData stringData = input.module.stringData().get(stringDataIndex++);
                        pendingStringDataLines = formatStringDataLines(stringData);
                        pendingStringDataLineIndex = 0;
                        pendingStringDataLabel = stringData.label();
                        return emit(
                                WindowsX64AssemblyLineKind.STRING_DATA,
                                pendingStringDataLabel,
                                pendingStringDataLines.get(pendingStringDataLineIndex++)
                        );
                    }
                    section = Section.CODE_SECTION;
                }
                case CODE_SECTION -> {
                    section = Section.ENTRY_POINT;
                    return emit(WindowsX64AssemblyLineKind.CODE_SECTION, ".code", ".code");
                }
                case ENTRY_POINT -> {
                    List<String> entryLines = entryPointLines();
                    if (entryLineIndex < entryLines.size()) {
                        return emit(WindowsX64AssemblyLineKind.ENTRY_POINT, WindowsX64CallingConvention.ENTRY_SYMBOL,
                                entryLines.get(entryLineIndex++));
                    }
                    section = Section.FUNCTIONS;
                }
                case FUNCTIONS -> {
                    WindowsX64AssemblyLine line = nextFunctionLine();
                    if (line != null) {
                        return line;
                    }
                    section = Section.END;
                }
                case END -> {
                    completed = true;
                    return emit(WindowsX64AssemblyLineKind.END, "module", "END");
                }
            }
        }
    }

    @Override
    public CompilerStageSnapshot advance() {
        next();
        return snapshot();
    }

    @Override
    public CompilerStageResult<Output> result() {
        return CompilerStageResult.success(CompileStage.CODEGEN, new Output(toAssemblySource()));
    }

    /**
     * 返回当前汇编行。
     *
     * @return 当前汇编行 Optional
     */
    public Optional<WindowsX64AssemblyLine> currentLine() {
        return Optional.ofNullable(currentLine);
    }

    /**
     * 构建汇编输出。
     *
     * @return 汇编输出
     */
    public AssemblySource toAssemblySource() {
        while (canNext()) {
            next();
        }
        return new AssemblySource(TargetPlatform.WINDOWS_X86_64, WindowsX64CallingConvention.ENTRY_SYMBOL, work.assemblyText());
    }

    private WindowsX64AssemblyLine nextFunctionLine() {
        while (functionIndex < input.module.functions().size()) {
            if (functionState == null) {
                functionState = new FunctionState(input.module.functions().get(functionIndex), input.module.externalFunctionNames());
                work.currentFunctionName = functionState.function.name();
                work.currentFrameLayout = functionState.frame;
                work.currentSection = "function";
            }
            WindowsX64AssemblyLine line = functionState.nextLine();
            if (line != null) {
                return emit(line.kind(), line.subject(), line.text(), line.sourceRange());
            }
            functionState = null;
            functionIndex++;
        }
        work.currentFunctionName = "";
        work.currentFrameLayout = null;
        work.currentSection = "end";
        return null;
    }

    private WindowsX64AssemblyLine emit(WindowsX64AssemblyLineKind kind, String subject, String text) {
        return emit(kind, subject, text, null);
    }

    private WindowsX64AssemblyLine emit(WindowsX64AssemblyLineKind kind, String subject, String text, minic.source.SourceRange sourceRange) {
        currentLine = new WindowsX64AssemblyLine(kind, subject, text, sourceRange);
        work.assemblyLines.add(text);
        work.assemblyLineData.add(currentLine);
        work.currentSection = switch (kind) {
            case HEADER -> "header";
            case CONST_SECTION, STRING_DATA -> "const";
            case CODE_SECTION, ENTRY_POINT, FUNCTION_STRUCTURE, INSTRUCTION -> "code";
            case END -> "end";
        };
        return currentLine;
    }

    private static List<String> entryPointLines() {
        return List.of(
                WindowsX64CallingConvention.ENTRY_SYMBOL + " PROC",
                "    sub rsp, 40",
                "    call " + WindowsX64CallingConvention.USER_MAIN_SYMBOL,
                "    mov ecx, eax",
                "    call ExitProcess",
                WindowsX64CallingConvention.ENTRY_SYMBOL + " ENDP"
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
        HEADER_TARGET,
        HEADER_PUBLIC,
        HEADER_EXIT_PROCESS,
        EXTERNS,
        CONST_SECTION,
        STRING_DATA,
        CODE_SECTION,
        ENTRY_POINT,
        FUNCTIONS,
        END
    }

    private static final class FunctionState {
        private final IrFunction function;
        private final WindowsX64FrameLayout frame;
        private final String functionSymbol;
        private final String epilogueLabel;
        private final WindowsX64InstructionEmitter instructionEmitter;
        private final ArrayDeque<PendingInstructionLine> pendingInstructionLines = new ArrayDeque<>();
        private FunctionSection section = FunctionSection.PROC;
        private int blockIndex;
        private int instructionIndex;
        private int trapIndex;

        private FunctionState(IrFunction function, java.util.Set<String> externalFunctionNames) {
            this.function = function;
            frame = WindowsX64FrameLayout.create(function);
            functionSymbol = WindowsX64CallingConvention.functionDefinitionSymbol(function.name());
            epilogueLabel = functionSymbol + "$epilogue";
            instructionEmitter = new WindowsX64InstructionEmitter(frame, externalFunctionNames);
        }

        private WindowsX64AssemblyLine nextLine() {
            while (true) {
                if (!pendingInstructionLines.isEmpty()) {
                    PendingInstructionLine pendingLine = pendingInstructionLines.removeFirst();
                    return new WindowsX64AssemblyLine(
                            WindowsX64AssemblyLineKind.INSTRUCTION,
                            function.name(),
                            pendingLine.text(),
                            pendingLine.sourceRange()
                    );
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
                        WindowsX64AssemblyLine blockLine = nextBlockLine();
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

        private WindowsX64AssemblyLine nextBlockLine() {
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
                        return new WindowsX64AssemblyLine(
                                WindowsX64AssemblyLineKind.INSTRUCTION,
                                function.name(),
                                pendingLine.text(),
                                pendingLine.sourceRange()
                        );
                    }
                    continue;
                }
                blockIndex++;
                instructionIndex = 0;
            }
            return null;
        }

        private WindowsX64AssemblyLine structure(String text) {
            return new WindowsX64AssemblyLine(WindowsX64AssemblyLineKind.FUNCTION_STRUCTURE, function.name(), text);
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
     * Codegen 阶段输入数据。
     *
     * @param module IR 模块
     */
    public record Input(IrModule module, List<String> externalFunctionNames) implements CompilerStageInput {
        /**
         * 创建输入数据。
         *
         * @param module IR 模块
         * @param externalFunctionNames 外部函数名列表
         */
        public Input {
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(externalFunctionNames, "externalFunctionNames");
            externalFunctionNames = List.copyOf(externalFunctionNames);
        }

        private Input(IrModule module) {
            this(module, module.externalFunctionNames().stream().toList());
        }
    }

    /**
     * Codegen 阶段内部工作数据。
     */
    public static final class Work implements CompilerStageWork {
        private final ArrayList<String> assemblyLines = new ArrayList<>();
        private final ArrayList<WindowsX64AssemblyLine> assemblyLineData = new ArrayList<>();
        private String currentSection = "header";
        private String currentFunctionName = "";
        private WindowsX64FrameLayout currentFrameLayout;

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
        public Optional<WindowsX64FrameLayout> currentFrameLayout() {
            return Optional.ofNullable(currentFrameLayout);
        }

        /**
         * 返回已产出汇编行摘要。
         *
         * @return 汇编行摘要
         */
        public List<String> assemblyLineSummaries() {
            return List.copyOf(assemblyLines);
        }

        /**
         * 返回已产出汇编行数据。
         *
         * @return 汇编行数据
         */
        public List<WindowsX64AssemblyLine> assemblyLineData() {
            return List.copyOf(assemblyLineData);
        }

        private String assemblyText() {
            return String.join(System.lineSeparator(), assemblyLines) + System.lineSeparator();
        }
    }

    /**
     * Codegen 阶段输出数据。
     *
     * @param assemblySource 汇编输出
     */
    public record Output(AssemblySource assemblySource) implements CompilerStageOutput {
        /**
         * 创建输出数据。
         *
         * @param assemblySource 汇编输出
         */
        public Output {
            Objects.requireNonNull(assemblySource, "assemblySource");
        }
    }
}
