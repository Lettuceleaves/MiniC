package minic.compiler.codegen.windows;

import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.IrIndirectCallInstruction;
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
        valueEmitter = new WindowsX64ValueEmitter(frame, externalFunctionNames);
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
                String register = fullRegisterForType(parameter.type());
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
                String register = storeLocalRegister(storeLocal.local().type());
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
                emitLoadMemoryToRegister(builder, frame.localSlot(loadLocal.local()), loadLocal.local().type(), "rcx");
                builder.append("    mov ").append(frame.temporarySlot(loadLocal.result()))
                        .append(", ").append(storeValueRegister(loadLocal.result().type())).append(System.lineSeparator());
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
            case IrElementAddressInstruction elementAddress -> {
                valueEmitter.emitLoadValue(builder, elementAddress.baseAddress(), "rax");
                valueEmitter.emitLoadValue(builder, elementAddress.index(), "ecx");
                builder.append("    movsxd rcx, ecx").append(System.lineSeparator());
                builder.append("    lea rax, [rax+rcx*4]").append(System.lineSeparator());
                builder.append("    mov ").append(frame.temporarySlot(elementAddress.result()))
                        .append(", rax").append(System.lineSeparator());
            }
            case IrFieldAddressInstruction fieldAddress -> {
                valueEmitter.emitLoadValue(builder, fieldAddress.baseAddress(), "rax");
                if (fieldAddress.offset() != 0) {
                    builder.append("    lea rax, [rax+").append(fieldAddress.offset()).append("]")
                            .append(System.lineSeparator());
                }
                builder.append("    mov ").append(frame.temporarySlot(fieldAddress.result()))
                        .append(", rax").append(System.lineSeparator());
            }
            case IrLoadPointerInstruction loadPointer -> {
                valueEmitter.emitLoadValue(builder, loadPointer.address(), "rax");
                emitLoadMemoryToRegister(
                        builder,
                        memoryPrefix(loadPointer.result().type()) + " [rax]",
                        loadPointer.result().type(),
                        "rax"
                );
                builder.append("    mov ").append(frame.temporarySlot(loadPointer.result()))
                        .append(", ").append(valueEmitter.storeRegister("rax", loadPointer.result().type()))
                        .append(System.lineSeparator());
            }
            case IrStorePointerInstruction storePointer -> {
                valueEmitter.emitLoadValue(builder, storePointer.address(), "rax");
                String register = storeValueRegister(storePointer.value().type());
                valueEmitter.emitLoadValue(builder, storePointer.value(), register);
                builder.append("    mov ").append(memoryPrefix(storePointer.value().type()))
                        .append(" [rax], ").append(register).append(System.lineSeparator());
            }
            case IrCallInstruction call -> emitCall(builder, call);
            case IrIndirectCallInstruction call -> emitIndirectCall(builder, call);
            case IrBranchInstruction branch -> emitBranch(builder, functionName, branch);
            case IrJumpInstruction jump -> emitJump(builder, functionName, jump.targetLabel());
            case IrReturnInstruction returnInstruction -> {
                valueEmitter.emitLoadValue(builder, returnInstruction.value(), returnRegister(returnInstruction.value().type()));
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
        IrType operationType = binaryOperationType(binary);
        String leftRegister = arithmeticRegister("rax", operationType);
        String rightRegister = arithmeticRegister("rcx", operationType);
        valueEmitter.emitLoadValue(builder, binary.left(), leftRegister);
        builder.append("    push rax").append(System.lineSeparator());
        valueEmitter.emitLoadValue(builder, binary.right(), rightRegister);
        builder.append("    pop rax").append(System.lineSeparator());
        switch (binary.operator()) {
            case ADD -> builder.append("    add ").append(leftRegister).append(", ").append(rightRegister)
                    .append(System.lineSeparator());
            case SUBTRACT -> builder.append("    sub ").append(leftRegister).append(", ").append(rightRegister)
                    .append(System.lineSeparator());
            case MULTIPLY -> builder.append("    imul ").append(leftRegister).append(", ").append(rightRegister)
                    .append(System.lineSeparator());
            case DIVIDE -> {
                builder.append(operationType == IrType.LONG ? "    cqo" : "    cdq").append(System.lineSeparator());
                builder.append("    idiv ").append(rightRegister).append(System.lineSeparator());
            }
            case EQUAL -> emitComparison(builder, "sete", leftRegister, rightRegister);
            case NOT_EQUAL -> emitComparison(builder, "setne", leftRegister, rightRegister);
            case LESS_THAN -> emitComparison(builder, "setl", leftRegister, rightRegister);
            case LESS_EQUAL -> emitComparison(builder, "setle", leftRegister, rightRegister);
            case GREATER_THAN -> emitComparison(builder, "setg", leftRegister, rightRegister);
            case GREATER_EQUAL -> emitComparison(builder, "setge", leftRegister, rightRegister);
        }
        builder.append("    mov ").append(frame.temporarySlot(binary.result()))
                .append(", ").append(valueEmitter.storeRegister("rax", binary.result().type()))
                .append(System.lineSeparator());
    }

    private void emitComparison(StringBuilder builder, String setInstruction, String leftRegister, String rightRegister) {
        builder.append("    cmp ").append(leftRegister).append(", ").append(rightRegister).append(System.lineSeparator());
        builder.append("    ").append(setInstruction).append(" al").append(System.lineSeparator());
        builder.append("    movzx eax, al").append(System.lineSeparator());
    }

    private void emitCall(StringBuilder builder, IrCallInstruction call) {
        emitCallArguments(builder, call.arguments());
        builder.append("    call ").append(WindowsX64CallingConvention.callSymbol(
                        call.calleeName(),
                        externalFunctionNames.contains(call.calleeName())
                ))
                .append(System.lineSeparator());
        builder.append("    mov ").append(frame.temporarySlot(call.result()))
                .append(", ").append(returnRegister(call.result().type())).append(System.lineSeparator());
    }

    private void emitIndirectCall(StringBuilder builder, IrIndirectCallInstruction call) {
        emitCallArguments(builder, call.arguments());
        valueEmitter.emitLoadValue(builder, call.calleeAddress(), "rax");
        builder.append("    call rax").append(System.lineSeparator());
        builder.append("    mov ").append(frame.temporarySlot(call.result()))
                .append(", ").append(returnRegister(call.result().type())).append(System.lineSeparator());
    }

    private void emitCallArguments(StringBuilder builder, java.util.List<minic.compiler.ir.value.IrValue> arguments) {
        for (int index = WindowsX64CallingConvention.INTEGER_ARGUMENT_REGISTERS.size();
             index < arguments.size();
             index++) {
            IrType type = arguments.get(index).type();
            String register = stackArgumentRegister(type);
            valueEmitter.emitLoadValue(builder, arguments.get(index), register);
            builder.append("    mov ").append(frame.outgoingStackArgumentSlot(index, type))
                    .append(", ").append(register).append(System.lineSeparator());
        }
        for (int index = 0; index < arguments.size(); index++) {
            if (WindowsX64CallingConvention.isRegisterArgument(index)) {
                valueEmitter.emitLoadValue(
                        builder,
                        arguments.get(index),
                        argumentRegister(arguments.get(index).type(), index)
                );
            }
        }
    }

    private String argumentRegister(IrType type, int argumentIndex) {
        return switch (type) {
            case BOOL, CHAR -> byteArgumentRegister(argumentIndex);
            case LONG, POINTER -> WindowsX64CallingConvention.pointerArgumentRegister(argumentIndex);
            case INT, INT_ARRAY, STRUCT -> WindowsX64CallingConvention.integerArgumentRegister(argumentIndex);
        };
    }

    private String memoryPrefix(IrType type) {
        return valueEmitter.memoryPrefix(type);
    }

    private String storeValueRegister(IrType type) {
        return valueEmitter.storeRegister("rcx", type);
    }

    private String storeLocalRegister(IrType type) {
        return switch (type) {
            case LONG, POINTER -> valueEmitter.storeRegister("rax", type);
            case BOOL, CHAR, INT, INT_ARRAY, STRUCT -> valueEmitter.storeRegister("rcx", type);
        };
    }

    private String fullRegisterForType(IrType type) {
        return switch (type) {
            case BOOL, CHAR, INT, INT_ARRAY, STRUCT -> "eax";
            case LONG, POINTER -> "rax";
        };
    }

    private String returnRegister(IrType type) {
        return valueEmitter.storeRegister("rax", type);
    }

    private String stackArgumentRegister(IrType type) {
        return valueEmitter.storeRegister("rax", type);
    }

    private String arithmeticRegister(String preferredRegister, IrType type) {
        return valueEmitter.arithmeticRegister(preferredRegister, type);
    }

    private String byteArgumentRegister(int argumentIndex) {
        return switch (WindowsX64CallingConvention.pointerArgumentRegister(argumentIndex)) {
            case "rcx" -> "cl";
            case "rdx" -> "dl";
            case "r8" -> "r8b";
            case "r9" -> "r9b";
            default -> throw new IllegalArgumentException("unsupported argument index: " + argumentIndex);
        };
    }

    private IrType binaryOperationType(IrBinaryInstruction binary) {
        if (binary.left().type() == IrType.LONG || binary.right().type() == IrType.LONG
                || binary.left().type() == IrType.POINTER || binary.right().type() == IrType.POINTER) {
            return IrType.LONG;
        }
        return IrType.INT;
    }

    private void emitLoadMemoryToRegister(
            StringBuilder builder,
            String source,
            IrType type,
            String preferredRegister
    ) {
        if (type == IrType.BOOL) {
            builder.append("    movzx ").append(valueEmitter.intRegisterName(preferredRegister))
                    .append(", ").append(source).append(System.lineSeparator());
            return;
        }
        if (type == IrType.CHAR) {
            builder.append("    movsx ").append(valueEmitter.intRegisterName(preferredRegister))
                    .append(", ").append(source).append(System.lineSeparator());
            return;
        }
        builder.append("    mov ").append(valueEmitter.loadRegister(preferredRegister, type))
                .append(", ").append(source).append(System.lineSeparator());
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
