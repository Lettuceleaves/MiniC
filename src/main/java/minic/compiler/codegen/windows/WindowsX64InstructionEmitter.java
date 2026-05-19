package minic.compiler.codegen.windows;

import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCastInstruction;
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
import minic.compiler.ir.instruction.IrMemCopyInstruction;
import minic.compiler.ir.instruction.IrMoveInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrStorePointerInstruction;
import minic.compiler.ir.instruction.IrSelectInstruction;
import minic.compiler.ir.instruction.IrUnaryInstruction;
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
                emitStoreRegisterToMemory(builder, destination, parameter.type(), argumentRegister(parameter.type(), index));
            } else {
                int stackOffset = WindowsX64CallingConvention.incomingStackArgumentOffset(index);
                String register = fullRegisterForType(parameter.type());
                String source = memoryPrefix(parameter.type()) + " [rbp+" + stackOffset + "]";
                if (parameter.type().isFloatingScalar()) {
                    emitLoadMemoryToRegister(builder, source, parameter.type(), register);
                    emitStoreRegisterToMemory(builder, destination, parameter.type(), register);
                } else {
                    builder.append("    mov ").append(register).append(", ")
                            .append(source)
                            .append(System.lineSeparator());
                    builder.append("    mov ").append(destination).append(", ").append(register)
                            .append(System.lineSeparator());
                }
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
                emitStoreRegisterToMemory(builder, frame.localSlot(storeLocal.local()), storeLocal.local().type(), register);
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
                emitStoreRegisterToMemory(
                        builder,
                        frame.temporarySlot(loadLocal.result()),
                        loadLocal.result().type(),
                        storeValueRegister(loadLocal.result().type())
                );
            }
            case IrCheckNonZeroInstruction checkNonZero -> {
                if (checkNonZero.value().type().isFloatingScalar()) {
                    valueEmitter.emitLoadValue(builder, checkNonZero.value(), "xmm0");
                    if (checkNonZero.value().type() == IrType.FLOAT) {
                        builder.append("    xorps xmm1, xmm1").append(System.lineSeparator());
                        builder.append("    ucomiss xmm0, xmm1").append(System.lineSeparator());
                    } else {
                        builder.append("    xorpd xmm1, xmm1").append(System.lineSeparator());
                        builder.append("    ucomisd xmm0, xmm1").append(System.lineSeparator());
                    }
                } else {
                    valueEmitter.emitLoadValue(builder, checkNonZero.value(), "eax");
                    builder.append("    cmp eax, 0").append(System.lineSeparator());
                }
                builder.append("    je ").append(functionName).append("$trap_divide_by_zero")
                        .append(System.lineSeparator());
            }
            case IrCastInstruction cast -> emitCast(builder, cast);
            case IrBinaryInstruction binary -> emitBinary(builder, binary);
            case IrUnaryInstruction unary -> emitUnary(builder, unary);
            case IrMoveInstruction move -> emitMove(builder, move);
            case IrSelectInstruction select -> emitSelect(builder, select);
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
                emitElementAddressScale(builder, elementAddress.elementSizeBytes());
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
                emitStoreRegisterToMemory(
                        builder,
                        frame.temporarySlot(loadPointer.result()),
                        loadPointer.result().type(),
                        valueEmitter.storeRegister("rax", loadPointer.result().type())
                );
            }
            case IrStorePointerInstruction storePointer -> {
                valueEmitter.emitLoadValue(builder, storePointer.address(), "rax");
                String register = storeValueRegister(storePointer.value().type());
                valueEmitter.emitLoadValue(builder, storePointer.value(), register);
                emitStoreRegisterToMemory(
                        builder,
                        memoryPrefix(storePointer.value().type()) + " [rax]",
                        storePointer.value().type(),
                        register
                );
            }
            case IrCallInstruction call -> emitCall(builder, call);
            case IrIndirectCallInstruction call -> emitIndirectCall(builder, call);
            case IrMemCopyInstruction memCopy -> emitMemCopy(builder, memCopy);
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
        if (operationType.isFloatingScalar()) {
            builder.append(operationType == IrType.FLOAT ? "    sub rsp, 4" : "    sub rsp, 8")
                    .append(System.lineSeparator());
            emitStoreRegisterToMemory(builder, floatingScratchSlot(operationType), operationType, leftRegister);
        } else {
            builder.append("    push rax").append(System.lineSeparator());
        }
        valueEmitter.emitLoadValue(builder, binary.right(), rightRegister);
        if (operationType.isFloatingScalar()) {
            emitLoadMemoryToRegister(builder, floatingScratchSlot(operationType), operationType, leftRegister);
            builder.append(operationType == IrType.FLOAT ? "    add rsp, 4" : "    add rsp, 8")
                    .append(System.lineSeparator());
        } else {
            builder.append("    pop rax").append(System.lineSeparator());
        }
        switch (binary.operator()) {
            case ADD -> emitArithmetic(builder, operationType, "add", leftRegister, rightRegister);
            case SUBTRACT -> emitArithmetic(builder, operationType, "sub", leftRegister, rightRegister);
            case MULTIPLY -> emitArithmetic(builder, operationType, "mul", leftRegister, rightRegister);
            case DIVIDE -> {
                if (operationType.isFloatingScalar()) {
                    emitArithmetic(builder, operationType, "div", leftRegister, rightRegister);
                } else {
                    builder.append(operationType == IrType.LONG ? "    cqo" : "    cdq").append(System.lineSeparator());
                    builder.append("    idiv ").append(rightRegister).append(System.lineSeparator());
                }
            }
            case MODULO -> {
                builder.append(operationType == IrType.LONG ? "    cqo" : "    cdq").append(System.lineSeparator());
                builder.append("    idiv ").append(rightRegister).append(System.lineSeparator());
                builder.append("    mov ").append(leftRegister).append(", ")
                        .append(operationType == IrType.LONG ? "rdx" : "edx").append(System.lineSeparator());
            }
            case BITWISE_AND -> builder.append("    and ").append(leftRegister).append(", ").append(rightRegister).append(System.lineSeparator());
            case BITWISE_OR -> builder.append("    or ").append(leftRegister).append(", ").append(rightRegister).append(System.lineSeparator());
            case BITWISE_XOR -> builder.append("    xor ").append(leftRegister).append(", ").append(rightRegister).append(System.lineSeparator());
            case SHIFT_LEFT -> {
                if (!"rcx".equals(rightRegister) && !"ecx".equals(rightRegister)) {
                    builder.append("    mov ecx, ").append(rightRegister).append(System.lineSeparator());
                }
                builder.append("    shl ").append(leftRegister).append(", cl").append(System.lineSeparator());
            }
            case SHIFT_RIGHT -> {
                if (!"rcx".equals(rightRegister) && !"ecx".equals(rightRegister)) {
                    builder.append("    mov ecx, ").append(rightRegister).append(System.lineSeparator());
                }
                builder.append("    sar ").append(leftRegister).append(", cl").append(System.lineSeparator());
            }
            case LOGICAL_AND -> emitLogicalBinary(builder, "and", leftRegister, rightRegister);
            case LOGICAL_OR -> emitLogicalBinary(builder, "or", leftRegister, rightRegister);
            case EQUAL -> emitComparison(builder, operationType, "sete", leftRegister, rightRegister);
            case NOT_EQUAL -> emitComparison(builder, operationType, "setne", leftRegister, rightRegister);
            case LESS_THAN -> emitComparison(builder, operationType, operationType.isFloatingScalar() ? "setb" : "setl", leftRegister, rightRegister);
            case LESS_EQUAL -> emitComparison(builder, operationType, operationType.isFloatingScalar() ? "setbe" : "setle", leftRegister, rightRegister);
            case GREATER_THAN -> emitComparison(builder, operationType, operationType.isFloatingScalar() ? "seta" : "setg", leftRegister, rightRegister);
            case GREATER_EQUAL -> emitComparison(builder, operationType, operationType.isFloatingScalar() ? "setae" : "setge", leftRegister, rightRegister);
        }
        emitStoreRegisterToMemory(
                builder,
                frame.temporarySlot(binary.result()),
                binary.result().type(),
                valueEmitter.storeRegister("rax", binary.result().type())
        );
    }

    private void emitUnary(StringBuilder builder, IrUnaryInstruction unary) {
        valueEmitter.emitLoadValue(builder, unary.operand(), "rax");
        switch (unary.operator()) {
            case LOGICAL_NOT -> {
                builder.append("    cmp ").append(valueEmitter.storeRegister("rax", unary.operand().type()))
                        .append(", 0").append(System.lineSeparator());
                builder.append("    sete al").append(System.lineSeparator());
                builder.append("    movzx eax, al").append(System.lineSeparator());
            }
            case BITWISE_NOT -> builder.append("    not ")
                    .append(valueEmitter.storeRegister("rax", unary.result().type()))
                    .append(System.lineSeparator());
            case NEGATE -> builder.append("    neg ")
                    .append(valueEmitter.storeRegister("rax", unary.result().type()))
                    .append(System.lineSeparator());
        }
        emitStoreRegisterToMemory(
                builder,
                frame.temporarySlot(unary.result()),
                unary.result().type(),
                valueEmitter.storeRegister("rax", unary.result().type())
        );
    }

    private void emitSelect(StringBuilder builder, IrSelectInstruction select) {
        String falseLabel = "minic$select_false_" + Math.abs(select.hashCode());
        String endLabel = "minic$select_end_" + Math.abs(select.hashCode());
        valueEmitter.emitLoadValue(builder, select.condition(), "eax");
        builder.append("    cmp eax, 0").append(System.lineSeparator());
        builder.append("    je ").append(falseLabel).append(System.lineSeparator());
        valueEmitter.emitLoadValue(builder, select.thenValue(), storeValueRegister(select.result().type()));
        emitStoreRegisterToMemory(builder, frame.temporarySlot(select.result()), select.result().type(), storeValueRegister(select.result().type()));
        builder.append("    jmp ").append(endLabel).append(System.lineSeparator());
        builder.append(falseLabel).append(":").append(System.lineSeparator());
        valueEmitter.emitLoadValue(builder, select.elseValue(), storeValueRegister(select.result().type()));
        emitStoreRegisterToMemory(builder, frame.temporarySlot(select.result()), select.result().type(), storeValueRegister(select.result().type()));
        builder.append(endLabel).append(":").append(System.lineSeparator());
    }

    private void emitMove(StringBuilder builder, IrMoveInstruction move) {
        valueEmitter.emitLoadValue(builder, move.value(), storeValueRegister(move.result().type()));
        emitStoreRegisterToMemory(
                builder,
                frame.temporarySlot(move.result()),
                move.result().type(),
                storeValueRegister(move.result().type())
        );
    }

    private void emitLogicalBinary(StringBuilder builder, String operation, String leftRegister, String rightRegister) {
        builder.append("    cmp ").append(leftRegister).append(", 0").append(System.lineSeparator());
        builder.append("    setne al").append(System.lineSeparator());
        builder.append("    movzx eax, al").append(System.lineSeparator());
        builder.append("    cmp ").append(rightRegister).append(", 0").append(System.lineSeparator());
        builder.append("    setne cl").append(System.lineSeparator());
        builder.append("    movzx ecx, cl").append(System.lineSeparator());
        builder.append("    ").append(operation).append(" eax, ecx").append(System.lineSeparator());
    }

    private void emitCast(StringBuilder builder, IrCastInstruction cast) {
        IrType sourceType = cast.value().type();
        IrType targetType = cast.result().type();
        if (sourceType == targetType) {
            valueEmitter.emitLoadValue(builder, cast.value(), valueEmitter.storeRegister("rax", targetType));
            emitStoreRegisterToMemory(builder, frame.temporarySlot(cast.result()), targetType, valueEmitter.storeRegister("rax", targetType));
            return;
        }
        if (targetType.isFloatingScalar()) {
            if (sourceType == IrType.FLOAT && targetType == IrType.DOUBLE) {
                valueEmitter.emitLoadValue(builder, cast.value(), "xmm0");
                builder.append("    cvtss2sd xmm0, xmm0").append(System.lineSeparator());
            } else if (sourceType.isIntegerScalar()) {
                valueEmitter.emitLoadValue(builder, cast.value(), integerCastRegister(sourceType));
                builder.append(targetType == IrType.FLOAT ? "    cvtsi2ss " : "    cvtsi2sd ")
                        .append("xmm0, ").append(integerCastRegister(sourceType)).append(System.lineSeparator());
            } else if (sourceType == IrType.DOUBLE && targetType == IrType.FLOAT) {
                valueEmitter.emitLoadValue(builder, cast.value(), "xmm0");
                builder.append("    cvtsd2ss xmm0, xmm0").append(System.lineSeparator());
            } else {
                valueEmitter.emitLoadValue(builder, cast.value(), "xmm0");
            }
            emitStoreRegisterToMemory(builder, frame.temporarySlot(cast.result()), targetType, "xmm0");
            return;
        }
        if (sourceType.isFloatingScalar() && targetType.isIntegerScalar()) {
            valueEmitter.emitLoadValue(builder, cast.value(), "xmm0");
            builder.append(sourceType == IrType.FLOAT ? "    cvttss2si " : "    cvttsd2si ")
                    .append(integerCastRegister(targetType)).append(", xmm0").append(System.lineSeparator());
            emitStoreRegisterToMemory(builder, frame.temporarySlot(cast.result()), targetType, integerCastRegister(targetType));
            return;
        }
        valueEmitter.emitLoadValue(builder, cast.value(), valueEmitter.storeRegister("rax", sourceType));
        emitStoreRegisterToMemory(builder, frame.temporarySlot(cast.result()), targetType, valueEmitter.storeRegister("rax", targetType));
    }

    private void emitComparison(
            StringBuilder builder,
            IrType operationType,
            String setInstruction,
            String leftRegister,
            String rightRegister
    ) {
        if (operationType.isFloatingScalar()) {
            builder.append(operationType == IrType.FLOAT ? "    ucomiss " : "    ucomisd ")
                    .append(leftRegister).append(", ").append(rightRegister).append(System.lineSeparator());
            builder.append("    ").append(setInstruction).append(" al").append(System.lineSeparator());
            builder.append("    movzx eax, al").append(System.lineSeparator());
            return;
        }
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
        emitStoreRegisterToMemory(builder, frame.temporarySlot(call.result()), call.result().type(), returnRegister(call.result().type()));
    }

    private void emitIndirectCall(StringBuilder builder, IrIndirectCallInstruction call) {
        emitCallArguments(builder, call.arguments());
        valueEmitter.emitLoadValue(builder, call.calleeAddress(), "rax");
        builder.append("    call rax").append(System.lineSeparator());
        emitStoreRegisterToMemory(builder, frame.temporarySlot(call.result()), call.result().type(), returnRegister(call.result().type()));
    }

    private void emitCallArguments(StringBuilder builder, java.util.List<minic.compiler.ir.value.IrValue> arguments) {
        for (int index = WindowsX64CallingConvention.INTEGER_ARGUMENT_REGISTERS.size();
             index < arguments.size();
             index++) {
            IrType type = arguments.get(index).type();
            String register = stackArgumentRegister(type);
            valueEmitter.emitLoadValue(builder, arguments.get(index), register);
            emitStoreRegisterToMemory(builder, frame.outgoingStackArgumentSlot(index, type), type, register);
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
            case FLOAT, DOUBLE -> WindowsX64CallingConvention.floatArgumentRegister(argumentIndex);
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
            case FLOAT, DOUBLE, LONG, POINTER -> valueEmitter.storeRegister("rax", type);
            case BOOL, CHAR, INT, INT_ARRAY, STRUCT -> valueEmitter.storeRegister("rcx", type);
        };
    }

    private String fullRegisterForType(IrType type) {
        return switch (type) {
            case FLOAT, DOUBLE -> "xmm0";
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
        if (binary.left().type() == IrType.DOUBLE || binary.right().type() == IrType.DOUBLE) {
            return IrType.DOUBLE;
        }
        if (binary.left().type() == IrType.FLOAT || binary.right().type() == IrType.FLOAT) {
            return IrType.FLOAT;
        }
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
        if (type == IrType.FLOAT || type == IrType.DOUBLE) {
            builder.append(type == IrType.FLOAT ? "    movss " : "    movsd ")
                    .append(valueEmitter.floatRegisterName(preferredRegister))
                    .append(", ").append(source).append(System.lineSeparator());
            return;
        }
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

    private void emitStoreRegisterToMemory(StringBuilder builder, String destination, IrType type, String register) {
        if (type == IrType.FLOAT || type == IrType.DOUBLE) {
            builder.append(type == IrType.FLOAT ? "    movss " : "    movsd ")
                    .append(destination)
                    .append(", ").append(valueEmitter.floatRegisterName(register))
                    .append(System.lineSeparator());
            return;
        }
        builder.append("    mov ").append(destination).append(", ")
                .append(valueEmitter.storeRegister(register, type)).append(System.lineSeparator());
    }

    private String integerCastRegister(IrType type) {
        return switch (type) {
            case LONG -> "rax";
            case BOOL, CHAR, INT -> "eax";
            default -> throw new IllegalArgumentException("not an integer scalar type: " + type);
        };
    }

    private void emitArithmetic(
            StringBuilder builder,
            IrType operationType,
            String operation,
            String leftRegister,
            String rightRegister
    ) {
        if (operationType == IrType.FLOAT || operationType == IrType.DOUBLE) {
            String suffix = operationType == IrType.FLOAT ? "ss" : "sd";
            builder.append("    ").append(operation).append(suffix).append(" ")
                    .append(leftRegister).append(", ").append(rightRegister)
                    .append(System.lineSeparator());
            return;
        }
        String instruction = operation.equals("mul") ? "imul" : operation;
        builder.append("    ").append(instruction).append(" ").append(leftRegister).append(", ").append(rightRegister)
                .append(System.lineSeparator());
    }

    private String floatingScratchSlot(IrType type) {
        return (type == IrType.FLOAT ? "DWORD PTR" : "QWORD PTR") + " [rsp]";
    }

    private void emitBranch(StringBuilder builder, String functionName, IrBranchInstruction branch) {
        if (branch.condition().type().isFloatingScalar()) {
            valueEmitter.emitLoadValue(builder, branch.condition(), "xmm0");
            if (branch.condition().type() == IrType.FLOAT) {
                builder.append("    xorps xmm1, xmm1").append(System.lineSeparator());
                builder.append("    ucomiss xmm0, xmm1").append(System.lineSeparator());
            } else {
                builder.append("    xorpd xmm1, xmm1").append(System.lineSeparator());
                builder.append("    ucomisd xmm0, xmm1").append(System.lineSeparator());
            }
        } else {
            valueEmitter.emitLoadValue(builder, branch.condition(), "eax");
            builder.append("    cmp eax, 0").append(System.lineSeparator());
        }
        builder.append("    jne ").append(blockSymbol(functionName, branch.thenLabel())).append(System.lineSeparator());
        emitJump(builder, functionName, branch.elseLabel());
    }

    private void emitElementAddressScale(StringBuilder builder, int elementSizeBytes) {
        switch (elementSizeBytes) {
            case 1 -> builder.append("    lea rax, [rax+rcx]").append(System.lineSeparator());
            case 2, 4, 8 -> builder.append("    lea rax, [rax+rcx*").append(elementSizeBytes).append("]")
                    .append(System.lineSeparator());
            default -> {
                builder.append("    imul rcx, ").append(elementSizeBytes).append(System.lineSeparator());
                builder.append("    lea rax, [rax+rcx]").append(System.lineSeparator());
            }
        }
    }

    private void emitJump(StringBuilder builder, String functionName, String targetLabel) {
        builder.append("    jmp ").append(blockSymbol(functionName, targetLabel)).append(System.lineSeparator());
    }

    private void emitMemCopy(StringBuilder builder, IrMemCopyInstruction memCopy) {
        valueEmitter.emitLoadValue(builder, memCopy.destination(), "rdi");
        valueEmitter.emitLoadValue(builder, memCopy.source(), "rsi");
        builder.append("    mov ecx, ").append(memCopy.sizeBytes()).append(System.lineSeparator());
        builder.append("    rep movsb").append(System.lineSeparator());
    }
}
