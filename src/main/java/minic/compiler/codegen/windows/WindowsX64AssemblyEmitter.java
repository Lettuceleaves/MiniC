package minic.compiler.codegen.windows;

import minic.compiler.codegen.AssemblyEmitter;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.target.TargetPlatform;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBinaryOperator;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrParameterRef;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Windows x86_64 MASM 风格汇编 emitter。
 */
public final class WindowsX64AssemblyEmitter implements AssemblyEmitter {
    private static final String ENTRY_SYMBOL = "main";
    private static final List<String> INTEGER_ARGUMENT_REGISTERS = List.of("ecx", "edx", "r8d", "r9d");

    @Override
    public AssemblySource emit(IrModule module) {
        Objects.requireNonNull(module, "module");
        StringBuilder builder = new StringBuilder();
        builder.append("; target: ").append(TargetPlatform.WINDOWS_X86_64.id()).append(System.lineSeparator());
        builder.append("PUBLIC ").append(ENTRY_SYMBOL).append(System.lineSeparator());
        builder.append(".code").append(System.lineSeparator());
        for (IrFunction function : module.functions()) {
            emitFunction(builder, function);
        }
        builder.append("END").append(System.lineSeparator());
        return new AssemblySource(TargetPlatform.WINDOWS_X86_64, ENTRY_SYMBOL, builder.toString());
    }

    private void emitFunction(StringBuilder builder, IrFunction function) {
        FunctionFrame frame = FunctionFrame.create(function);
        String functionSymbol = symbolName(function.name());
        String epilogueLabel = functionSymbol + "$epilogue";
        builder.append(functionSymbol).append(" PROC").append(System.lineSeparator());
        builder.append("    push rbp").append(System.lineSeparator());
        builder.append("    mov rbp, rsp").append(System.lineSeparator());
        if (frame.frameSize() > 0) {
            builder.append("    sub rsp, ").append(frame.frameSize()).append(System.lineSeparator());
        }
        emitParameterStores(builder, function, frame);
        for (IrInstruction instruction : function.blocks().getFirst().instructions()) {
            emitInstruction(builder, frame, functionSymbol, epilogueLabel, instruction);
        }
        emitFunctionTrap(builder, functionSymbol, "uninitialized", 101, epilogueLabel);
        emitFunctionTrap(builder, functionSymbol, "divide_by_zero", 102, epilogueLabel);
        builder.append(epilogueLabel).append(":").append(System.lineSeparator());
        builder.append("    mov rsp, rbp").append(System.lineSeparator());
        builder.append("    pop rbp").append(System.lineSeparator());
        builder.append("    ret").append(System.lineSeparator());
        builder.append(functionSymbol).append(" ENDP").append(System.lineSeparator());
    }

    private void emitParameterStores(StringBuilder builder, IrFunction function, FunctionFrame frame) {
        for (int index = 0; index < function.parameters().size(); index++) {
            IrParameter parameter = function.parameters().get(index);
            String destination = frame.parameterSlot(parameter.name());
            if (index < INTEGER_ARGUMENT_REGISTERS.size()) {
                builder.append("    mov ").append(destination).append(", ")
                        .append(INTEGER_ARGUMENT_REGISTERS.get(index)).append(System.lineSeparator());
            } else {
                int stackOffset = 48 + (index - INTEGER_ARGUMENT_REGISTERS.size()) * 8;
                builder.append("    mov eax, DWORD PTR [rbp+").append(stackOffset).append("]")
                        .append(System.lineSeparator());
                builder.append("    mov ").append(destination).append(", eax").append(System.lineSeparator());
            }
        }
    }

    private void emitInstruction(
            StringBuilder builder,
            FunctionFrame frame,
            String functionName,
            String epilogueLabel,
            IrInstruction instruction
    ) {
        switch (instruction) {
            case IrDeclareLocalInstruction declareLocal -> {
                builder.append("    mov ").append(frame.localInitializedSlot(declareLocal.local()))
                        .append(", 0").append(System.lineSeparator());
            }
            case IrStoreLocalInstruction storeLocal -> {
                emitLoadValue(builder, frame, storeLocal.value(), "eax");
                builder.append("    mov ").append(frame.localSlot(storeLocal.local()))
                        .append(", eax").append(System.lineSeparator());
                builder.append("    mov ").append(frame.localInitializedSlot(storeLocal.local()))
                        .append(", 1").append(System.lineSeparator());
            }
            case IrCheckInitializedInstruction checkInitialized -> {
                builder.append("    cmp ").append(frame.localInitializedSlot(checkInitialized.local()))
                        .append(", 0").append(System.lineSeparator());
                builder.append("    je ").append(functionName).append("$trap_uninitialized")
                        .append(System.lineSeparator());
            }
            case IrLoadLocalInstruction loadLocal -> {
                builder.append("    mov eax, ").append(frame.localSlot(loadLocal.local())).append(System.lineSeparator());
                builder.append("    mov ").append(frame.temporarySlot(loadLocal.result()))
                        .append(", eax").append(System.lineSeparator());
            }
            case IrCheckNonZeroInstruction checkNonZero -> {
                emitLoadValue(builder, frame, checkNonZero.value(), "eax");
                builder.append("    cmp eax, 0").append(System.lineSeparator());
                builder.append("    je ").append(functionName).append("$trap_divide_by_zero")
                        .append(System.lineSeparator());
            }
            case IrBinaryInstruction binary -> emitBinary(builder, frame, binary);
            case IrCallInstruction call -> emitCall(builder, frame, call);
            case IrReturnInstruction returnInstruction -> {
                emitLoadValue(builder, frame, returnInstruction.value(), "eax");
                builder.append("    jmp ").append(epilogueLabel).append(System.lineSeparator());
            }
            default -> throw new IllegalArgumentException("unsupported IR instruction: "
                    + instruction.getClass().getSimpleName());
        }
    }

    private void emitBinary(StringBuilder builder, FunctionFrame frame, IrBinaryInstruction binary) {
        emitLoadValue(builder, frame, binary.left(), "eax");
        builder.append("    push rax").append(System.lineSeparator());
        emitLoadValue(builder, frame, binary.right(), "ecx");
        builder.append("    pop rax").append(System.lineSeparator());
        switch (binary.operator()) {
            case ADD -> builder.append("    add eax, ecx").append(System.lineSeparator());
            case SUBTRACT -> builder.append("    sub eax, ecx").append(System.lineSeparator());
            case MULTIPLY -> builder.append("    imul eax, ecx").append(System.lineSeparator());
            case DIVIDE -> {
                builder.append("    cdq").append(System.lineSeparator());
                builder.append("    idiv ecx").append(System.lineSeparator());
            }
            case EQUAL -> emitComparison(builder, "sete");
            case NOT_EQUAL -> emitComparison(builder, "setne");
            case LESS_THAN -> emitComparison(builder, "setl");
            case LESS_EQUAL -> emitComparison(builder, "setle");
            case GREATER_THAN -> emitComparison(builder, "setg");
            case GREATER_EQUAL -> emitComparison(builder, "setge");
        }
        builder.append("    mov ").append(frame.temporarySlot(binary.result()))
                .append(", eax").append(System.lineSeparator());
    }

    private void emitComparison(StringBuilder builder, String setInstruction) {
        builder.append("    cmp eax, ecx").append(System.lineSeparator());
        builder.append("    ").append(setInstruction).append(" al").append(System.lineSeparator());
        builder.append("    movzx eax, al").append(System.lineSeparator());
    }

    private void emitCall(StringBuilder builder, FunctionFrame frame, IrCallInstruction call) {
        for (int index = INTEGER_ARGUMENT_REGISTERS.size(); index < call.arguments().size(); index++) {
            emitLoadValue(builder, frame, call.arguments().get(index), "eax");
            builder.append("    mov ").append(frame.outgoingStackArgumentSlot(index)).append(", eax")
                    .append(System.lineSeparator());
        }
        for (int index = 0; index < call.arguments().size(); index++) {
            if (index < INTEGER_ARGUMENT_REGISTERS.size()) {
                emitLoadValue(builder, frame, call.arguments().get(index), INTEGER_ARGUMENT_REGISTERS.get(index));
            }
        }
        builder.append("    call ").append(symbolName(call.calleeName())).append(System.lineSeparator());
        builder.append("    mov ").append(frame.temporarySlot(call.result()))
                .append(", eax").append(System.lineSeparator());
    }

    private void emitLoadValue(StringBuilder builder, FunctionFrame frame, IrValue value, String register) {
        if (value instanceof IrConstant constant) {
            builder.append("    mov ").append(register).append(", ").append(constant.value()).append(System.lineSeparator());
            return;
        }
        if (value instanceof IrTemporary temporary) {
            builder.append("    mov ").append(register).append(", ")
                    .append(frame.temporarySlot(temporary)).append(System.lineSeparator());
            return;
        }
        if (value instanceof IrParameterRef parameterRef) {
            builder.append("    mov ").append(register).append(", ")
                    .append(frame.parameterSlot(parameterRef.name())).append(System.lineSeparator());
            return;
        }
        throw new IllegalArgumentException("unsupported IR value: " + value.getClass().getSimpleName());
    }

    private void emitFunctionTrap(
            StringBuilder builder,
            String functionName,
            String trapName,
            int exitCode,
            String epilogueLabel
    ) {
        builder.append(functionName).append("$trap_").append(trapName).append(":").append(System.lineSeparator());
        builder.append("    mov eax, ").append(exitCode).append(System.lineSeparator());
        builder.append("    jmp ").append(epilogueLabel).append(System.lineSeparator());
    }

    private String symbolName(String functionName) {
        if (ENTRY_SYMBOL.equals(functionName)) {
            return ENTRY_SYMBOL;
        }
        return "minic$" + functionName;
    }

    private record FunctionFrame(
            Map<String, Integer> parameterOffsets,
            Map<String, Integer> localOffsets,
            Map<String, Integer> localInitializedOffsets,
            Map<String, Integer> temporaryOffsets,
            int outgoingArgumentAreaSize,
            int frameSize
    ) {
        private static FunctionFrame create(IrFunction function) {
            LinkedHashMap<String, Integer> parameterOffsets = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> localOffsets = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> localInitializedOffsets = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> temporaryOffsets = new LinkedHashMap<>();
            int outgoingArgumentAreaSize = collectOutgoingArgumentAreaSize(function);
            int nextOffset = 0;
            for (IrParameter parameter : function.parameters()) {
                nextOffset += 4;
                parameterOffsets.put(parameter.name(), nextOffset);
            }
            for (IrInstruction instruction : function.blocks().getFirst().instructions()) {
                nextOffset = collectSlots(
                        instruction,
                        localOffsets,
                        localInitializedOffsets,
                        temporaryOffsets,
                        nextOffset
                );
            }
            int frameSize = alignTo16(outgoingArgumentAreaSize + nextOffset);
            return new FunctionFrame(
                    parameterOffsets,
                    localOffsets,
                    localInitializedOffsets,
                    temporaryOffsets,
                    outgoingArgumentAreaSize,
                    frameSize
            );
        }

        private static int collectOutgoingArgumentAreaSize(IrFunction function) {
            int maxArgumentCount = 0;
            for (IrInstruction instruction : function.blocks().getFirst().instructions()) {
                if (instruction instanceof IrCallInstruction call) {
                    maxArgumentCount = Math.max(maxArgumentCount, call.arguments().size());
                }
            }
            int stackArgumentCount = Math.max(0, maxArgumentCount - INTEGER_ARGUMENT_REGISTERS.size());
            return alignTo16(32 + stackArgumentCount * 8);
        }

        private static int collectSlots(
                IrInstruction instruction,
                Map<String, Integer> localOffsets,
                Map<String, Integer> localInitializedOffsets,
                Map<String, Integer> temporaryOffsets,
                int nextOffset
        ) {
            if (instruction instanceof IrDeclareLocalInstruction declareLocal) {
                nextOffset = ensureLocal(declareLocal.local(), localOffsets, localInitializedOffsets, nextOffset);
            } else if (instruction instanceof IrLoadLocalInstruction loadLocal) {
                nextOffset = ensureLocal(loadLocal.local(), localOffsets, localInitializedOffsets, nextOffset);
                nextOffset = ensureTemporary(loadLocal.result(), temporaryOffsets, nextOffset);
            } else if (instruction instanceof IrBinaryInstruction binary) {
                nextOffset = ensureTemporary(binary.result(), temporaryOffsets, nextOffset);
            } else if (instruction instanceof IrCallInstruction call) {
                nextOffset = ensureTemporary(call.result(), temporaryOffsets, nextOffset);
            } else if (instruction instanceof IrStoreLocalInstruction storeLocal) {
                nextOffset = ensureLocal(storeLocal.local(), localOffsets, localInitializedOffsets, nextOffset);
            } else if (instruction instanceof IrCheckInitializedInstruction checkInitialized) {
                nextOffset = ensureLocal(checkInitialized.local(), localOffsets, localInitializedOffsets, nextOffset);
            }
            return nextOffset;
        }

        private static int ensureLocal(
                IrLocal local,
                Map<String, Integer> localOffsets,
                Map<String, Integer> localInitializedOffsets,
                int nextOffset
        ) {
            if (!localOffsets.containsKey(local.name())) {
                nextOffset += 4;
                localOffsets.put(local.name(), nextOffset);
                nextOffset += 4;
                localInitializedOffsets.put(local.name(), nextOffset);
            }
            return nextOffset;
        }

        private static int ensureTemporary(
                IrTemporary temporary,
                Map<String, Integer> temporaryOffsets,
                int nextOffset
        ) {
            if (!temporaryOffsets.containsKey(temporary.name())) {
                nextOffset += 4;
                temporaryOffsets.put(temporary.name(), nextOffset);
            }
            return nextOffset;
        }

        private static int alignTo16(int value) {
            return ((value + 15) / 16) * 16;
        }

        private String parameterSlot(String name) {
            return stackSlot(parameterOffsets.get(name));
        }

        private String localSlot(IrLocal local) {
            return stackSlot(localOffsets.get(local.name()));
        }

        private String localInitializedSlot(IrLocal local) {
            return stackSlot(localInitializedOffsets.get(local.name()));
        }

        private String temporarySlot(IrTemporary temporary) {
            return stackSlot(temporaryOffsets.get(temporary.name()));
        }

        private String outgoingStackArgumentSlot(int argumentIndex) {
            if (argumentIndex < INTEGER_ARGUMENT_REGISTERS.size()) {
                throw new IllegalArgumentException("register argument has no outgoing stack slot");
            }
            int stackOffset = 32 + (argumentIndex - INTEGER_ARGUMENT_REGISTERS.size()) * 8;
            return "DWORD PTR [rsp+" + stackOffset + "]";
        }

        private String stackSlot(Integer offset) {
            if (offset == null) {
                throw new IllegalArgumentException("missing stack slot");
            }
            return "DWORD PTR [rbp-" + (outgoingArgumentAreaSize + offset) + "]";
        }
    }
}
