package minic.compiler.asm;

import minic.compiler.ir.instruction.MemoryInstruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator;
import minic.compiler.ir.instruction.ControlInstruction.IrBranchInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrCastInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrJumpInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrMemCopyInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrSelectInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrUnaryInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue.IrFloatConstant;
import minic.compiler.ir.value.IrValue.IrConstant;
import minic.compiler.ir.value.IrValue.IrTemporary;

import java.util.Set;
import java.util.stream.Collectors;

final class InstructionEmitter {
    private static final Set<String> BINARY_REGISTER_HOMES = Set.of("r10", "r11", "rbx", "r12", "r13", "r14", "r15");
    private final FrameLayout frame;
    private final ValueEmitter valueEmitter;
    private final TemporaryLocations locations;
    private final Set<String> externalFunctionNames;
    private final Set<String> addressedLocals;
    private final boolean optimizeInstructions;

    InstructionEmitter(FrameLayout frame, Set<String> externalFunctionNames, IrFunction function) {
        this(frame, externalFunctionNames, function, TemporaryLocations.allStack(frame));
    }

    InstructionEmitter(FrameLayout frame, Set<String> externalFunctionNames, IrFunction function,
                       TemporaryLocations locations) {
        this(frame, externalFunctionNames, function, locations, false);
    }

    InstructionEmitter(FrameLayout frame, Set<String> externalFunctionNames, IrFunction function,
                       TemporaryLocations locations, boolean optimizeInstructions) {
        this.frame = frame;
        this.locations = locations;
        this.optimizeInstructions = optimizeInstructions;
        this.externalFunctionNames = Set.copyOf(externalFunctionNames);
        addressedLocals = function.blocks().stream().flatMap(block -> block.instructions().stream())
                .filter(IrAddressOfLocalInstruction.class::isInstance).map(IrAddressOfLocalInstruction.class::cast)
                .map(address -> address.local().name()).collect(Collectors.toUnmodifiableSet());
        valueEmitter = new ValueEmitter(frame, externalFunctionNames, locations);
    }

    void emitParameterStores(StringBuilder builder, IrFunction function) {
        String incomingBase = frame.realigned() ? "r11" : "rbp";
        if (frame.realigned()) builder.append("    mov r11, ").append(frame.originalFrameSlot()).append(System.lineSeparator());
        if (function.variadic()) {
            // A va_list walks one contiguous array of eight-byte slots.  Home
            // the four register arguments into the shadow space supplied by
            // the caller so that slot 3 -> slot 4 is ordinary pointer advance.
            for (int index = 0; index < CallingConvention.INTEGER_ARGUMENT_REGISTERS.size(); index++) {
                builder.append("    mov QWORD PTR [").append(incomingBase).append("+")
                        .append(CallingConvention.incomingArgumentSlotOffset(index))
                        .append("], ")
                        .append(CallingConvention.pointerArgumentRegister(index))
                        .append(System.lineSeparator());
            }
        }
        for (int index = 0; index < function.parameters().size(); index++) {
            IrParameter parameter = function.parameters().get(index);
            String destination = frame.parameterSlot(parameter.name(), parameter.type());
            if (CallingConvention.isRegisterArgument(index)) {
                emitStoreRegisterToMemory(builder, destination, parameter.type(), argumentRegister(parameter.type(), index));
            } else {
                int stackOffset = CallingConvention.incomingStackArgumentOffset(index);
                String register = fullRegisterForType(parameter.type());
                String source = memoryPrefix(parameter.type()) + " [" + incomingBase + "+" + stackOffset + "]";
                emitLoadMemoryToRegister(builder, source, parameter.type(), register);
                emitStoreRegisterToMemory(builder, destination, parameter.type(), register);
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
                if (frame.hasLocalInitializedFlag(declareLocal.local()))
                    builder.append("    mov ").append(frame.localInitializedSlot(declareLocal.local()))
                        .append(", 0").append(System.lineSeparator());
            }
            case IrStoreLocalInstruction storeLocal -> {
                String register = storeLocalRegister(storeLocal.local().type());
                valueEmitter.emitLoadValue(builder, storeLocal.value(), register);
                emitStoreRegisterToMemory(builder, frame.localSlot(storeLocal.local()), storeLocal.local().type(), register);
                if (frame.hasLocalInitializedFlag(storeLocal.local()))
                    builder.append("    mov ").append(frame.localInitializedSlot(storeLocal.local()))
                        .append(", 1").append(System.lineSeparator());
            }
            case IrCheckInitializedInstruction checkInitialized -> {
                // Direct-store flags cannot observe pointer stores or writes by callees.
                // Native checks cover unexposed locals; debug retains byte-level alias checks.
                // Classify the emitted function: unused address calculations remain pure and removable.
                if (addressedLocals.contains(checkInitialized.local().name())) break;
                builder.append("    cmp ").append(frame.localInitializedSlot(checkInitialized.local()))
                        .append(", 0").append(System.lineSeparator());
                builder.append("    je ").append(functionName).append("$trap_uninitialized")
                        .append(System.lineSeparator());
            }
            case IrLoadLocalInstruction loadLocal -> {
                emitLoadMemoryToRegister(builder, frame.localSlot(loadLocal.local()), loadLocal.local().type(), "rcx");
                valueEmitter.emitStoreTemporary(
                        builder, loadLocal.result(),
                        storeValueRegister(loadLocal.result().type())
                );
            }
            case IrCheckNonZeroInstruction checkNonZero -> {
                if (checkNonZero.value().type().isFloatingScalar()) {
                    valueEmitter.emitLoadValue(builder, checkNonZero.value(), "xmm0");
                    emitFloatingTruthFromXmm0(builder, checkNonZero.value().type());
                    builder.append("    cmp eax, 0").append(System.lineSeparator());
                } else {
                    valueEmitter.emitLoadValue(builder, checkNonZero.value(), "rax");
                    builder.append("    cmp ")
                            .append(valueEmitter.storeRegister("rax", checkNonZero.value().type()))
                            .append(", 0").append(System.lineSeparator());
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
                if (frame.realigned() && addressOfLocal.local().incomingArgumentArea()) {
                    builder.append("    mov rax, ").append(frame.originalFrameSlot()).append(System.lineSeparator());
                    builder.append("    lea rax, [rax+")
                            .append(CallingConvention.incomingArgumentSlotOffset(addressOfLocal.local().incomingArgumentIndex()))
                            .append("]").append(System.lineSeparator());
                } else {
                    builder.append("    lea rax, ").append(frame.localAddress(addressOfLocal.local()))
                            .append(System.lineSeparator());
                }
                valueEmitter.emitStoreTemporary(builder, addressOfLocal.result(), "rax");
            }
            case IrElementAddressInstruction elementAddress -> {
                valueEmitter.emitLoadValue(builder, elementAddress.baseAddress(), "rax");
                IrType indexType = elementAddress.index().type();
                valueEmitter.emitLoadValue(builder, elementAddress.index(), "rcx");
                if (indexType.isSignedInteger() && indexType.sizeBytes() < Long.BYTES)
                    builder.append("    movsxd rcx, ecx").append(System.lineSeparator());
                emitElementAddressScale(builder, elementAddress.elementSizeBytes());
                valueEmitter.emitStoreTemporary(builder, elementAddress.result(), "rax");
            }
            case IrFieldAddressInstruction fieldAddress -> {
                valueEmitter.emitLoadValue(builder, fieldAddress.baseAddress(), "rax");
                if (fieldAddress.offset() != 0) {
                    builder.append("    lea rax, [rax+").append(fieldAddress.offset()).append("]")
                            .append(System.lineSeparator());
                }
                valueEmitter.emitStoreTemporary(builder, fieldAddress.result(), "rax");
            }
            case IrLoadPointerInstruction loadPointer -> {
                valueEmitter.emitLoadValue(builder, loadPointer.address(), "rax");
                emitLoadMemoryToRegister(
                        builder,
                        memoryPrefix(loadPointer.result().type()) + " [rax]",
                        loadPointer.result().type(),
                        "rax"
                );
                valueEmitter.emitStoreTemporary(
                        builder, loadPointer.result(),
                        valueEmitter.storeRegister("rax", loadPointer.result().type())
                );
            }
            case IrStorePointerInstruction storePointer -> {
                String register = storeValueRegister(storePointer.value().type());
                valueEmitter.emitLoadValue(builder, storePointer.value(), register);
                // Float constants use eax/rax as a bit-pattern scratch register.
                // Materialize the value before reserving rax for the destination address.
                valueEmitter.emitLoadValue(builder, storePointer.address(), "rax");
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
                if (returnInstruction.value() != null) {
                    valueEmitter.emitLoadValue(
                            builder,
                            returnInstruction.value(),
                            returnRegister(returnInstruction.value().type())
                    );
                }
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
        if (emitPowerOfTwoDivision(builder, binary, operationType)) return;
        if (emitBinaryToRegisterHome(builder, binary, operationType)) return;
        String leftRegister = arithmeticRegister("rax", operationType);
        String rightRegister = arithmeticRegister("rcx", operationType);
        if (emitImmediateBinary(builder, binary, operationType, leftRegister)) return;
        valueEmitter.emitLoadValue(builder, binary.left(), leftRegister);
        if (operationType.isFloatingScalar()) {
            builder.append(operationType == IrType.FLOAT ? "    sub rsp, 4" : "    sub rsp, 8")
                    .append(System.lineSeparator());
            emitStoreRegisterToMemory(builder, floatingScratchSlot(operationType), operationType, leftRegister);
        } else if (!optimizeInstructions) {
            builder.append("    push rax").append(System.lineSeparator());
        }
        valueEmitter.emitLoadValue(builder, binary.right(), rightRegister);
        if (operationType.isFloatingScalar()) {
            emitLoadMemoryToRegister(builder, floatingScratchSlot(operationType), operationType, leftRegister);
            builder.append(operationType == IrType.FLOAT ? "    add rsp, 4" : "    add rsp, 8")
                    .append(System.lineSeparator());
        } else if (!optimizeInstructions) {
            builder.append("    pop rax").append(System.lineSeparator());
        }
        switch (binary.operator()) {
            case ADD -> emitArithmetic(builder, operationType, "add", leftRegister, rightRegister);
            case SUBTRACT -> emitArithmetic(builder, operationType, "sub", leftRegister, rightRegister);
            case MULTIPLY -> emitArithmetic(builder, operationType, "mul", leftRegister, rightRegister);
            case DIVIDE -> {
                if (operationType.isFloatingScalar()) {
                    emitArithmetic(builder, operationType, "div", leftRegister, rightRegister);
                } else if (operationType.isUnsignedInteger()) {
                    builder.append(operationType.sizeBytes() == 8 ? "    xor rdx, rdx" : "    xor edx, edx")
                            .append(System.lineSeparator());
                    builder.append("    div ").append(rightRegister).append(System.lineSeparator());
                } else {
                    builder.append(operationType.sizeBytes() == 8 ? "    cqo" : "    cdq").append(System.lineSeparator());
                    builder.append("    idiv ").append(rightRegister).append(System.lineSeparator());
                }
            }
            case MODULO -> {
                if (operationType.isUnsignedInteger()) {
                    builder.append(operationType.sizeBytes() == 8 ? "    xor rdx, rdx" : "    xor edx, edx")
                            .append(System.lineSeparator());
                    builder.append("    div ").append(rightRegister).append(System.lineSeparator());
                } else {
                    builder.append(operationType.sizeBytes() == 8 ? "    cqo" : "    cdq").append(System.lineSeparator());
                    builder.append("    idiv ").append(rightRegister).append(System.lineSeparator());
                }
                builder.append("    mov ").append(leftRegister).append(", ")
                        .append(operationType.sizeBytes() == 8 ? "rdx" : "edx").append(System.lineSeparator());
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
                builder.append(operationType.isUnsignedInteger() ? "    shr " : "    sar ")
                        .append(leftRegister).append(", cl").append(System.lineSeparator());
            }
            case LOGICAL_AND -> emitLogicalBinary(builder, "and", leftRegister, rightRegister);
            case LOGICAL_OR -> emitLogicalBinary(builder, "or", leftRegister, rightRegister);
            case EQUAL -> emitComparison(builder, operationType, "sete", leftRegister, rightRegister);
            case NOT_EQUAL -> emitComparison(builder, operationType, "setne", leftRegister, rightRegister);
            case LESS_THAN -> emitComparison(builder, operationType,
                    operationType.isFloatingScalar() || operationType.isUnsignedInteger() ? "setb" : "setl", leftRegister, rightRegister);
            case LESS_EQUAL -> emitComparison(builder, operationType,
                    operationType.isFloatingScalar() || operationType.isUnsignedInteger() ? "setbe" : "setle", leftRegister, rightRegister);
            case GREATER_THAN -> emitComparison(builder, operationType,
                    operationType.isFloatingScalar() || operationType.isUnsignedInteger() ? "seta" : "setg", leftRegister, rightRegister);
            case GREATER_EQUAL -> emitComparison(builder, operationType,
                    operationType.isFloatingScalar() || operationType.isUnsignedInteger() ? "setae" : "setge", leftRegister, rightRegister);
        }
        valueEmitter.emitStoreTemporary(
                builder, binary.result(),
                valueEmitter.storeRegister("rax", binary.result().type())
        );
    }

    /** Exact integer division by a positive power of two, using only reserved scratch registers. */
    private boolean emitPowerOfTwoDivision(StringBuilder builder, IrBinaryInstruction binary, IrType type) {
        if (!optimizeInstructions || !type.isIntegerScalar() || type.sizeBytes() < 4
                || binary.left().type() != type || binary.result().type() != type
                || !(binary.right() instanceof IrConstant constant) || constant.type() != type
                || (binary.operator() != IrBinaryOperator.DIVIDE && binary.operator() != IrBinaryOperator.MODULO)) return false;
        long divisor = type.sizeBytes() == 4
                ? (type.isUnsignedInteger() ? constant.value() & 0xffffffffL : (int) constant.value())
                : constant.value();
        // The unsigned high bit is a valid divisor; signed negative values keep the hardware path,
        // including -1, whose minimum-value overflow must not be silently optimized away.
        if (divisor == 0 || (divisor & (divisor - 1)) != 0 || type.isSignedInteger() && divisor < 0) return false;
        int shift = Long.numberOfTrailingZeros(divisor);
        String accumulator = arithmeticRegister("rax", type);
        String bias = arithmeticRegister("rdx", type);
        valueEmitter.emitLoadValue(builder, binary.left(), accumulator);
        if (shift == 0) {
            if (binary.operator() == IrBinaryOperator.MODULO)
                builder.append("    xor eax, eax").append(System.lineSeparator());
        } else if (type.isUnsignedInteger()) {
            if (binary.operator() == IrBinaryOperator.DIVIDE)
                builder.append("    shr ").append(accumulator).append(", ").append(shift).append(System.lineSeparator());
            else emitPowerOfTwoMask(builder, type, divisor - 1);
        } else {
            // bias = x < 0 ? divisor-1 : 0. This addition cannot overflow: positive x adds zero,
            // negative x moves towards zero. It implements truncation rather than floor division.
            builder.append(type.sizeBytes() == 8 ? "    cqo" : "    cdq").append(System.lineSeparator());
            builder.append("    shr ").append(bias).append(", ").append(type.sizeBytes() * 8 - shift).append(System.lineSeparator());
            builder.append("    add ").append(accumulator).append(", ").append(bias).append(System.lineSeparator());
            if (binary.operator() == IrBinaryOperator.DIVIDE)
                builder.append("    sar ").append(accumulator).append(", ").append(shift).append(System.lineSeparator());
            else {
                emitPowerOfTwoMask(builder, type, divisor - 1);
                builder.append("    sub ").append(accumulator).append(", ").append(bias).append(System.lineSeparator());
            }
        }
        // Read the old operand fully before writing the result, including a non-SSA in-place update.
        valueEmitter.emitStoreTemporary(builder, binary.result(), accumulator);
        return true;
    }

    private void emitPowerOfTwoMask(StringBuilder builder, IrType type, long mask) {
        String accumulator = arithmeticRegister("rax", type);
        // x64 AND r64,imm32 sign-extends its immediate. Wide positive masks need a scratch GPR.
        if (mask <= Integer.MAX_VALUE) {
            builder.append("    and ").append(accumulator).append(", ").append(mask).append(System.lineSeparator());
        } else {
            builder.append("    mov rcx, ").append(mask).append(System.lineSeparator());
            builder.append("    and ").append(accumulator).append(", rcx").append(System.lineSeparator());
        }
    }

    /** Integer IR values are already evaluated and loading one into rcx changes no allocated home. */
    private boolean emitBinaryToRegisterHome(StringBuilder builder, IrBinaryInstruction binary, IrType type) {
        if (!optimizeInstructions || !type.isIntegerScalar() || type.sizeBytes() < 4
                || binary.result().type() != type || binary.left().type() != type || binary.right().type() != type
                || !(locations.location(binary.result()) instanceof ValueLocation.Register home)
                || !BINARY_REGISTER_HOMES.contains(home.name())) return false;
        String mnemonic = integerBinaryMnemonic(binary.operator());
        if (mnemonic == null) return false;
        Long immediate = integerImmediate(binary, type);
        String right = arithmeticRegister("rcx", type);
        // The result may be the right operand of a non-SSA update. Save that old value
        // before loading the left operand into the destination register.
        if (immediate == null) valueEmitter.emitLoadValue(builder, binary.right(), right);
        boolean alreadyInHome = binary.left() instanceof IrTemporary temporary
                && locations.location(temporary).equals(home);
        // A 32-bit arithmetic instruction itself clears the high half, so unlike an
        // ordinary value load it does not need a redundant self-move first.
        if (!alreadyInHome) valueEmitter.emitLoadValue(builder, binary.left(), home.operand());
        builder.append("    ").append(mnemonic).append(" ").append(home.operand()).append(", ")
                .append(immediate == null ? right : immediate.toString()).append(System.lineSeparator());
        return true;
    }

    /** IR values are already evaluated; integer loads into rcx do not clobber rax. */
    private boolean emitImmediateBinary(StringBuilder builder, IrBinaryInstruction binary,
                                        IrType type, String leftRegister) {
        if (!optimizeInstructions) return false;
        String mnemonic = integerBinaryMnemonic(binary.operator());
        Long immediate = integerImmediate(binary, type);
        if (mnemonic == null || immediate == null) return false;
        valueEmitter.emitLoadValue(builder, binary.left(), leftRegister);
        builder.append("    ").append(mnemonic).append(" ").append(leftRegister).append(", ")
                .append(immediate).append(System.lineSeparator());
        valueEmitter.emitStoreTemporary(builder, binary.result(),
                valueEmitter.storeRegister("rax", binary.result().type()));
        return true;
    }

    private static String integerBinaryMnemonic(IrBinaryOperator operator) {
        return switch (operator) {
            case ADD -> "add";
            case SUBTRACT -> "sub";
            case MULTIPLY -> "imul";
            case BITWISE_AND -> "and";
            case BITWISE_OR -> "or";
            case BITWISE_XOR -> "xor";
            default -> null;
        };
    }

    private static Long integerImmediate(IrBinaryInstruction binary, IrType type) {
        if (!(binary.right() instanceof IrConstant constant) || binary.left().type() != type || constant.type() != type
                || type.isFloatingScalar() || type.sizeBytes() < 4) return null;
        // 32-bit operations consume the low 32 bits. A 64-bit x64 operation sign-extends
        // its encoded immediate, so 0x00000000ffffffff must not be emitted as -1.
        long immediate = type.sizeBytes() == 4 ? (int) constant.value() : constant.value();
        return immediate < Integer.MIN_VALUE || immediate > Integer.MAX_VALUE ? null : immediate;
    }

    private void emitUnary(StringBuilder builder, IrUnaryInstruction unary) {
        valueEmitter.emitLoadValue(builder, unary.operand(), "rax");
        switch (unary.operator()) {
            case LOGICAL_NOT -> {
                if (unary.operand().type().isFloatingScalar()) {
                    emitFloatingTruthFromXmm0(builder, unary.operand().type());
                    builder.append("    xor eax, 1").append(System.lineSeparator());
                } else {
                    builder.append("    cmp ").append(valueEmitter.storeRegister("rax", unary.operand().type()))
                            .append(", 0").append(System.lineSeparator());
                    builder.append("    sete al").append(System.lineSeparator());
                    builder.append("    movzx eax, al").append(System.lineSeparator());
                }
            }
            case BITWISE_NOT -> builder.append("    not ")
                    .append(valueEmitter.storeRegister("rax", unary.result().type()))
                    .append(System.lineSeparator());
            case NEGATE -> {
                if (unary.result().type().isFloatingScalar()) {
                    // Flip only the sign: subtraction would lose negative zero and may alter NaNs.
                    valueEmitter.emitLoadValue(builder, new IrFloatConstant(-0.0, unary.result().type()), "xmm1");
                    builder.append(unary.result().type() == IrType.FLOAT ? "    xorps xmm0, xmm1" : "    xorpd xmm0, xmm1")
                            .append(System.lineSeparator());
                } else {
                    builder.append("    neg ").append(valueEmitter.storeRegister("rax", unary.result().type()))
                            .append(System.lineSeparator());
                }
            }
        }
        valueEmitter.emitStoreTemporary(
                builder, unary.result(),
                valueEmitter.storeRegister("rax", unary.result().type())
        );
    }

    private void emitSelect(StringBuilder builder, IrSelectInstruction select) {
        String falseLabel = "minic$select_false_" + Math.abs(select.hashCode());
        String endLabel = "minic$select_end_" + Math.abs(select.hashCode());
        valueEmitter.emitLoadValue(builder, select.condition(), "rax");
        builder.append("    cmp ").append(valueEmitter.storeRegister("rax", select.condition().type()))
                .append(", 0").append(System.lineSeparator());
        builder.append("    je ").append(falseLabel).append(System.lineSeparator());
        valueEmitter.emitLoadValue(builder, select.thenValue(), storeValueRegister(select.result().type()));
        valueEmitter.emitStoreTemporary(builder, select.result(), storeValueRegister(select.result().type()));
        builder.append("    jmp ").append(endLabel).append(System.lineSeparator());
        builder.append(falseLabel).append(":").append(System.lineSeparator());
        valueEmitter.emitLoadValue(builder, select.elseValue(), storeValueRegister(select.result().type()));
        valueEmitter.emitStoreTemporary(builder, select.result(), storeValueRegister(select.result().type()));
        builder.append(endLabel).append(":").append(System.lineSeparator());
    }

    private void emitMove(StringBuilder builder, IrMoveInstruction move) {
        valueEmitter.emitLoadValue(builder, move.value(), storeValueRegister(move.result().type()));
        valueEmitter.emitStoreTemporary(
                builder, move.result(),
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
            valueEmitter.emitStoreTemporary(builder, cast.result(), valueEmitter.storeRegister("rax", targetType));
            return;
        }
        if (sourceType.isFloatingScalar() && targetType == IrType.BOOL) {
            valueEmitter.emitLoadValue(builder, cast.value(), "xmm0");
            emitFloatingTruthFromXmm0(builder, sourceType);
            valueEmitter.emitStoreTemporary(builder, cast.result(), "rax");
            return;
        }
        if (sourceType.isIntegerScalar() && targetType == IrType.BOOL) {
            valueEmitter.emitLoadValue(builder, cast.value(), "rax");
            builder.append("    cmp ").append(valueEmitter.storeRegister("rax", sourceType))
                    .append(", 0").append(System.lineSeparator());
            builder.append("    setne al").append(System.lineSeparator());
            builder.append("    movzx eax, al").append(System.lineSeparator());
            valueEmitter.emitStoreTemporary(builder, cast.result(), "rax");
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
            valueEmitter.emitStoreTemporary(builder, cast.result(), "xmm0");
            return;
        }
        if (sourceType.isFloatingScalar() && targetType.isIntegerScalar()) {
            valueEmitter.emitLoadValue(builder, cast.value(), "xmm0");
            builder.append(sourceType == IrType.FLOAT ? "    cvttss2si " : "    cvttsd2si ")
                    .append(integerCastRegister(targetType)).append(", xmm0").append(System.lineSeparator());
            valueEmitter.emitStoreTemporary(builder, cast.result(), integerCastRegister(targetType));
            return;
        }
        if (sourceType.isIntegerScalar() && targetType.isIntegerScalar()) {
            valueEmitter.emitLoadValue(builder, cast.value(), "rax");
            if (sourceType.sizeBytes() == 1 && targetType.sizeBytes() > 1) {
                builder.append(sourceType.isSignedInteger() ? "    movsx eax, al" : "    movzx eax, al")
                        .append(System.lineSeparator());
            } else if (sourceType.sizeBytes() == 2 && targetType.sizeBytes() > 2) {
                builder.append(sourceType.isSignedInteger() ? "    movsx eax, ax" : "    movzx eax, ax")
                        .append(System.lineSeparator());
            }
            if (sourceType.sizeBytes() <= 4 && targetType.sizeBytes() == 8) {
                if (sourceType.isSignedInteger()) {
                    builder.append("    movsxd rax, eax").append(System.lineSeparator());
                } else {
                    builder.append("    mov eax, eax").append(System.lineSeparator());
                }
            }
            valueEmitter.emitStoreTemporary(builder, cast.result(),
                    valueEmitter.storeRegister("rax", targetType));
            return;
        }
        valueEmitter.emitLoadValue(builder, cast.value(), valueEmitter.storeRegister("rax", sourceType));
        valueEmitter.emitStoreTemporary(builder, cast.result(), valueEmitter.storeRegister("rax", targetType));
    }

    private void emitComparison(
            StringBuilder builder,
            IrType operationType,
            String setInstruction,
            String leftRegister,
            String rightRegister
    ) {
        if (operationType.isFloatingScalar()) {
            // UCOMIS{S,D}: unordered sets ZF=PF=CF=1. Reverse < and <=
            // to use conditions which exclude unordered without new encoder opcodes.
            if (setInstruction.equals("setb") || setInstruction.equals("setbe")) {
                String previousLeft = leftRegister;
                leftRegister = rightRegister;
                rightRegister = previousLeft;
                setInstruction = setInstruction.equals("setb") ? "seta" : "setae";
            }
            builder.append(operationType == IrType.FLOAT ? "    ucomiss " : "    ucomisd ")
                    .append(leftRegister).append(", ").append(rightRegister).append(System.lineSeparator());
            builder.append("    ").append(setInstruction).append(" al").append(System.lineSeparator());
            if (setInstruction.equals("sete")) {
                builder.append("    setae dl").append(System.lineSeparator());
                builder.append("    and al, dl").append(System.lineSeparator());
            } else if (setInstruction.equals("setne")) {
                builder.append("    setb dl").append(System.lineSeparator());
                builder.append("    or al, dl").append(System.lineSeparator());
            }
            builder.append("    movzx eax, al").append(System.lineSeparator());
            return;
        }
        builder.append("    cmp ").append(leftRegister).append(", ").append(rightRegister).append(System.lineSeparator());
        builder.append("    ").append(setInstruction).append(" al").append(System.lineSeparator());
        builder.append("    movzx eax, al").append(System.lineSeparator());
    }

    private void emitCall(StringBuilder builder, IrCallInstruction call) {
        emitCallArguments(builder, call.arguments(), call.variadic());
        builder.append("    call ").append(CallingConvention.callSymbol(
                        call.calleeName(),
                        externalFunctionNames.contains(call.calleeName())
                ))
                .append(System.lineSeparator());
        if (call.result() != null) {
            valueEmitter.emitStoreTemporary(builder, call.result(), returnRegister(call.result().type()));
        }
    }

    private void emitIndirectCall(StringBuilder builder, IrIndirectCallInstruction call) {
        emitCallArguments(builder, call.arguments(), call.variadic());
        valueEmitter.emitLoadValue(builder, call.calleeAddress(), "rax");
        builder.append("    call rax").append(System.lineSeparator());
        if (call.result() != null) {
            valueEmitter.emitStoreTemporary(builder, call.result(), returnRegister(call.result().type()));
        }
    }

    private void emitCallArguments(
            StringBuilder builder,
            java.util.List<minic.compiler.ir.value.IrValue> arguments,
            boolean variadic
    ) {
        for (int index = CallingConvention.INTEGER_ARGUMENT_REGISTERS.size();
             index < arguments.size();
             index++) {
            IrType type = arguments.get(index).type();
            String register = stackArgumentRegister(type);
            valueEmitter.emitLoadValue(builder, arguments.get(index), register);
            emitStoreRegisterToMemory(builder, frame.outgoingStackArgumentSlot(index, type), type, register);
        }
        for (int index = 0; index < arguments.size(); index++) {
            if (CallingConvention.isRegisterArgument(index)) {
                valueEmitter.emitLoadValue(
                        builder,
                        arguments.get(index),
                        argumentRegister(arguments.get(index).type(), index)
                );
                if (variadic && arguments.get(index).type().isFloatingScalar()) {
                    String integerRegister = CallingConvention.pointerArgumentRegister(index);
                    String floatingRegister = CallingConvention.floatArgumentRegister(index);
                    builder.append(arguments.get(index).type() == IrType.DOUBLE ? "    movq " : "    movd ")
                            .append(arguments.get(index).type() == IrType.DOUBLE
                                    ? integerRegister
                                    : CallingConvention.integerArgumentRegister(index))
                            .append(", ")
                            .append(floatingRegister)
                            .append(System.lineSeparator());
                }
            }
        }
    }

    private String argumentRegister(IrType type, int argumentIndex) {
        if (type.isFloatingScalar()) return CallingConvention.floatArgumentRegister(argumentIndex);
        if (type == IrType.POINTER || type.sizeBytes() == 8) {
            return CallingConvention.pointerArgumentRegister(argumentIndex);
        }
        return CallingConvention.integerArgumentRegister(argumentIndex);
    }

    private String memoryPrefix(IrType type) {
        return valueEmitter.memoryPrefix(type);
    }

    private String storeValueRegister(IrType type) {
        return valueEmitter.storeRegister("rcx", type);
    }

    private String storeLocalRegister(IrType type) {
        return valueEmitter.storeRegister(type.isFloatingScalar() || type == IrType.POINTER || type.sizeBytes() == 8
                ? "rax" : "rcx", type);
    }

    private String fullRegisterForType(IrType type) {
        if (type.isFloatingScalar()) return "xmm0";
        return type == IrType.POINTER || type.sizeBytes() == 8 ? "rax" : "eax";
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
        return switch (CallingConvention.pointerArgumentRegister(argumentIndex)) {
            case "rcx" -> "cl";
            case "rdx" -> "dl";
            case "r8" -> "r8b";
            case "r9" -> "r9b";
            default -> throw new IllegalArgumentException("unsupported argument index: " + argumentIndex);
        };
    }

    private IrType binaryOperationType(IrBinaryInstruction binary) {
        if (binary.left().type() == binary.right().type()) return binary.left().type();
        if (binary.left().type() == IrType.DOUBLE || binary.right().type() == IrType.DOUBLE) {
            return IrType.DOUBLE;
        }
        if (binary.left().type() == IrType.FLOAT || binary.right().type() == IrType.FLOAT) {
            return IrType.FLOAT;
        }
        if (binary.left().type() == IrType.POINTER || binary.right().type() == IrType.POINTER) {
            return IrType.POINTER;
        }
        if (binary.left().type().isIntegerScalar() && binary.right().type().isIntegerScalar()) {
            if (binary.left().type().sizeBytes() > binary.right().type().sizeBytes()) return binary.left().type();
            if (binary.right().type().sizeBytes() > binary.left().type().sizeBytes()) return binary.right().type();
            return binary.left().type().isUnsignedInteger() ? binary.left().type() : binary.right().type();
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
        if (type.isIntegerScalar() && type.sizeBytes() < Integer.BYTES) {
            builder.append(type.isSignedInteger() ? "    movsx " : "    movzx ")
                    .append(valueEmitter.intRegisterName(preferredRegister))
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
        if (!type.isIntegerScalar()) {
            throw new IllegalArgumentException("not an integer scalar type: " + type);
        }
        return type.sizeBytes() == Long.BYTES
                || type.isUnsignedInteger() && type.sizeBytes() == Integer.BYTES
                ? "rax" : "eax";
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
            emitFloatingTruthFromXmm0(builder, branch.condition().type());
            builder.append("    cmp eax, 0").append(System.lineSeparator());
        } else {
            valueEmitter.emitLoadValue(builder, branch.condition(), "rax");
            builder.append("    cmp ").append(valueEmitter.storeRegister("rax", branch.condition().type()))
                    .append(", 0").append(System.lineSeparator());
        }
        builder.append("    jne ").append(blockSymbol(functionName, branch.thenLabel())).append(System.lineSeparator());
        emitJump(builder, functionName, branch.elseLabel());
    }

    /** Canonical integer truth in eax: only positive and negative zero are false. */
    private void emitFloatingTruthFromXmm0(StringBuilder builder, IrType type) {
        builder.append(type == IrType.FLOAT ? "    xorps xmm1, xmm1" : "    xorpd xmm1, xmm1")
                .append(System.lineSeparator());
        emitComparison(builder, type, "setne", "xmm0", "xmm1");
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
        if (optimizeInstructions && !memCopy.volatileAccess() && memCopy.sizeBytes() <= 16) {
            emitSmallCopy(builder, memCopy);
            return;
        }
        // REP MOVSB changes both Windows x64 nonvolatile index registers.
        builder.append("    push rsi").append(System.lineSeparator());
        builder.append("    push rdi").append(System.lineSeparator());
        valueEmitter.emitLoadValue(builder, memCopy.destination(), "rdi");
        valueEmitter.emitLoadValue(builder, memCopy.source(), "rsi");
        builder.append("    mov ecx, ").append(memCopy.sizeBytes()).append(System.lineSeparator());
        builder.append("    rep movsb").append(System.lineSeparator());
        builder.append("    pop rdi").append(System.lineSeparator());
        builder.append("    pop rsi").append(System.lineSeparator());
    }

    private void emitSmallCopy(StringBuilder builder, IrMemCopyInstruction memCopy) {
        valueEmitter.emitLoadValue(builder, memCopy.destination(), "rax");
        valueEmitter.emitLoadValue(builder, memCopy.source(), "rcx");
        // These volatile scratch registers are disjoint from temporary homes. Read the
        // complete object before any store, preserving self/overlapping IR copy snapshots.
        // MOVSD is a raw bit move: it also preserves signed zero and NaN payloads.
        var stores = new StringBuilder();
        int offset = 0;
        for (int size : new int[]{8, 8, 4, 2, 1}) {
            if (offset + size > memCopy.sizeBytes()) continue;
            String register = switch (size) {
                case 8 -> offset == 0 ? "xmm0" : "xmm1";
                case 4 -> "edx";
                case 2 -> "r8w";
                default -> "r9b";
            };
            String operation = size == 8 ? "movsd " : "mov ";
            String width = switch (size) { case 8 -> "QWORD"; case 4 -> "DWORD"; case 2 -> "WORD"; default -> "BYTE"; };
            String displacement = offset == 0 ? "" : "+" + offset;
            builder.append("    ").append(operation).append(register).append(", ").append(width)
                    .append(" PTR [rcx").append(displacement).append(']').append(System.lineSeparator());
            stores.append("    ").append(operation).append(width).append(" PTR [rax").append(displacement)
                    .append("], ").append(register).append(System.lineSeparator());
            offset += size;
        }
        builder.append(stores);
    }
}
