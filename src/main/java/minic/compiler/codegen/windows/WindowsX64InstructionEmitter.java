package minic.compiler.codegen.windows;

import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrJumpInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrStorePointerInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.model.IrType;

import java.util.Set;

final class WindowsX64InstructionEmitter {
    private final WindowsX64FrameLayout frame;
    private final WindowsX64ValueEmitter valueEmitter;
    private final Set<String> externalFunctionNames;

    WindowsX64InstructionEmitter(WindowsX64FrameLayout frame, Set<String> externalFunctionNames) {
        this.frame = frame;
        this.externalFunctionNames = Set.copyOf(externalFunctionNames);
        valueEmitter = new WindowsX64ValueEmitter(frame);
    }

    void emitParameterStores(StringBuilder builder, IrFunction function) {
        for (int index = 0; index < function.parameters().size(); index++) {
            IrParameter parameter = function.parameters().get(index);
            String destination = frame.parameterSlot(parameter.name(), parameter.type());
            if (WindowsX64CallingConvention.isRegisterArgument(index)) {
                builder.append("    mov ").append(destination).append(", ")
                        .append(argumentRegister(parameter.type(), index)).append(System.lineSeparator());
            } else {
                int stackOffset = WindowsX64CallingConvention.incomingStackArgumentOffset(index);
                String register = registerForType(parameter.type());
                builder.append("    mov ").append(register).append(", ")
                        .append(memoryPrefix(parameter.type())).append(" [rbp+").append(stackOffset).append("]")
                        .append(System.lineSeparator());
                builder.append("    mov ").append(destination).append(", ").append(register)
                        .append(System.lineSeparator());
            }
        }
    }

    void emitInstruction(
            StringBuilder builder,
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
                String register = registerForType(storeLocal.local().type());
                valueEmitter.emitLoadValue(builder, storeLocal.value(), register);
                builder.append("    mov ").append(frame.localSlot(storeLocal.local()))
                        .append(", ").append(register).append(System.lineSeparator());
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
                String register = registerForType(loadLocal.result().type());
                builder.append("    mov ").append(register).append(", ").append(frame.localSlot(loadLocal.local()))
                        .append(System.lineSeparator());
                builder.append("    mov ").append(frame.temporarySlot(loadLocal.result()))
                        .append(", ").append(register).append(System.lineSeparator());
            }
            case IrCheckNonZeroInstruction checkNonZero -> {
                valueEmitter.emitLoadValue(builder, checkNonZero.value(), "eax");
                builder.append("    cmp eax, 0").append(System.lineSeparator());
                builder.append("    je ").append(functionName).append("$trap_divide_by_zero")
                        .append(System.lineSeparator());
            }
            case IrBinaryInstruction binary -> emitBinary(builder, binary);
            case IrAddressOfLocalInstruction addressOfLocal -> {
                builder.append("    lea rax, ").append(frame.localAddress(addressOfLocal.local()))
                        .append(System.lineSeparator());
                builder.append("    mov ").append(frame.temporarySlot(addressOfLocal.result()))
                        .append(", rax").append(System.lineSeparator());
            }
            case IrLoadPointerInstruction loadPointer -> {
                valueEmitter.emitLoadValue(builder, loadPointer.address(), "rax");
                builder.append("    mov eax, DWORD PTR [rax]").append(System.lineSeparator());
                builder.append("    mov ").append(frame.temporarySlot(loadPointer.result()))
                        .append(", eax").append(System.lineSeparator());
            }
            case IrStorePointerInstruction storePointer -> {
                valueEmitter.emitLoadValue(builder, storePointer.address(), "rax");
                valueEmitter.emitLoadValue(builder, storePointer.value(), "ecx");
                builder.append("    mov DWORD PTR [rax], ecx").append(System.lineSeparator());
            }
            case IrCallInstruction call -> emitCall(builder, call);
            case IrBranchInstruction branch -> emitBranch(builder, functionName, branch);
            case IrJumpInstruction jump -> emitJump(builder, functionName, jump.targetLabel());
            case IrReturnInstruction returnInstruction -> {
                valueEmitter.emitLoadValue(builder, returnInstruction.value(), "eax");
                builder.append("    jmp ").append(epilogueLabel).append(System.lineSeparator());
            }
            default -> throw new IllegalArgumentException("unsupported IR instruction: "
                    + instruction.getClass().getSimpleName());
        }
    }

    String blockSymbol(String functionName, String blockLabel) {
        return functionName + "$" + blockLabel;
    }

    void emitFunctionTrap(
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

    private void emitBinary(StringBuilder builder, IrBinaryInstruction binary) {
        valueEmitter.emitLoadValue(builder, binary.left(), "eax");
        builder.append("    push rax").append(System.lineSeparator());
        valueEmitter.emitLoadValue(builder, binary.right(), "ecx");
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

    private void emitCall(StringBuilder builder, IrCallInstruction call) {
        for (int index = WindowsX64CallingConvention.INTEGER_ARGUMENT_REGISTERS.size();
             index < call.arguments().size();
             index++) {
            if (call.arguments().get(index).type() == IrType.POINTER) {
                valueEmitter.emitLoadValue(builder, call.arguments().get(index), "rax");
                builder.append("    mov QWORD PTR [rsp+")
                        .append(WindowsX64CallingConvention.outgoingStackArgumentOffset(index))
                        .append("], rax").append(System.lineSeparator());
            } else {
                valueEmitter.emitLoadValue(builder, call.arguments().get(index), "eax");
                builder.append("    mov ").append(frame.outgoingStackArgumentSlot(index)).append(", eax")
                        .append(System.lineSeparator());
            }
        }
        for (int index = 0; index < call.arguments().size(); index++) {
            if (WindowsX64CallingConvention.isRegisterArgument(index)) {
                valueEmitter.emitLoadValue(
                        builder,
                        call.arguments().get(index),
                        argumentRegister(call.arguments().get(index).type(), index)
                );
            }
        }
        builder.append("    call ").append(WindowsX64CallingConvention.callSymbol(
                        call.calleeName(),
                        externalFunctionNames.contains(call.calleeName())
                ))
                .append(System.lineSeparator());
        builder.append("    mov ").append(frame.temporarySlot(call.result()))
                .append(", eax").append(System.lineSeparator());
    }

    private String argumentRegister(IrType type, int argumentIndex) {
        if (type == IrType.POINTER) {
            return WindowsX64CallingConvention.pointerArgumentRegister(argumentIndex);
        }
        return WindowsX64CallingConvention.integerArgumentRegister(argumentIndex);
    }

    private String registerForType(IrType type) {
        if (type == IrType.POINTER) {
            return "rax";
        }
        return "eax";
    }

    private String memoryPrefix(IrType type) {
        if (type == IrType.POINTER) {
            return "QWORD PTR";
        }
        return "DWORD PTR";
    }

    private void emitBranch(StringBuilder builder, String functionName, IrBranchInstruction branch) {
        valueEmitter.emitLoadValue(builder, branch.condition(), "eax");
        builder.append("    cmp eax, 0").append(System.lineSeparator());
        builder.append("    jne ").append(blockSymbol(functionName, branch.thenLabel())).append(System.lineSeparator());
        emitJump(builder, functionName, branch.elseLabel());
    }

    private void emitJump(StringBuilder builder, String functionName, String targetLabel) {
        builder.append("    jmp ").append(blockSymbol(functionName, targetLabel)).append(System.lineSeparator());
    }
}
